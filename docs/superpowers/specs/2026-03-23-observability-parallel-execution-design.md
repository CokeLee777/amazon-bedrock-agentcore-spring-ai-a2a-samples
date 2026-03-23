# Observability + Parallel Tool Execution 설계

**날짜**: 2026-03-23
**브랜치**: feat/observability-parallel-execution
**상태**: 승인됨

---

## 목표

1. OpenTelemetry 기반 분산 추적 — 멀티에이전트 호출 체인 전체를 하나의 trace-id로 묶어 로그에서 가시화
2. 병렬 Tool Execution — host-agent가 다운스트림 에이전트를 동시에 호출해 레이턴시 감소

---

## 아키텍처 개요

### 현재

```
AgentCore → host-agent → order-agent    (순차)
                       → delivery-agent (순차)
                       → payment-agent  (순차)
→ 추적 없음, 병목 지점 불투명
```

### 목표

```
AgentCore → host-agent ──┬→ order-agent    (동시)
      [trace: abc123]     ├→ delivery-agent (동시)
                          └→ payment-agent  (동시)
→ 전체 체인이 하나의 trace-id로 로그에 출력
→ 각 에이전트 span 시간 개별 측정
```

---

## 모듈 구조

### 신규 모듈

```
auto-configurations/observability/
└── spring-ai-a2a-autoconfigure-observability/
    └── A2AObservabilityAutoConfiguration
        ├── @ConditionalOnClass(ObservationRegistry.class)
        ├── @ConditionalOnProperty(prefix="spring.ai.a2a.observability", name="enabled", matchIfMissing=true)
        ├── @AutoConfiguration(after = {MicrometerAutoConfiguration.class, ObservationAutoConfiguration.class})
        ├── ObservationRegistry 빈 (@ConditionalOnMissingBean) — ObservationAutoConfiguration 없는 환경 fallback
        └── OtlpMeterRegistry 빈 (OTEL_EXPORTER_OTLP_ENDPOINT 환경변수 존재 시) — 사용자가 opentelemetry-exporter-otlp를 직접 추가해야 활성화
```

`spring/autoconfigure/auto-configuration.imports`에 등록 필요.
`settings.gradle.kts`에 `:spring-ai-a2a-autoconfigure-observability` include 추가 필요.

### 기존 모듈 변경

| 모듈 | 변경 내용 |
|------|----------|
| `spring-ai-a2a-agent-common` | `spring-boot-starter-web` 의존성 제거 (완료) |
| `spring-ai-a2a-server` | `DefaultAgentExecutor`에 `ObservationRegistry` 생성자 주입, `micrometer-observation` 의존성 추가 |
| 각 sample 모듈 | `spring-ai-a2a-autoconfigure-observability` 의존성 추가 (bridge 포함됨) |

> `DefaultAgentExecutor`에 `ObservationRegistry` 주입을 위해 `spring-ai-a2a-server`의
> 생성자 시그니처가 변경된다. 라이브러리 사용자 영향 범위 검토 필요.

---

## 계측 지점

| 위치 | 계측 방법 | Span 이름 |
|------|----------|-----------|
| `InvocationsController.invoke()` | Spring MVC 자동 계측 | `POST /invocations` |
| `RemoteAgentConnections.sendMessage()` | `Observation` 수동 wrap | `a2a.agent.send` (agentName 태그 포함) |
| `DeliveryAgentClient.send()` | `Observation` 수동 wrap | `a2a.agent.send` (agentName 태그 포함) |
| `PaymentAgentClient.send()` | `Observation` 수동 wrap | `a2a.agent.send` (agentName 태그 포함) |
| `DefaultAgentExecutor.execute()` | `Observation` 수동 wrap | `a2a.agent.execute` |

> `A2ATransport`는 Spring 비의존 static 유틸리티로 유지. 계측은 이미 `@Component`인 호출부에서 담당.
> `@Observed`는 사용하지 않음 — `DefaultAgentExecutor.execute()`는 A2A SDK가 직접 호출하므로 AOP 프록시 미경유.

### 호출부 계측 패턴

```java
// RemoteAgentConnections, DeliveryAgentClient, PaymentAgentClient (이미 @Component)
// ObservationRegistry 생성자 주입 후:

Observation.createNotStarted("a2a.agent.send", observationRegistry)
    .lowCardinalityKeyValue("agentName", agentName)
    .observe(() -> A2ATransport.send(agentCard, message));
```

---

## Trace 전파 흐름

```
host-agent (span: POST /invocations)
  └─ RemoteAgentConnections.sendMessage() (span: a2a.agent.send)
       → A2A SDK 내부 HTTP 요청에 traceparent 헤더 수동 주입
       → order-agent (span: a2a.agent.execute)
            └─ DeliveryAgentClient.send() (span: a2a.agent.send)
                 → delivery-agent (span: ...)
```

**[TBD — 구현 전 확인 필요]** `A2ATransport`는 A2A SDK의 `JdkA2AHttpClient`(`java.net.http.HttpClient`)를
사용하므로 Micrometer RestClient 자동 전파가 적용되지 않는다.
`JSONRPCTransportConfig`에 커스텀 HTTP 헤더 주입 API 존재 여부를 A2A SDK 0.3.3.Final 소스에서 확인 필요.
API가 없을 경우 대안:
- `JSONRPCTransportConfig` 서브클래싱
- SDK가 제공하는 인터셉터/데코레이터 패턴 사용

