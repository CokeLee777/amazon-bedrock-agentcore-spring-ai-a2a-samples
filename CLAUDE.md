# CLAUDE.md

## 프로젝트 개요

Amazon Bedrock AgentCore Runtime + Spring AI + A2A 프로토콜을 활용한 멀티 에이전트 오케스트레이션 샘플.
AgentCore Runtime이 세션을 관리하며, host-agent(오케스트레이터)가 Spring AI tool-calling으로 다운스트림 A2A 에이전트를 호출한다.

## 빌드 / 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 각 에이전트 실행 (로컬: local 프로파일 활성화 필요)
SPRING_PROFILES_ACTIVE=local ./gradlew :host-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :order-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :delivery-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :payment-agent:bootRun

# 특정 모듈 컴파일 확인
./gradlew :spring-ai-a2a-agent-common:compileJava
```

Gradle Kotlin DSL 사용: 루트 `build.gradle.kts`, `settings.gradle.kts`, 서브프로젝트 `build.gradle.kts`.

## 모듈 구조

```
spring-ai-a2a/
├── spring-ai-a2a-agent-common/              # 공유 유틸리티
│   ├── A2ATransport                         # A2A 클라이언트 (TaskEvent 수신, 동기 블로킹)
│   ├── LazyAgentCard                        # AgentCard lazy 로딩·캐싱 (시작 실패 시 첫 호출에 재시도)
│   └── util/TextExtractor                   # Task/Message에서 텍스트 추출
├── spring-ai-a2a-server/                    # A2A 서버 구현체
│   ├── controller/AgentCardController       # GET /.well-known/agent-card.json
│   ├── controller/MessageController         # A2A JSON-RPC 메시지 엔드포인트
│   ├── controller/TaskController            # A2A Task API 엔드포인트
│   ├── executor/DefaultAgentExecutor        # AgentExecutor 기본 구현체 (task 생명주기 관리)
│   └── executor/ChatClientExecutorHandler   # 에이전트 로직 위임 함수형 인터페이스
├── auto-configurations/agent/common/
│   └── spring-ai-a2a-autoconfigure-agent-common/   # 에이전트 공통 자동 구성
│       ├── AgentCommonAutoConfiguration     # PingController, RemoteAgentProperties, RemoteAgentCardRegistry
│       ├── RemoteAgentProperties              # @ConfigurationProperties(prefix=a2a.remote)
│       └── RemoteAgentCardRegistry            # LazyAgentCard 레지스트리
├── auto-configurations/server/
│   └── spring-ai-a2a-autoconfigure-server/          # A2A 서버 인프라 자동 구성
│       ├── A2AServerAutoConfiguration       # ChatClient 클래스가 클래스패스에 있을 때 활성화
│       │   ├── a2aTaskExecutor              # Virtual thread 기반 Executor (에이전트 비동기 실행)
│       │   ├── DefaultValuesConfigProvider  # A2A SDK 기본값 (a2a.blocking.* 등)
│       │   └── SpringA2AConfigProvider      # Environment + 기본값 폴백 (커스텀 시 INFO 로그)
│       └── A2AServerProperties              # @ConfigurationProperties(prefix=spring.ai.a2a.server)
├── auto-configurations/models/chat/memory/repository/
│   └── spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core/
│       ├── BedrockChatMemoryAutoConfiguration   # @AutoConfiguration(before=ChatMemoryAutoConfiguration)
│       └── BedrockChatMemoryRepositoryProperties  # @ConfigurationProperties(prefix=spring.ai.chat.memory.repository.bedrock.agent-core)
├── memory/repository/
│   └── spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/
│       ├── BedrockChatMemoryRepository      # ChatMemoryRepository 구현체 (append, conversationId="actorId:sessionId")
│       ├── AgentCoreEventToMessageConverter # AgentCore Event → Spring AI Message 변환
│       └── BedrockChatMemoryRepositoryConfig  # memoryId, maxTurns 설정 빈
├── spring-ai-a2a-starters/                  # 의존성 묶음용 starter (java-library, api 전이)
│   ├── spring-ai-a2a-starter-agent-common/       # agent-common + autoconfigure-agent-common
│   ├── spring-ai-a2a-starter-server/             # server + autoconfigure-server
│   └── spring-ai-a2a-starter-model-chat-memory-repository-bedrock-agent-core/  # Bedrock memory impl + autoconfigure
├── samples/
│   ├── host-agent/   (port: 8080)           # AgentCore Runtime 진입점 · 오케스트레이터 (starter-agent-common + starter-model-chat-memory-repository-bedrock-agent-core)
│   │   ├── invocation/                      # POST /invocations, 오케스트레이션
│   │   │   ├── InvocationController         # REST 엔드포인트
│   │   │   ├── InvocationConfiguration      # orchestrator ChatClient @Bean (defaultTools + advisors)
│   │   │   ├── DefaultInvocationService     # ChatMemoryRepository, actorId:sessionId 복합키
│   │   │   └── InvocationRequest/Response, InvocationService
│   │   ├── remote/                          # 다운스트림 A2A 연동
│   │   │   ├── RemoteAgentTools             # @Tool delegateToRemoteAgent / delegateToRemoteAgentsParallel, RemoteAgentCardRegistry 주입
│   │   │   └── RemoteAgentDelegationRequest # 툴 파라미터 record
│   │   └── HostAgentApplication             # 부트스트랩
│   ├── order-agent/  (port: 9001)           # 주문 조회 · 취소 가능 여부 확인 A2A 에이전트 (starter-agent-common + starter-server)
│   │   ├── OrderTools                       # getOrderList, checkOrderCancellability
│   │   ├── DeliveryAgentClient              # delivery-agent 호출 클라이언트 (LazyAgentCard)
│   │   ├── PaymentAgentClient               # payment-agent 호출 클라이언트 (LazyAgentCard)
│   │   └── OrderAgentConfiguration          # A2A 서버 빈; 다운스트림 URL은 a2a.remote.agents (autoconfigure)
│   ├── delivery-agent/ (port: 9002)         # 배송 추적 A2A 에이전트 (starter-agent-common + starter-server)
│   │   └── DeliveryTools                    # trackDelivery
│   └── payment-agent/ (port: 9003)          # 결제/환불 상태 확인 A2A 에이전트 (starter-agent-common + starter-server)
│       └── PaymentTools                     # getPaymentStatus
└── spring-ai-a2a-integration-tests/         # 전체 에이전트 통합 테스트 (jar 미생성)
    ├── HostAgentIntegrationTest
    ├── OrderAgentIntegrationTest
    ├── DeliveryAgentIntegrationTest
    └── PaymentAgentIntegrationTest
