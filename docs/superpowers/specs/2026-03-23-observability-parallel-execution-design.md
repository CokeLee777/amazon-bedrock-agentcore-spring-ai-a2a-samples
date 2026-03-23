# Observability + Parallel Tool Execution 설계

**날짜**: 2026-03-23
**브랜치**: feat/observability-parallel-execution
**상태**: 승인됨

---

## 목표

1. OpenTelemetry 기반 분산 추적 — 멀티에이전트 호출 체인 전체를 하나의 trace-id로 묶어 로그에서 가시화
2. 병렬 Tool Execution — host-agent가 다운스트림 에이전트를 순차가 아닌 동시에 호출해 레이턴시 감소

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
        ├── ObservationRegistry 빈 (없을 때만 등록)
        └── OTLP exporter (OTEL_EXPORTER_OTLP_ENDPOINT 환경변수 설정 시 조건부 활성화)
```

### 기존 모듈 변경

| 모듈 | 변경 내용 |
|------|----------|
| `spring-ai-a2a-agent-common` | `A2ATransport.send()`에 span 계측 추가 |
| `spring-ai-a2a-autoconfigure-server` | `ChatClient` 빌드 시 `parallelToolCalls(true)` 옵션 추가 |
| 각 sample 모듈 | `micrometer-tracing-bridge-otel` 의존성 추가 |

---

## 계측 지점

| 위치 | 계측 방법 | Span 이름 |
|------|----------|-----------|
| `InvocationsController.invoke()` | Spring MVC 자동 계측 | `POST /invocations` |
| `A2ATransport.send()` | `ObservationRegistry` 수동 wrap | `a2a.agent.send` (agentName 태그 포함) |
| `DefaultAgentExecutor.execute()` | `@Observed` 또는 수동 wrap | `a2a.agent.execute` |

> `A2ATransport` 한 곳만 계측하면 모든 에이전트 간 호출이 자동으로 추적됨.

---

## Trace 전파 흐름

```
host-agent (span: POST /invocations)
  └─ A2ATransport.send("order-agent")
       → HTTP 헤더에 traceparent 자동 삽입 (W3C TraceContext)
       → order-agent (span: a2a.agent.send, a2a.agent.execute)
            └─ A2ATransport.send("delivery-agent")
                 → delivery-agent (span: ...)
```

Spring `RestClient` + Micrometer가 W3C `traceparent` 헤더 전파를 자동 처리.

---

## 병렬 Tool Execution

Spring AI `ChatClient`에 `parallelToolCalls(true)` 설정:

```java
// A2AServerAutoConfiguration
ChatClient.builder(chatModel)
    .defaultOptions(options -> options.parallelToolCalls(true))
    .build();
```

- LLM(Claude 3.x+, Bedrock Converse)이 한 번의 응답에 여러 tool call을 내려줄 때 동시 실행
- `A2ATransport.send()`는 blocking이지만 virtual thread 기반이므로 OS 스레드 낭비 없이 I/O 중첩 가능
- 별도 async 리팩터링 불필요

---

## 에러 처리

| 상황 | 처리 방식 |
|------|----------|
| OTLP 백엔드 연결 불가 | Exporter가 자동 drop — 앱 동작에 영향 없음 |
| 다운스트림 에이전트 타임아웃 | span에 `error=true` + 예외 메시지 기록 후 기존 예외 그대로 전파 |
| trace 헤더 없는 요청 | 새 root span 자동 생성 — 기존 동작 유지 |

> Observability는 비기능 요소 — 실패해도 비즈니스 로직에 절대 영향 없음

---

## 로그 출력 예시

```
[host-agent]     [trace=abc123 span=def456] POST /invocations
[order-agent]    [trace=abc123 span=ghi789] a2a.agent.execute  ← 동일 trace-id
[delivery-agent] [trace=abc123 span=jkl012] a2a.agent.execute  ← 동일 trace-id
```

에이전트 간 호출 체인이 같은 `trace-id`로 묶임. OTLP exporter는 `OTEL_EXPORTER_OTLP_ENDPOINT` 환경변수 설정만으로 활성화.

---

## 테스트 전략

| 종류 | 내용 |
|------|------|
| 단위 테스트 | `A2ATransport` — `ObservationRegistry` mock으로 span 생성 여부 검증 |
| 통합 테스트 | `HostAgentIntegrationTest` 확장 — `traceparent` 헤더가 다운스트림 요청에 전파되는지 검증 |
| 병렬 실행 검증 | 여러 tool call 시 실행 시간이 순차 합산보다 짧은지 확인 (기존 `A2AVirtualThreadIntegrationTest` 패턴 재사용) |

---

## 의존성 추가

```kotlin
// 각 에이전트 build.gradle.kts
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("io.opentelemetry:opentelemetry-exporter-otlp") // 조건부

// spring-ai-a2a-autoconfigure-observability
implementation("io.micrometer:micrometer-tracing-bridge-otel")
compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
```

---

## 구현 순서

1. `spring-ai-a2a-autoconfigure-observability` 모듈 생성
2. `A2ATransport.send()` span 계측
3. `DefaultAgentExecutor` span 계측
4. `A2AServerAutoConfiguration` parallel tool calls 옵션 추가
5. 각 sample 모듈 의존성 추가
6. 테스트 작성 및 검증