---

## 병렬 Tool Execution

### 한계: Spring AI 순차 실행

CLAUDE.md에 명시된 대로, Spring AI Tool Calling은 기본적으로 **순차 실행**이다.
`parallelToolCalls(true)`는 LLM에게 병렬 tool call을 허용하는 힌트일 뿐, Java 측 실행을 바꾸지 않는다.
`BedrockConverseOptions`의 지원 여부도 구현 전 확인이 필요하다.

### 전략: CompletableFuture 기반 병렬 디스패치

Spring AI tool call 실행 루프를 병렬 디스패치로 대체:

```
LLM 응답 (여러 tool call 포함)
  └─ ToolCallParallelExecutor (신규)
       ├─ CompletableFuture.supplyAsync(() → order-agent 호출, a2aTaskExecutor)
       ├─ CompletableFuture.supplyAsync(() → delivery-agent 호출, a2aTaskExecutor)
       └─ CompletableFuture.allOf(...).join()
```

- 기존 `a2aTaskExecutor` (virtual thread 기반) 재사용
- `A2ATransport.send()` blocking 코드 변경 없음 (virtual thread에서 안전)

**[TBD — 구현 전 확인 필요]** Spring AI 1.1.3의 `ChatClient` / `DefaultChatClient` 내부 tool call 루프의
공개 확장 지점(`ToolCallingManager`, `ToolCallResultConverter` 등) 수준 확인 필요.
확장 지점이 제한적일 경우 `ChatClient` wrapping 방식으로 우회 전략 수립.

Observability와 독립적으로 구현 가능하므로 **별도 단계(Phase 2)**로 진행.

---

## 에러 처리

| 상황 | 처리 방식 |
|------|----------|
| OTLP 백엔드 연결 불가 | Exporter가 자동 drop — 앱 동작에 영향 없음 |
| 다운스트림 에이전트 타임아웃 | span에 `error=true` + 예외 메시지 기록, `A2ATransport` 기존 에러 문자열 반환 동작 유지 |
| trace 헤더 없는 요청 | 새 root span 자동 생성 — 기존 동작 유지 |

> Observability는 비기능 요소 — 실패해도 비즈니스 로직에 절대 영향 없음

---

## 로그 출력 예시

```
[host-agent]     [traceId=abc123 spanId=def456] POST /invocations
[order-agent]    [traceId=abc123 spanId=ghi789] a2a.agent.execute  ← 동일 trace-id
[delivery-agent] [traceId=abc123 spanId=jkl012] a2a.agent.execute  ← 동일 trace-id
```

OTLP exporter 활성화: `OTEL_EXPORTER_OTLP_ENDPOINT` 환경변수 설정 +
사용자가 `opentelemetry-exporter-otlp` 의존성 직접 추가 필요.

---

## 테스트 전략

| 종류 | 내용 |
|------|------|
| 단위 테스트 | `RemoteAgentConnections` / `DeliveryAgentClient` / `PaymentAgentClient` — `ObservationRegistry` mock으로 span 생성 검증 |
| 통합 테스트 | `HostAgentIntegrationTest` 확장 — 다운스트림 요청에 `traceparent` 헤더가 포함되는지 검증 |
| 병렬 실행 검증 | 병렬 실행 구현 완료 후 — 실행 시간이 순차 합산보다 짧은지 확인 |

---

## 의존성 추가

```kotlin
// spring-ai-a2a-autoconfigure-observability/build.gradle.kts
implementation("io.micrometer:micrometer-tracing-bridge-otel")
compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")  // 사용자 직접 추가 필요

// spring-ai-a2a-server/build.gradle.kts
implementation("io.micrometer:micrometer-observation")

// 각 에이전트 sample build.gradle.kts
implementation(project(":spring-ai-a2a-autoconfigure-observability"))  // bridge 포함
```

---

## 구현 순서

### Phase 1: Observability

1. `spring-ai-a2a-autoconfigure-observability` 모듈 생성 (`auto-configurations/observability/` 하위, `settings.gradle.kts` include 추가)
2. **[TBD 확인]** A2A SDK `JSONRPCTransportConfig` 헤더 주입 API 조사 → `traceparent` 주입 방식 확정
3. `RemoteAgentConnections`, `DeliveryAgentClient`, `PaymentAgentClient`에 `ObservationRegistry` 주입 + `Observation` 수동 wrap
4. `DefaultAgentExecutor.execute()` `Observation` 수동 wrap (`micrometer-observation` 의존성 추가)
5. 각 sample 모듈에 `spring-ai-a2a-autoconfigure-observability` 의존성 추가
6. 단위 테스트 + 통합 테스트 작성

### Phase 2: 병렬 실행 (Phase 1 완료 후)

7. **[TBD 확인]** Spring AI 1.1.3 tool call 확장 지점 조사
8. `ToolCallParallelExecutor` 구현 + `a2aTaskExecutor` 연동
9. `parallelToolCalls` LLM 옵션 설정 (BedrockConverseOptions 지원 여부 확인 후)
10. 병렬 실행 검증 테스트