```

## 핵심 설계 결정 사항

### Starter 모듈 (`spring-ai-a2a-starters`)

- Gradle 서브프로젝트로 **구현·autoconfigure JAR를 한 의존성으로 묶는** 용도다. `java-library` 플러그인과 `api(project(...))`로 하위 모듈을 선언해, 소비 앱이 예전처럼 각 JAR의 타입을 컴파일 클래스패스에서 쓸 수 있게 전이한다.
- 소스는 없어도 되며(빈 JAR), 샘플은 아래 조합을 사용한다.
  - **A2A 다운스트림 에이전트** (`order-agent`, `delivery-agent`, `payment-agent`): `spring-ai-a2a-starter-agent-common` + `spring-ai-a2a-starter-server`
  - **host-agent**: `spring-ai-a2a-starter-agent-common` + `spring-ai-a2a-starter-model-chat-memory-repository-bedrock-agent-core`
- 외부 프로젝트는 동일 starter 좌표를 쓰거나, 필요 시 개별 모듈만 골라 `implementation`할 수 있다.

### A2ATransport

- 다운스트림 에이전트 서버는 `emitter.addArtifact()` + `emitter.complete()`로 **Task** 를 반환한다.
  따라서 클라이언트는 반드시 `TaskEvent` 를 수신해야 한다. `MessageEvent` 는 절대 발생하지 않는다.
- `sendMessage()` 는 동기 블로킹 호출이다. `CompletableFuture.supplyAsync()` 로 별도 스레드에서 실행하고
  `.get(timeoutSeconds, TimeUnit.SECONDS)` 로 실제 타임아웃을 강제한다.

### LazyAgentCard (spring-ai-a2a-agent-common)

- 생성자에서 즉시 AgentCard fetch를 시도하고, 실패하면 URL을 보관한다.
- `get()` — card가 null이면 매 호출마다 재시도. 실제 에이전트 통신 직전에 사용한다.
- `peek()` — 네트워크 호출 없이 현재 캐시 상태만 반환. 시스템 프롬프트 생성 등 정보 조회에 사용한다.
  (startup 시 `get()` 대신 `peek()`를 쓰지 않으면 에이전트 수 × 조회 횟수만큼 불필요한 WARN이 발생한다.)

### RemoteAgentCardRegistry (`spring-ai-a2a-autoconfigure-agent-common`)

- `a2a.remote.agents` YAML 맵 키(예: `order-agent`)마다 하나의 `LazyAgentCard`를 등록한다. (과거 `remote.agents`는 **`a2a.remote.agents`** 로 이전.)
- `findCardByAgentName` — LLM이 쓰는 라우팅 이름은 `AgentCard#name()`(카드 JSON) 기준. 각 `LazyAgentCard`에 대해 `peek()`가 비어 있으면 해당 항목만 `get()`으로 해소한 뒤 첫 일치를 반환한다.
- `findLazyCardByAgentName` — 설정 맵의 **키**로 `LazyAgentCard`를 꺼낸다. `order-agent`의 `DeliveryAgentClient` / `PaymentAgentClient`가 사용한다.
- `getAgentDescriptions` — 오케스트레이터 시스템 프롬프트용 JSON 줄 묶음. 캐시가 하나라도 있으면 `peek()`만 사용하고, 전부 비어 있으면 각 카드에 `get()`을 시도한다(첫 사용자 턴에서도 라우팅 가능한 목록을 주기 위함).
- `peekCachedAgentCards` — 네트워크 없이 캐시된 카드만 스냅샷(알 수 없는 에이전트 에러 메시지의 “사용 가능한 에이전트” 목록 등).

