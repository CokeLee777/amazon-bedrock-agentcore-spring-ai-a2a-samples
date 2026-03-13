# Amazon Bedrock AgentCore Spring Boot Samples

Amazon Bedrock AgentCore Runtime과 Spring AI를 활용한 A2A(Agent-to-Agent) 프로토콜 기반 멀티 에이전트 오케스트레이션 샘플 프로젝트입니다.

## 개요

이 프로젝트는 Amazon Bedrock AgentCore Runtime 위에서 동작하는 고객 지원 오케스트레이터를 구현합니다. 사용자의 주문/배송 문의를 LLM이 분석하여 적절한 다운스트림 A2A 에이전트를 Spring AI tool-calling으로 호출합니다.

## 아키텍처

```
AgentCore Runtime
      │
      ▼ (POST /invocations)
┌─────────────────────────┐
│       host-agent        │  ← Spring AI ChatClient (Bedrock Converse / Nova Pro)
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

| 모듈 | 포트   | 설명                                                  |
|------|------|-----------------------------------------------------|
| `a2a-common` | —    | A2A 클라이언트 `A2aTransport`, `TextExtractor` 등 공유 유틸리티 |
| `spring-ai-a2a-server` | —    | A2A 서버 구현체 (AgentCard, Message 컨트롤러, Task 컨트롤러)     |
| `spring-ai-a2a-server-autoconfigure` | —    | A2A 서버/공통 인프라 자동 구성                                 |
| `agents:host-agent` | 8080 | AgentCore Runtime 진입점 · 오케스트레이터                     |
| `agents:order-agent` | 9000 | 주문 조회 · 취소 가능 여부 확인 A2A 에이전트                        |
| `agents:delivery-agent` | 9000 | 배송 추적 A2A 에이전트                                      |
| `agents:payment-agent` | 9000 | 결제/환불 상태 확인 A2A 에이전트                                |

## 전제 조건

- Java 21
- AWS 자격증명 (Bedrock 접근 권한 필요)
- Amazon Nova Pro 모델 접근 활성화 (`ap-northeast-2` 기본값)

## 환경변수

| 변수 | 기본값                     | 설명 |
|------|-------------------------|------|
| `BEDROCK_REGION` | `ap-northeast-2`        | AWS 리전 |
| `BEDROCK_MODEL_ID` | `amazon.nova-pro-v1:0`  | Bedrock Converse 모델 ID |
| `ORDER_AGENT_URL` | agent별 기본값 | 주문 에이전트 URL |
| `DELIVERY_AGENT_URL` | agent별 기본값 | 배송 에이전트 URL |
| `PAYMENT_AGENT_URL` | agent별 기본값 | 결제 에이전트 URL |
| `A2A_CLIENT_TIMEOUT_SECONDS` | `15`                    | 다운스트림 에이전트 호출 타임아웃(초) |
| `AGENT_URL` | agent별 기본값              | 각 에이전트의 공개 베이스 URL (AgentCard.url) |
| `AGENT_PORT` | agent별 기본값              | 각 에이전트 리슨 포트 |

## 실행 방법

### 로컬 (Gradle)

```bash
# 전체 빌드
./gradlew build

# 각 에이전트 실행 (별도 터미널)
./gradlew :agents:order-agent:bootRun
./gradlew :agents:delivery-agent:bootRun
./gradlew :agents:payment-agent:bootRun
./gradlew :agents:host-agent:bootRun
```

### Docker

빌드 컨텍스트는 항상 **프로젝트 루트**입니다.

```bash
# 다운스트림 에이전트
docker buildx build --platform linux/amd64 \
  -f agents/order-agent/Dockerfile -t order-agent:latest .
docker buildx build --platform linux/amd64 \
  -f agents/delivery-agent/Dockerfile -t delivery-agent:latest .
docker buildx build --platform linux/amd64 \
  -f agents/payment-agent/Dockerfile -t payment-agent:latest .

# host-agent (AgentCore 배포 → ARM64 필수)
docker buildx build --platform linux/arm64 \
  -f agents/host-agent/Dockerfile -t host-agent:latest .
```

## 주요 기술 스택

- **Java 21**, **Spring Boot 3.5.0**
- **Spring AI 1.1.2** — ChatClient, Tool Calling (`@Tool` / `@ToolParam`), Bedrock Converse
- **Amazon Bedrock** (Amazon Nova Pro) — LLM 추론
- **A2A Java SDK 0.3.3.Final** (`io.github.a2asdk`) — Agent-to-Agent 프로토콜
- **AWS SDK 2.42.x** — **Bedrock** AgentCore 등
- **Amazon Bedrock AgentCore Runtime** — 세션 관리, 에이전트 엔트리포인트
