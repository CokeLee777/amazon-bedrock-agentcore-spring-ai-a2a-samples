# Spring AI A2A

Spring AI 기반 [A2A(Agent-to-Agent) 프로토콜](https://google.github.io/A2A/) 에이전트를 쉽게 구현할 수 있는 라이브러리입니다.
A2A 서버 인프라, 에이전트 공통 유틸리티, 대화 메모리 등을 Spring Boot Auto-configuration으로 제공하며,
`samples/` 디렉토리에 Amazon Bedrock AgentCore Runtime 기반 멀티 에이전트 오케스트레이션 예제가 포함되어 있습니다.

## 라이브러리 모듈

### spring-ai-a2a-server

A2A 프로토콜 서버 구현체입니다. Spring MVC 컨트롤러와 `DefaultAgentExecutor`를 제공합니다.

| 컴포넌트 | 설명 |
|---------|------|
| `AgentCardController` | `GET /.well-known/agent-card.json` — AgentCard 제공 |
| `MessageController` | A2A JSON-RPC 메시지 엔드포인트 |
| `TaskController` | A2A Task API 엔드포인트 |
| `DefaultAgentExecutor` | Task 생명주기 관리, `ChatClientExecutorHandler`에 실행 위임 |
| `ChatClientExecutorHandler` | 에이전트 로직을 정의하는 함수형 인터페이스 (`RequestContext → String`) |

### spring-ai-a2a-agent-common

A2A 클라이언트 측 공유 유틸리티입니다.

| 컴포넌트 | 설명 |
|---------|------|
| `A2ATransport` | 다운스트림 에이전트에 메시지를 전송하고 `TaskEvent` 응답을 동기 블로킹으로 수신 |
| `LazyAgentCard` | AgentCard를 lazy 로딩·캐싱. `get()`은 null이면 재시도, `peek()`은 캐시만 반환 |
| `TextExtractor` | `Task` / `Message`에서 텍스트를 추출하는 유틸리티 |

### Auto-configurations

| 모듈 | 활성화 조건 | 설명 |
|------|-----------|------|
| `spring-ai-a2a-autoconfigure-agent-common` | 항상 활성화 | `GET /ping` 헬스체크 엔드포인트 등록 |
| `spring-ai-a2a-autoconfigure-server` | `ChatClient` 클래스가 클래스패스에 존재 | A2A 서버 인프라 자동 구성 (Virtual thread Executor, TaskStore, RequestHandler 등) |
| `spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core` | `memory-id` 프로퍼티 설정 시 | `BedrockChatMemoryRepository` 등록. 미설정 시 Spring AI `InMemoryChatMemoryRepository` 폴백 |

### Memory 구현체

현재 Amazon Bedrock AgentCore 기반 구현체가 제공됩니다.

**`spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core`**

| 컴포넌트 | 설명 |
|---------|------|
| `BedrockChatMemoryRepository` | `ChatMemoryRepository` 구현체. `conversationId = "actorId:sessionId"` 복합키 사용 |
| `AgentCoreEventToMessageConverter` | AgentCore Event → Spring AI `Message` 변환 |

```yaml
spring:
  ai:
    chat:
      memory:
        repository:
          bedrock:
            agent-core:
              memory-id: ${BEDROCK_MEMORY_ID:}  # 미설정 시 InMemory 폴백
              max-turns: 10
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
| `order-agent` | 9001 | 주문 조회 · 취소 가능 여부 확인 (delivery/payment 에이전트 연동) |
| `delivery-agent` | 9002 | 운송장번호 기반 배송 추적 |
| `payment-agent` | 9003 | 결제/환불 상태 확인 |

**host-agent 패키지 구조** (`io.github.cokelee777.agent.host` 기준)

- `invocation` — `POST /invocations` (`InvocationController`), orchestrator `ChatClient` 빈 (`InvocationConfiguration`), `InvocationService` / `DefaultInvocationService` 및 요청·응답 타입
- `remote` — 위임 DTO (`RemoteAgentDelegationRequest`), Spring AI `@Tool` (`RemoteAgentTools`); 다운스트림 URL은 `a2a.remote.agents` (`RemoteAgentProperties`, autoconfigure 모듈)
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
| `BEDROCK_MEMORY_ID` | — | Bedrock AgentCore Memory 리소스 ID (미설정 시 InMemory 폴백) |

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
- **Spring AI 1.1.3** — ChatClient, Tool Calling (`@Tool` / `@ToolParam`), Bedrock Converse
- **A2A Java SDK 0.3.3.Final** — Agent-to-Agent 프로토콜
- **AWS SDK 2.42.9** — Amazon Bedrock, Bedrock AgentCore
- **Virtual thread** — A2A 에이전트 실행은 virtual thread 기반 `a2aTaskExecutor`에서 수행
