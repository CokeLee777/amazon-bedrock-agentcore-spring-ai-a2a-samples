# Amazon Bedrock AgentCore Spring AI A2A Samples

Amazon Bedrock AgentCore Runtime과 Spring AI를 활용한 A2A(Agent-to-Agent) 프로토콜 기반 멀티 에이전트 오케스트레이션 샘플 프로젝트입니다.

## 개요

이 프로젝트는 Amazon Bedrock AgentCore Runtime 위에서 동작하는 고객 지원 오케스트레이터를 구현합니다. 사용자의 주문/배송 문의를 LLM이 분석하여 적절한 다운스트림 A2A 에이전트를 Spring AI tool-calling으로 호출합니다.

## 아키텍처

```
AgentCore Runtime
      │
      ▼ (POST /invocations)
┌─────────────────────────┐
│       host-agent        │  ← Spring AI ChatClient (Bedrock Converse / Nova Lite)
│       (port: 8080)      │     RemoteAgentConnections (@Tool)
└──┬──────────┬──────┬────┘
   │          │      │  (A2A JSON-RPC)
   ▼          ▼      ▼
Order       Delivery  Payment
Agent       Agent     Agent
(9000)      (9000)    (9000)
```

오케스트레이터의 AgentCard에는 다음 스킬이 등록되어 있습니다.

| 스킬 ID | 설명 |
|--------|------|
| `order_query` | 주문 목록 조회 |
| `order_cancellability` | 주문 취소 가능 여부 판단 |
| `delivery_tracking` | 운송장번호 기반 배송 추적 |

## 모듈 구조

| 모듈 | 포트   | 설명                                                                          |
|------|------|-----------------------------------------------------------------------------|
| `agent-common` | —    | `A2ATransport`, `LazyAgentCard`, `TextExtractor` 등 공유 유틸리티                  |
| `spring-ai-a2a-server` | —    | A2A 서버 구현체 (AgentCard, Message 컨트롤러, Task 컨트롤러)                             |
| `spring-ai-a2a-server-autoconfigure` | —    | A2A 서버/공통 인프라 자동 구성                                                        |
| `agents:host-agent` | 8080 | AgentCore Runtime 진입점 · 오케스트레이터                                             |
| `agents:order-agent` | 9001 | 주문 조회 · 취소 가능 여부 확인 A2A 에이전트 (delivery/payment 에이전트 호출 포함)                  |
| `agents:delivery-agent` | 9002 | 배송 추적 A2A 에이전트                                                              |
| `agents:payment-agent` | 9003 | 결제/환불 상태 확인 A2A 에이전트                                                        |

## 전제 조건

- Java 25
- AWS 자격증명 (Bedrock 접근 권한 필요)
- Amazon Nova Pro 모델 접근 활성화 (`ap-northeast-2` 기본값)

## 환경변수

| 변수 | 기본값                    | 설명 |
|------|------------------------|------|
| `BEDROCK_REGION` | `ap-northeast-2`       | AWS 리전 |
| `BEDROCK_MODEL_ID` | `amazon.nova-lite-v1:0` | Bedrock Converse 모델 ID |
| `ORDER_AGENT_URL` | agent별 기본값             | 주문 에이전트 URL |
| `DELIVERY_AGENT_URL` | agent별 기본값             | 배송 에이전트 URL |
| `PAYMENT_AGENT_URL` | agent별 기본값             | 결제 에이전트 URL |
| `A2A_CLIENT_TIMEOUT_SECONDS` | `15`                   | 다운스트림 에이전트 호출 타임아웃(초) |
| `AGENT_URL` | agent별 기본값             | 각 에이전트의 공개 베이스 URL (AgentCard.url) |
| `AGENT_PORT` | agent별 기본값             | 각 에이전트 리슨 포트 |
| `BEDROCK_MEMORY_MODE` | `none`                 | 메모리 사용 모드 (`none` / `short_term` / `long_term` / `both`) |
| `BEDROCK_MEMORY_ID` | —                      | Bedrock AgentCore Memory 리소스 ID (mode가 `none`이 아닐 때 필수) |
| `BEDROCK_MEMORY_STRATEGY_ID` | —                      | Memory Strategy ID (mode가 `long_term` / `both`일 때 필수) |

A2A 서버의 blocking 타임아웃은 `application.yml`에서 설정할 수 있으며, autoconfigure 기본값을 앱 설정으로 오버라이드할 수 있다.

| 설정 키 (application.yml) | 기본값 | 설명 |
|---------------------------|--------|------|
| `a2a.blocking.agent.timeout.seconds` | `30` | 에이전트 실행(LLM·툴 등) 완료 대기 최대 시간(초) |
| `a2a.blocking.consumption.timeout.seconds` | `5` | 이벤트 소비/영속화 완료 대기 최대 시간(초) |
| `aws.bedrock.agent-core.memory.mode` | `none` | 메모리 사용 모드 |
| `aws.bedrock.agent-core.memory.memory-id` | — | Memory 리소스 ID |
| `aws.bedrock.agent-core.memory.strategy-id` | — | Memory Strategy ID |
| `aws.bedrock.agent-core.memory.short-term-max-turns` | `10` | 단기 기억 최대 대화 턴 수 |
| `aws.bedrock.agent-core.memory.long-term-max-results` | `4` | 장기 기억 최대 검색 결과 수 |

## 실행 방법

### 로컬 (Gradle)

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
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:order-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:delivery-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:payment-agent:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :agents:host-agent:bootRun
```

> **IntelliJ 사용자**: Spring Boot 실행 설정의 **Active Profiles** 항목에 `local`을 입력하면 동일하게 적용됩니다.

### Docker

빌드 컨텍스트는 항상 **프로젝트 루트**입니다.

```bash
# 다운스트림 에이전트
docker buildx build --platform linux/arm64 \
  -f agents/order-agent/Dockerfile -t order-agent:latest .
docker buildx build --platform linux/arm64 \
  -f agents/delivery-agent/Dockerfile -t delivery-agent:latest .
docker buildx build --platform linux/arm64 \
  -f agents/payment-agent/Dockerfile -t payment-agent:latest .

# host-agent (AgentCore 배포 → ARM64 필수)
docker buildx build --platform linux/arm64 \
  -f agents/host-agent/Dockerfile -t host-agent:latest .
```

## 주요 기술 스택

- **Java 25**, **Spring Boot 3.5.0**
- **Spring AI 1.1.2** — ChatClient, Tool Calling (`@Tool` / `@ToolParam`), Bedrock Converse
- **Amazon Bedrock** (Amazon Nova Pro) — LLM 추론
- **A2A Java SDK 0.3.3.Final** (`io.github.a2asdk`) — Agent-to-Agent 프로토콜
- **AWS SDK 2.42.x** — **Bedrock** AgentCore 등
- **Amazon Bedrock AgentCore Runtime** — 세션 관리, 에이전트 엔트리포인트
- **Virtual thread** — A2A 에이전트 실행은 `a2aTaskExecutor`(virtual thread)에서 수행되며, 동시 요청이 많을 때 스레드 풀 제한 없이 확장된다.
