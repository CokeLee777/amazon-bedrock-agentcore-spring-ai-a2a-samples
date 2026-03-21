# CLAUDE.md

## 프로젝트 개요

Amazon Bedrock AgentCore Runtime + Spring AI + A2A 프로토콜을 활용한 멀티 에이전트 오케스트레이션 샘플.
AgentCore Runtime이 세션을 관리하며, host-agent(오케스트레이터)가 Spring AI tool-calling으로 다운스트림 A2A 에이전트를 호출한다.

## 빌드 / 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 각 에이전트 실행 (로컬: local 프로파일 활성화 필요)
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:host-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:order-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:delivery-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:payment-agent:bootRun

# 특정 모듈 컴파일 확인
./gradlew :agent-common:compileJava
```

Gradle Kotlin DSL 사용: 루트 `build.gradle.kts`, `settings.gradle.kts`, 서브프로젝트 `build.gradle.kts`.

## 모듈 구조

```
amazon-bedrock-agentcore-spring-ai-a2a-samples/
├── agent-common/                              # 공유 유틸리티
│   ├── A2ATransport                         # A2A 클라이언트 (TaskEvent 수신, 동기 블로킹)
│   ├── LazyAgentCard                        # AgentCard lazy 로딩·캐싱 (시작 실패 시 첫 호출에 재시도)
│   └── util/TextExtractor                   # Task/Message에서 텍스트 추출
├── spring-ai-a2a-server/                    # A2A 서버 구현체 (JSON-RPC, AgentCard, Ping 컨트롤러)
├── spring-ai-a2a-server-autoconfigure/      # A2A 서버·공통 자동 구성
│   ├── A2AServerAutoConfiguration           # AgentExecutor 빈 있을 때만 활성화
│   │   ├── a2aTaskExecutor                  # Virtual thread 기반 Executor (에이전트 비동기 실행)
│   │   ├── DefaultValuesConfigProvider      # A2A SDK 기본값 (a2a.blocking.* 등)
│   │   └── SpringA2AConfigProvider          # Environment + 기본값 폴백 (커스텀 시 DEBUG 로그)
│   └── A2ACommonAutoConfiguration           # AgentCard 빈 있을 때만 활성화
├── agents/
│   ├── host-agent/   (port: 8080)           # AgentCore Runtime 진입점 · 오케스트레이터
│   │   ├── InvocationsController            # POST /invocations, 요청마다 동적 시스템 프롬프트 생성
│   │   ├── RemoteAgentConnections           # 다운스트림 에이전트 호출 Tool (@Tool), LazyAgentCard 맵 관리
│   │   ├── RemoteAgentProperties            # 다운스트림 에이전트 URL 설정
│   │   ├── config/BedrockMemoryConfiguration  # mode != none 일 때 Bedrock 메모리 빈 등록
│   │   ├── config/NoOpMemoryConfiguration   # mode == none 일 때 no-op 메모리 빈 등록
│   │   ├── memory/bedrock/BedrockMemoryProperties  # @ConfigurationProperties(prefix=aws.bedrock.agent-core.memory)
│   │   ├── memory/bedrock/BedrockConversationMemoryService  # 단기 기억 (listEvents / createEvent)
│   │   └── memory/bedrock/BedrockLongTermMemoryService     # 장기 기억 (retrieveMemoryRecords)
│   ├── order-agent/  (port: 9001)           # 주문 조회 · 취소 가능 여부 확인 A2A 에이전트
│   │   ├── OrderTools                       # getOrderList, checkOrderCancellability
│   │   ├── DeliveryAgentClient              # delivery-agent 호출 클라이언트 (LazyAgentCard)
│   │   ├── PaymentAgentClient               # payment-agent 호출 클라이언트 (LazyAgentCard)
│   │   └── RemoteAgentProperties            # 다운스트림 에이전트 URL 설정
│   ├── delivery-agent/ (port: 9002)         # 배송 추적 A2A 에이전트
│   │   └── DeliveryTools                    # trackDelivery
│   └── payment-agent/ (port: 9003)          # 결제/환불 상태 확인 A2A 에이전트
│       └── PaymentTools                     # getPaymentStatus
└── integration-tests/                       # 전체 에이전트 통합 테스트 (jar 미생성)
    ├── HostAgentIntegrationTest
    ├── OrderAgentIntegrationTest
    ├── DeliveryAgentIntegrationTest
    └── PaymentAgentIntegrationTest
