# Downstream Agents MCP Server Design

**Date:** 2026-04-02
**Approach:** Approach 1 — 각 에이전트 모듈에 직접 의존성 추가

---

## 1. 목표

order-agent, delivery-agent, payment-agent 세 downstream 에이전트를 MCP 서버로도 노출한다.
기존 A2A 서버는 그대로 유지하며, 동일 포트에서 A2A와 MCP를 함께 서빙한다.

---

## 2. 아키텍처 개요

```
order-agent   :9001
  ├── A2A  POST /                          (기존)
  ├── A2A  GET  /.well-known/agent-card.json  (기존)
  └── MCP  POST /mcp                       (신규, Streamable HTTP)

delivery-agent :9002  (동일 구조)
payment-agent  :9003  (동일 구조)
```

### Transport

- `spring-ai-starter-mcp-server-webmvc` 사용 (Spring AI 1.1.3 BOM 포함)
- `McpServerStreamableHttpWebMvcAutoConfiguration` 활성화
  → `WebMvcStreamableServerTransportProvider` 기반 Streamable HTTP (2025-03-26 MCP 스펙)
- `spring.ai.mcp.server.protocol=STREAMABLE` 로 SSE 기본값 오버라이드

### Tool 등록 방식

- `@McpTool` 어노테이션 방식 대신 **`ToolCallbackProvider` 빈 등록** 방식 사용
- 이유:
  - `*Tools` 클래스를 순수 Spring AI (`org.springframework.ai`) 의존성으로 유지
  - MCP 노출 여부는 Configuration 레벨에서 결정
  - Resources/Completions도 Configuration 빈으로 일관성 있게 등록

---

## 3. 각 에이전트 MCP 노출 기능

| 에이전트 | Tools | Resources | Completions |
|---|---|---|---|
| order-agent | `getOrderList`, `checkOrderCancellability` | `orders://list` | 주문번호 (`ORD-*`) |
| delivery-agent | `trackDelivery` | `deliveries://list` | 운송장번호 (`TRACK-*`) |
| payment-agent | `getPaymentStatus` | `payments://list` | 주문번호 (`ORD-*`) |

---

## 4. 변경 범위

### 4-1. 의존성 (각 에이전트 `build.gradle.kts`)

```kotlin
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
```

### 4-2. 설정 (각 에이전트 `application.yml`)

```yaml
spring:
  ai:
    mcp:
      server:
        name: <agent-name>   # order-agent / delivery-agent / payment-agent
        version: 1.0.0
        protocol: STREAMABLE
```

### 4-3. Repository 확장

`DeliveryRepository`, `PaymentRepository`에 `findAll()` 추가.
Resources/Completions 구현 시 전체 목록이 필요하기 때문이다.
`OrderRepository`는 이미 `findAll()`을 보유하므로 변경 불필요.

| 파일 | 변경 내용 |
|---|---|
| `DeliveryRepository` | `List<Delivery> findAll()` 메서드 추가 |
| `InMemoryDeliveryRepository` | `deliveryStore.values()` 반환 구현 추가 |
| `PaymentRepository` | `List<Payment> findAll()` 메서드 추가 |
| `InMemoryPaymentRepository` | `paymentStore.values()` 반환 구현 추가 |

### 4-4. MCP 전용 Configuration 클래스 신규 생성

`*AgentConfiguration`은 A2A 설정(AgentCard, ChatClient, AgentExecutor)만 담당한다.
MCP 관련 빈은 별도 `*McpConfiguration` 클래스로 분리한다.

- `*AgentConfiguration` — A2A 전용 (기존 파일 변경 없음)
- `*McpConfiguration` — MCP 전용 (신규 생성)

**DeliveryMcpConfiguration 예시:**
```java
@Configuration
public class DeliveryMcpConfiguration {

    @Bean
    public ToolCallbackProvider mcpTools(DeliveryTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> mcpResources(DeliveryRepository repo) {
        McpSchema.Resource resource = new McpSchema.Resource(
            "deliveries://list", "전체 배송 목록", "text/plain", null, null);
        return List.of(new McpServerFeatures.SyncResourceSpecification(resource,
            (exchange, req) -> new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(req.uri(), "text/plain",
                    repo.findAll().stream()
                        .map(Delivery::toStatusLine)
                        .collect(Collectors.joining("\n")))))));
    }

    @Bean
    public List<McpServerFeatures.SyncCompletionSpecification> mcpCompletions(DeliveryRepository repo) {
        return List.of(new McpServerFeatures.SyncCompletionSpecification(
            new McpSchema.CompleteReference("ref/resource", "deliveries://list"),
            (exchange, req) -> {
                String prefix = req.argument().value();
                List<String> candidates = repo.findAll().stream()
                    .map(Delivery::trackingNumber)
                    .filter(n -> n.startsWith(prefix))
                    .toList();
                return new McpSchema.CompleteResult(
                    new McpSchema.CompleteResult.CompleteResultValue(candidates, candidates.size(), false));
            }));
    }

}
```

order-agent(`OrderMcpConfiguration`), payment-agent(`PaymentMcpConfiguration`)도 동일한 패턴으로 구현한다.

---

## 5. 전체 변경 파일 목록

| 파일 | 변경 유형 |
|---|---|
| `samples/order-agent/build.gradle.kts` | 의존성 추가 |
| `samples/order-agent/src/main/resources/application.yml` | MCP 설정 추가 |
| `samples/order-agent/.../OrderMcpConfiguration.java` | 신규 생성 (Tools/Resources/Completions) |
| `samples/delivery-agent/build.gradle.kts` | 의존성 추가 |
| `samples/delivery-agent/src/main/resources/application.yml` | MCP 설정 추가 |
| `samples/delivery-agent/.../DeliveryMcpConfiguration.java` | 신규 생성 (Tools/Resources/Completions) |
| `samples/delivery-agent/.../DeliveryRepository.java` | `findAll()` 추가 |
| `samples/delivery-agent/.../InMemoryDeliveryRepository.java` | `findAll()` 구현 추가 |
| `samples/payment-agent/build.gradle.kts` | 의존성 추가 |
| `samples/payment-agent/src/main/resources/application.yml` | MCP 설정 추가 |
| `samples/payment-agent/.../PaymentMcpConfiguration.java` | 신규 생성 (Tools/Resources/Completions) |
| `samples/payment-agent/.../PaymentRepository.java` | `findAll()` 추가 |
| `samples/payment-agent/.../InMemoryPaymentRepository.java` | `findAll()` 구현 추가 |

---

## 6. 비변경 사항

- `*Tools.java` — `@Tool` 어노테이션 유지, `@McpTool` 추가 없음
- `*AgentConfiguration.java` — 변경 없음 (A2A 설정 전담 유지)
- `spring-ai-a2a-starter-server` — 변경 없음 (새 starter 모듈 미생성)
- A2A 서버 동작 — 완전 유지
- host-agent — 변경 없음