### RemoteAgentTools (`host-agent` → `remote`)

- 패키지: `io.github.cokelee777.agent.host.remote`.
- `@Tool` 메서드 `delegateToRemoteAgent(RemoteAgentDelegationRequest)` 로 단일 다운스트림 호출, 병렬 배치는 `delegateToRemoteAgentsParallel(List<RemoteAgentDelegationRequest>)`.
- 각 파라미터에 `@ToolParam(description = "...")` 을 달아 LLM이 올바른 값을 추론하도록 돕는다.
- `RemoteAgentCardRegistry`를 주입받는다. `delegateToRemoteAgent` / `delegateToRemoteAgentsParallel`은 `findCardByAgentName`으로 카드를 해소하고 `A2ATransport`로 전송한다. 알 수 없는 이름일 때는 `peekCachedAgentCards()` 기반으로 영문 한 줄 에러를 반환한다.

### Invocation 경로 (`host-agent` → `invocation`)

- `InvocationController` — AgentCore Runtime → `POST /invocations`.
- `InvocationConfiguration` — orchestrator용 `ChatClient` 빈 (`RemoteAgentTools`를 defaultTools로 등록, `SimpleLoggerAdvisor` 등).
- `DefaultInvocationService` — 시스템 프롬프트를 **매 요청마다** `RemoteAgentCardRegistry#getAgentDescriptions()`로 동적 생성한다.
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
- `auto-configurations/server/spring-ai-a2a-autoconfigure-server/src/main/resources/application.yml`에 기본값이 있으며, 각 에이전트 모듈의 `application.yml`에서 재정의하면 **오버라이드**된다 (Spring Boot: 메인 앱 설정이 라이브러리보다 우선).
- 커스텀 값을 쓰면 `SpringA2AConfigProvider`에서 INFO 로그로 `Using custom A2A config: key=value` 또는 `Using custom A2A optional config: key=value` 출력.