```

## 핵심 설계 결정 사항

### A2ATransport

- 다운스트림 에이전트 서버는 `emitter.addArtifact()` + `emitter.complete()`로 **Task** 를 반환한다.
  따라서 클라이언트는 반드시 `TaskEvent` 를 수신해야 한다. `MessageEvent` 는 절대 발생하지 않는다.
- `sendMessage()` 는 동기 블로킹 호출이다. `CompletableFuture.supplyAsync()` 로 별도 스레드에서 실행하고
  `.get(timeoutSeconds, TimeUnit.SECONDS)` 로 실제 타임아웃을 강제한다.

### LazyAgentCard (agent-common)

- 생성자에서 즉시 AgentCard fetch를 시도하고, 실패하면 URL을 보관한다.
- `get()` — card가 null이면 매 호출마다 재시도. 실제 에이전트 통신 직전에 사용한다.
- `peek()` — 네트워크 호출 없이 현재 캐시 상태만 반환. 시스템 프롬프트 생성 등 정보 조회에 사용한다.
  (startup 시 `get()` 대신 `peek()`를 쓰지 않으면 에이전트 수 × 조회 횟수만큼 불필요한 WARN이 발생한다.)

### RemoteAgentConnections (host-agent)

- `@Tool` 메서드 `sendMessage(agentName, task)` 하나로 모든 다운스트림 에이전트 호출을 처리한다.
- 각 파라미터에 `@ToolParam(description = "...")` 을 달아 LLM이 올바른 값을 추론하도록 돕는다.
- 내부적으로 `Map<String, LazyAgentCard>` (config key → LazyAgentCard)를 관리한다.
  `sendMessage` 는 card name으로 조회(`get()` 경유), `getAgentDescriptions`/`getAgentNames` 는 `peek()` 경유.
- 로컬 실행 시 헤더가 없으면 UUID로 폴백하여 개발/테스트 편의를 보장한다.

### InvocationsController (host-agent)

- 시스템 프롬프트를 **매 요청마다** `connections.getAgentDescriptions()`로 동적 생성한다.
  빈 초기화 시 고정(static)하면 시작 후 lazy 로드된 에이전트가 프롬프트에 반영되지 않는다.

### Spring AI Tool Calling

- `ChatClient.call()` 은 동기 블로킹 agentic loop다. 스트리밍은 `.stream()` 을 사용해야 한다.
- 툴 실행은 기본적으로 **순차 실행**이다. LLM이 한 번의 응답에 여러 툴을 요청해도 Java 측에서 순차 처리한다.
- 단일 필드 래퍼 record 대신 `@ToolParam`이 달린 단일 파라미터를 사용한다.

### a2aTaskExecutor (Virtual thread)

- A2A 에이전트 실행은 **virtual thread** 기반 `a2aTaskExecutor` 빈에서 수행된다.
- `Thread.ofVirtual().name("a2a-task-", 1).factory()` + `newThreadPerTaskExecutor` 사용.
- `DefaultRequestHandler`에 이 Executor가 주입되므로, SDK의 `a2a.executor.*`(스레드 풀 설정) 기본값은 **사용되지 않는다.** (Executor를 우리가 제공하기 때문.)
- `DefaultValuesConfigProvider` / `SpringA2AConfigProvider`는 **스레드가 아니라 A2A 설정값**용이다. `a2a.blocking.agent.timeout.seconds`, `a2a.blocking.consumption.timeout.seconds` 등은 SDK가 사용하며, 지우면 안 된다.
- Virtual thread 사용 검증: `A2AVirtualThreadIntegrationTest` 실행 또는 `DefaultAgentExecutor` 로거를 DEBUG로 올려 `Executing agent on thread a2a-task-N (virtual=true)` 로그 확인.

### A2A 서버 설정 (blocking 타임아웃)

- `a2a.blocking.agent.timeout.seconds` — 에이전트 실행(LLM·툴·다운스트림 호출) 완료 대기 최대 시간(초). 기본 30.
- `a2a.blocking.consumption.timeout.seconds` — 이벤트 소비/영속화(TaskStore 반영) 완료 대기 최대 시간(초). 기본 5.
- autoconfigure의 `src/main/resources/application.yml`에 기본값이 있으며, 각 에이전트 모듈의 `application.yml`에서 재정의하면 **오버라이드**된다 (Spring Boot: 메인 앱 설정이 라이브러리보다 우선).
- 커스텀 값을 쓰면 `SpringA2AConfigProvider`에서 INFO 로그로 `Using custom A2A config: key=value` 또는 `Using custom A2A optional config: key=value` 출력.

### BedrockMemoryProperties (host-agent)

- prefix: `aws.bedrock.agent-core.memory`. 환경변수 `BEDROCK_MEMORY_ID` 등으로 오버라이드.
- `mode` — 필수(`@NotNull`). 기본값 `none`. 미설정 시 AWS 연결 시도하지 않는다.
- `memoryId`, `strategyId` — `@Nullable`. properties 레벨에서는 선택. 실제 필요한 서비스 생성자에서 `Assert.hasText`로 검증.
  - `BedrockConversationMemoryService` 생성자: `memoryId` 필수 검증.
  - `BedrockLongTermMemoryService` 생성자: `memoryId` + `strategyId` 모두 필수 검증.
- `shortTermMaxTurns`(`@Min(1)`, 기본 10), `longTermMaxResults`(`@Min(1)`, 기본 4) — 항상 유효한 값 보장.
- `BedrockMemoryConfiguration` — `mode != none`일 때만 활성화. mode별 빈 분기:
  - `short_term`: `BedrockConversationMemoryService` + `NoOpLongTermMemoryService`
  - `long_term` / `both`: `BedrockConversationMemoryService` + `BedrockLongTermMemoryService`
- `NoOpMemoryConfiguration` — `mode == none`일 때 활성화. `BedrockMemoryProperties`도 함께 등록하여 `DefaultInvocationService`가 mode를 읽을 수 있도록 한다.

### auto-configure 활성화 조건

- `A2AServerAutoConfiguration` — `AgentExecutor` 빈이 컨텍스트에 있을 때만 활성화 (다운스트림 에이전트 서버용).
- `A2ACommonAutoConfiguration` — `AgentCard` 빈이 컨텍스트에 있을 때만 활성화 (Ping + AgentCard 컨트롤러).

## Docker 빌드

모든 Dockerfile의 빌드 컨텍스트는 **프로젝트 루트**다.

```bash
# host-agent (AgentCore 배포 → ARM64 필수)
docker buildx build --platform linux/arm64 \
  -f agents/host-agent/Dockerfile \
  -t host-agent:arm64 --load .

# 다운스트림 에이전트 (amd64)
docker buildx build --platform linux/amd64 \
  -f agents/order-agent/Dockerfile    -t order-agent:latest    --load .
docker buildx build --platform linux/amd64 \
  -f agents/delivery-agent/Dockerfile -t delivery-agent:latest --load .
docker buildx build --platform linux/amd64 \
  -f agents/payment-agent/Dockerfile  -t payment-agent:latest  --load .
```

## 의존성 버전 (루트 build.gradle.kts extra)

- `springAiVersion` — 1.1.2
- `awsSdkVersion` — 2.42.9
- `a2aVersion` — 0.3.3.Final
- `gsonVersion` — 2.13.2

## 코드 컨벤션

- `io.spring.javaformat` 플러그인으로 Spring 공식 포맷을 적용한다.
- 모든 public 타입과 메서드에 Javadoc(`/** */`)을 작성한다.
- 설정값은 환경변수로 오버라이드 가능하도록 `${VAR:default}` 패턴을 사용한다.
- `@Tool` 메서드 파라미터에는 반드시 `@ToolParam(description = "...")` 을 달아 LLM 추론을 돕는다.
