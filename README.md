# Spring AI A2A

Spring AI 기반 [A2A(Agent-to-Agent) 프로토콜](https://google.github.io/A2A/) 에이전트를 쉽게 구현할 수 있는 라이브러리입니다.
A2A 서버 인프라, 에이전트 공통 유틸리티, 대화 메모리 등을 Spring Boot Auto-configuration으로 제공하며,
`samples/` 디렉토리에 Amazon Bedrock AgentCore Runtime 기반 멀티 에이전트 오케스트레이션 예제가 포함되어 있습니다.

## 라이브러리 모듈

### spring-ai-a2a-server

A2A 프로토콜 서버 구현체입니다. Spring MVC 컨트롤러와 `AgentExecutor` 구현체를 제공합니다.

| 컴포넌트 | 설명 |
|---------|------|
| `AgentCardController` | `GET /.well-known/agent-card.json` — AgentCard 제공 |
| `MessageController` | A2A JSON-RPC 메시지 엔드포인트 (동기 JSON / `Accept: text/event-stream` 시 SSE) |
| `TaskController` | A2A Task API 엔드포인트 |
| `DefaultAgentExecutor` | Task 생명주기 관리, `ChatClientExecutorHandler`에 **동기** 실행 위임 (`String` 응답) |
| `StreamingAgentExecutor` | 동일 생명주기 + `StreamingChatClientExecutorHandler`의 **`Flux<String>`** 청크를 `TaskUpdater`로 **append 스트리밍** artifact |
| `ChatClientExecutorHandler` | 에이전트 로직을 정의하는 함수형 인터페이스 (`RequestContext → String`) |
| `StreamingChatClientExecutorHandler` | `RequestContext` 기준 `ChatClient` **스트림**을 `Flux<String>`으로 반환 |

### spring-ai-a2a-agent-common

A2A 클라이언트 측 공유 유틸리티입니다.

| 컴포넌트 | 설명 |
|---------|------|
| `A2ATransport` | `send`: 비스트리밍 호출 후 **`TaskEvent`** 기반으로 결과 문자열 수집(추가 타임아웃 없음). `sendStream`: SSE **`TaskUpdateEvent`** 를 누적하며 최대 **60초** 대기 |
| `LazyAgentCard` | AgentCard를 lazy 로딩·캐싱. `get()`은 null이면 재시도, `peek()`은 캐시만 반환 |
| `TextExtractor` | `Task` / `Message`에서 텍스트를 추출하는 유틸리티 |

### Auto-configurations

| 모듈 | 활성화 조건 | 설명 |
|------|-----------|------|
| `spring-ai-a2a-autoconfigure-agent-common` | 모듈이 클래스패스에 있을 때 | `spring.ai.a2a.remote.*` 바인딩(`RemoteAgentProperties`), `RemoteAgentCardRegistry` 빈 등록 (`GET /ping`은 샘플 애플리케이션의 `PingController`에서 제공) |
| `spring-ai-a2a-autoconfigure-server` | `ChatClient`가 클래스패스에 있고 `spring.ai.a2a.server.enabled=true`(기본값) | A2A 서버 인프라 자동 구성 (Virtual thread Executor, TaskStore, RequestHandler 등) |
| `spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core` | 구현체·AgentCore 클라이언트 클래스가 있고, `spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id`가 설정되며 AWS 자격 증명 빈이 있을 때 | `BedrockAgentCoreChatMemoryRepository` 등록. 조건 미충족 시 Spring AI `InMemoryChatMemoryRepository` 폴백 |

### Starter 모듈 (`spring-ai-a2a-starters/`)

여러 라이브러리 JAR을 **한 Gradle/Maven 의존성**으로 묶기 위한 모듈입니다. `java-library`와 `api` 전이를 사용해, 소비 앱이 하위 모듈의 타입을 컴파일 시점에 그대로 참조할 수 있습니다.

| Starter | 포함 모듈 | 이 레포 `samples/` |
|---------|-----------|---------------------|
| `spring-ai-a2a-starter-agent-common` | `spring-ai-a2a-agent-common`, `spring-ai-a2a-autoconfigure-agent-common` | 모든 샘플 |
| `spring-ai-a2a-starter-server` | `spring-ai-a2a-server`, `spring-ai-a2a-autoconfigure-server` | `order-agent`, `delivery-agent`, `payment-agent` |
| `spring-ai-a2a-starter-model-chat-memory-repository-bedrock-agent-core` | `spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core`, `spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core` | `host-agent` |

일부 조합만 필요하면 위 표의 **포함 모듈**을 개별 `implementation`으로 나누어 선언해도 됩니다.

### Memory 구현체

현재 Amazon Bedrock AgentCore 기반 구현체가 제공됩니다.

**`spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core`**

| 컴포넌트 | 설명 |
|---------|------|
| `BedrockAgentCoreChatMemoryRepository` | `ChatMemoryRepository` 및 `AdvancedBedrockAgentCoreChatMemoryRepository` 구현. Spring AI `conversationId`는 Bedrock AgentCore `sessionId`에 매핑되고, `actorId`는 설정 또는 오버로드로 전달 |
| `BedrockAgentCoreChatMemoryConfig` | `BedrockAgentCoreClient`, Memory Store `memoryId`, `actorId` 구성 |

```yaml
spring:
  ai:
    chat:
      memory:
        repository:
          bedrock:
            agent-core:
              memory:
                memory-id: ${BEDROCK_MEMORY_ID:}   # 비어 있으면 InMemory 폴백
                actor-id: ${BEDROCK_ACTOR_ID:spring-ai}
```

---

## 샘플 (`samples/`)

Amazon Bedrock AgentCore Runtime 위에서 동작하는 고객 지원 멀티 에이전트 오케스트레이션 예제입니다.

### 아키텍처

```
AgentCore Runtime
      │
      ▼ (POST /invocations)
┌─────────────────────────┐
│       host-agent        │  ← Spring AI ChatClient (Bedrock Converse)
│       (port: 8080)      │     RemoteAgentTools (@Tool)
└──┬──────────┬──────┬────┘
   │          │      │  (A2A JSON-RPC)
   ▼          ▼      ▼
Order       Delivery  Payment
Agent       Agent     Agent
(9001)      (9002)    (9003)
```

| 샘플 | 포트 | 설명 |
|------|------|------|
| `host-agent` | 8080 | AgentCore Runtime 진입점 · 오케스트레이터 |
| `order-agent` | 9001 | 주문 목록 조회 · 취소 가능 여부 확인 (delivery/payment 에이전트 연동); MCP Tools/Resources/Completions 제공 |
| `delivery-agent` | 9002 | 전체 배송 목록 조회 및 운송장번호 기반 배송 추적; MCP Tools/Resources/Completions 제공 |
| `payment-agent` | 9003 | 전체 결제 목록 조회 및 결제/환불 상태 확인; MCP Tools/Resources/Completions 제공 |

**host-agent 패키지 구조** (`io.github.cokelee777.agent.host` 기준)

- `invocation` — `POST /invocations` (`InvocationController`), orchestrator `ChatClient` 빈 (`InvocationConfiguration`), `InvocationService` / `DefaultInvocationService` 및 요청·응답 타입
- `remote` — 위임 DTO (`RemoteAgentDelegationRequest`), Spring AI `@Tool` (`RemoteAgentTools`); 다운스트림 URL은 `spring.ai.a2a.remote.agents` (`RemoteAgentProperties`, autoconfigure 모듈)
- 루트 — `HostAgentApplication` (부트스트랩)

### 전제 조건

- Java 25
- AWS 자격증명 (Bedrock 접근 권한 필요)
- Amazon Bedrock 모델 접근 활성화 (`ap-northeast-2` 기본값)

### 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BEDROCK_REGION` | `ap-northeast-2` | AWS 리전 |
| `BEDROCK_MODEL_ID` | — | Bedrock Converse 모델 ID |
| `ORDER_AGENT_URL` | — | 주문 에이전트 URL |
| `DELIVERY_AGENT_URL` | — | 배송 에이전트 URL |
| `PAYMENT_AGENT_URL` | — | 결제 에이전트 URL |
| `BEDROCK_MEMORY_ID` | — | Bedrock AgentCore Memory Store ID (`spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id`; 미설정·플레이스홀더면 InMemory 폴백) |
| `BEDROCK_ACTOR_ID` | (설정 기본값 `spring-ai`) | AgentCore Memory에서 사용하는 액터 ID (`memory.actor-id`) |
| `BEDROCK_MAX_TOKENS` | `8096` | Bedrock Converse `max-tokens` |

A2A 서버 blocking 타임아웃은 각 에이전트의 `application.yml`에서 오버라이드할 수 있습니다.

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `a2a.blocking.agent.timeout.seconds` | `30` | 에이전트 실행(LLM·툴 등) 완료 대기 최대 시간(초) |
| `a2a.blocking.consumption.timeout.seconds` | `5` | 이벤트 소비/영속화 완료 대기 최대 시간(초) |

### 로컬 실행

로컬에서는 에이전트마다 다른 포트를 사용합니다. `local` 프로파일을 활성화하면 `application-local.yml`의 포트/URL 설정이 적용됩니다.

| 에이전트 | 로컬 포트 | AgentCore 포트 |
|---------|---------|--------------|
| host-agent | 8080 | 8080 |
| order-agent | 9001 | 9000 |
| delivery-agent | 9002 | 9000 |
| payment-agent | 9003 | 9000 |

```bash
# 전체 빌드
./gradlew build

# 각 에이전트 실행 (별도 터미널, local 프로파일 활성화)
SPRING_PROFILES_ACTIVE=local ./gradlew :order-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :delivery-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :payment-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :host-agent:bootRun
```

> **IntelliJ 사용자**: Spring Boot 실행 설정의 **Active Profiles** 항목에 `local`을 입력하면 동일하게 적용됩니다.

### Docker 빌드

빌드 컨텍스트는 항상 **프로젝트 루트**입니다.

```bash
# 다운스트림 에이전트 (arm64)
docker buildx build --platform linux/arm64 \
  -f samples/order-agent/Dockerfile -t order-agent:latest --load .
docker buildx build --platform linux/arm64 \
  -f samples/delivery-agent/Dockerfile -t delivery-agent:latest --load .
docker buildx build --platform linux/arm64 \
  -f samples/payment-agent/Dockerfile -t payment-agent:latest --load .

# host-agent (AgentCore 배포 → ARM64 필수)
docker buildx build --platform linux/arm64 \
  -f samples/host-agent/Dockerfile -t host-agent:arm64 --load .
```

---

## 기술 스택

- **Java 25**, **Spring Boot 3.5.0**
- **Spring AI 1.1.3** — ChatClient, Tool Calling (`@Tool` / `@ToolParam`), Bedrock Converse, MCP Server (`spring-ai-starter-mcp-server-webmvc`)
- **A2A Java SDK 0.3.3.Final** — Agent-to-Agent 프로토콜
- **AWS SDK 2.42.9** — Amazon Bedrock, Bedrock AgentCore
- **Virtual thread** — A2A 에이전트 실행은 virtual thread 기반 `a2aTaskExecutor`에서 수행