### BedrockChatMemoryRepositoryProperties (autoconfigure 모듈)

- prefix: `spring.ai.chat.memory.repository.bedrock.agent-core`. 환경변수 `BEDROCK_MEMORY_ID` 등으로 오버라이드.
- `memoryId` — `@Nullable`. `memory-id` 미설정 시 autoconfigure 전체가 비활성화되어 Spring AI의 `InMemoryChatMemoryRepository`가 폴백으로 등록된다.
- `maxTurns` — `@Min(1)`, 기본 10. `listEvents`에서 `maxResults = maxTurns * 2`로 사용.

### BedrockChatMemoryAutoConfiguration

- `@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)` — Spring AI InMemory 폴백보다 먼저 등록된다.
- `@ConditionalOnClass(BedrockChatMemoryRepository.class)` — 클래스패스에 구현체 모듈이 있을 때만 활성화.
- `@ConditionalOnProperty(prefix=CONFIG_PREFIX, name="memory-id")` — `memory-id` 미설정 시 비활성화 → InMemory 폴백.
- `bedrockAgentCoreClient` 빈에 `@ConditionalOnMissingBean` 적용 → 테스트에서 mock 주입 가능.

### BedrockChatMemoryRepository (impl 모듈)

- `ChatMemoryRepository` 구현체. `conversationId = "actorId:sessionId"` 복합키 파싱.
- `findByConversationId` — `listEvents(maxResults = maxTurns * 2)` → `AgentCoreEventToMessageConverter` 변환, 오름차순 정렬.
- `saveAll` — `createEvent` 호출 (append-only, `JdbcChatMemoryRepository`와 동일한 semantics). `DefaultInvocationService`는 새 2개 메시지만 전달.
- `deleteByConversationId` — `UnsupportedOperationException` (삭제 API 미지원).
- `findConversationIds` — `UnsupportedOperationException` (목록 API 미지원).

### auto-configure 활성화 조건

- `A2AServerAutoConfiguration` — `@ConditionalOnClass(ChatClient.class)` + `@ConditionalOnProperty(spring.ai.a2a.server.enabled, matchIfMissing=true)`. A2A 서버 인프라(컨트롤러, Executor, TaskStore 등) 자동 구성.
- `AgentCommonAutoConfiguration` — 조건 없이 항상 활성화. `PingController` (`GET /ping`), `@EnableConfigurationProperties(RemoteAgentProperties)` (`a2a.remote.*`), `RemoteAgentCardRegistry` 빈 등록.

## Docker 빌드

모든 Dockerfile의 빌드 컨텍스트는 **프로젝트 루트**다.

```bash
# host-agent (AgentCore 배포 → ARM64 필수)
docker buildx build --platform linux/arm64 \
  -f samples/host-agent/Dockerfile \
  -t host-agent:arm64 --load .

# 다운스트림 에이전트 (arm64)
docker buildx build --platform linux/arm64 \
  -f samples/order-agent/Dockerfile    -t order-agent:latest    --load .
docker buildx build --platform linux/arm64 \
  -f samples/delivery-agent/Dockerfile -t delivery-agent:latest --load .
docker buildx build --platform linux/arm64 \
  -f samples/payment-agent/Dockerfile  -t payment-agent:latest  --load .
```

## 의존성 버전 (루트 build.gradle.kts extra)

- `springAiVersion` — 1.1.3
- `awsSdkVersion` — 2.42.9
- `a2aVersion` — 0.3.3.Final
- `gsonVersion` — 2.13.2

## 코드 컨벤션

- `io.spring.javaformat` 플러그인으로 Spring 공식 포맷을 적용한다.
- 모든 public 타입과 메서드에 Javadoc(`/** */`)을 작성한다.
- 설정값은 환경변수로 오버라이드 가능하도록 `${VAR:default}` 패턴을 사용한다.
- `@Tool` 메서드 파라미터에는 반드시 `@ToolParam(description = "...")` 을 달아 LLM 추론을 돕는다.
