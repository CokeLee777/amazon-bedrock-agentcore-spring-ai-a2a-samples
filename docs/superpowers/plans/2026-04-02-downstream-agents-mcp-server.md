# Downstream Agents MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** order-agent, delivery-agent, payment-agent에 MCP Streamable HTTP 서버를 추가해 Tools/Resources/Completions를 노출한다.

**Architecture:** 각 에이전트의 `build.gradle.kts`에 `spring-ai-starter-mcp-server-webmvc` 의존성을 추가하고, `application.yml`에 MCP 설정을 추가한다. MCP 빈(Tools/Resources/Completions)은 기존 `*AgentConfiguration`과 분리된 `*McpConfiguration` 클래스에 정의한다. DeliveryRepository와 PaymentRepository에 `findAll()`을 추가해 Resources/Completions 구현을 가능하게 한다.

**Tech Stack:** Spring AI 1.1.3, `io.modelcontextprotocol.sdk:mcp-core:0.17.0` (transitive), `spring-ai-starter-mcp-server-webmvc`, JUnit 5, AssertJ

---

## File Map

**신규 생성:**
- `samples/order-agent/src/main/java/io/github/cokelee777/agent/order/OrderMcpConfiguration.java`
- `samples/order-agent/src/test/java/io/github/cokelee777/agent/order/OrderMcpConfigurationTest.java`
- `samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/DeliveryMcpConfiguration.java`
- `samples/delivery-agent/src/test/java/io/github/cokelee777/agent/delivery/DeliveryMcpConfigurationTest.java`
- `samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/PaymentMcpConfiguration.java`
- `samples/payment-agent/src/test/java/io/github/cokelee777/agent/payment/PaymentMcpConfigurationTest.java`

**수정:**
- `samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/repository/DeliveryRepository.java`
- `samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/repository/InMemoryDeliveryRepository.java`
- `samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/repository/PaymentRepository.java`
- `samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/repository/InMemoryPaymentRepository.java`
- `samples/order-agent/build.gradle.kts`
- `samples/delivery-agent/build.gradle.kts`
- `samples/payment-agent/build.gradle.kts`
- `samples/order-agent/src/main/resources/application.yml`
- `samples/delivery-agent/src/main/resources/application.yml`
- `samples/payment-agent/src/main/resources/application.yml`

---

## Task 1: DeliveryRepository findAll() 추가

**Files:**
- Modify: `samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/repository/DeliveryRepository.java`
- Modify: `samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/repository/InMemoryDeliveryRepository.java`
- Test: `samples/delivery-agent/src/test/java/io/github/cokelee777/agent/delivery/DeliveryToolsTest.java` (기존 파일에 테스트 추가)

- [ ] **Step 1: 기존 테스트 파일에 findAll 테스트 추가**

`samples/delivery-agent/src/test/java/io/github/cokelee777/agent/delivery/DeliveryToolsTest.java` 파일 하단 `}` 앞에 다음 테스트를 추가한다.

```java
@Test
void findAll_returnsFiveSeededDeliveries() {
    InMemoryDeliveryRepository repo = new InMemoryDeliveryRepository();
    assertThat(repo.findAll()).hasSize(5);
}

@Test
void findAll_containsTrack1001AndTrack2008() {
    InMemoryDeliveryRepository repo = new InMemoryDeliveryRepository();
    List<String> trackingNumbers = repo.findAll()
        .stream()
        .map(io.github.cokelee777.agent.delivery.domain.Delivery::trackingNumber)
        .toList();
    assertThat(trackingNumbers).contains("TRACK-1001", "TRACK-2008");
}
```

파일 상단 import에 `import java.util.List;` 추가.

- [ ] **Step 2: 테스트 실행 — 컴파일 오류 확인**

```bash
./gradlew :samples:delivery-agent:test --tests "io.github.cokelee777.agent.delivery.DeliveryToolsTest.findAll_returnsFiveSeededDeliveries" 2>&1 | tail -20
```

Expected: `findAll()` 메서드가 없어서 컴파일 오류 발생.

- [ ] **Step 3: DeliveryRepository 인터페이스에 findAll() 추가**

`samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/repository/DeliveryRepository.java`:

```java
package io.github.cokelee777.agent.delivery.repository;

import io.github.cokelee777.agent.delivery.domain.Delivery;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to seeded shipments for the sample delivery agent.
 */
public interface DeliveryRepository {

	/**
	 * Returns all seeded shipments in stable insertion order.
	 * @return every seeded delivery
	 */
	List<Delivery> findAll();

	/**
	 * Resolves a shipment by tracking reference; accepts bare ids or free text containing
	 * a known id.
	 * @param trackingNumber user- or LLM-provided value, possibly null or blank
	 * @return the shipment, if any
	 */
	Optional<Delivery> findByTrackingNumber(String trackingNumber);

}
```

- [ ] **Step 4: InMemoryDeliveryRepository에 findAll() 구현 추가**

`samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/repository/InMemoryDeliveryRepository.java` — `findByTrackingNumber` 메서드 위에 추가:

```java
@Override
public List<Delivery> findAll() {
    return List.copyOf(deliveryStore.values());
}
```

파일 상단에 `import java.util.List;` 추가 (이미 없는 경우).

- [ ] **Step 5: 테스트 실행 — 통과 확인**

```bash
./gradlew :samples:delivery-agent:test --tests "io.github.cokelee777.agent.delivery.DeliveryToolsTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 전체 테스트 통과.

---

## Task 2: PaymentRepository findAll() 추가

**Files:**
- Modify: `samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/repository/PaymentRepository.java`
- Modify: `samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/repository/InMemoryPaymentRepository.java`
- Test: `samples/payment-agent/src/test/java/io/github/cokelee777/agent/payment/PaymentToolsTest.java` (기존 파일에 테스트 추가)

- [ ] **Step 1: 기존 테스트 파일에 findAll 테스트 추가**

`samples/payment-agent/src/test/java/io/github/cokelee777/agent/payment/PaymentToolsTest.java` 파일 하단 `}` 앞에 추가:

```java
@Test
void findAll_returnsTenSeededPayments() {
    InMemoryPaymentRepository repo = new InMemoryPaymentRepository();
    assertThat(repo.findAll()).hasSize(10);
}

@Test
void findAll_containsOrd1001AndOrd1009() {
    InMemoryPaymentRepository repo = new InMemoryPaymentRepository();
    List<String> orderNumbers = repo.findAll()
        .stream()
        .map(io.github.cokelee777.agent.payment.domain.Payment::orderNumber)
        .toList();
    assertThat(orderNumbers).contains("ORD-1001", "ORD-1009");
}
```

파일 상단에 필요한 import 추가:
```java
import io.github.cokelee777.agent.payment.repository.InMemoryPaymentRepository;
import java.util.List;
```

- [ ] **Step 2: 테스트 실행 — 컴파일 오류 확인**

```bash
./gradlew :samples:payment-agent:test --tests "io.github.cokelee777.agent.payment.PaymentToolsTest.findAll_returnsTenSeededPayments" 2>&1 | tail -20
```

Expected: 컴파일 오류 (`findAll()` 없음).

- [ ] **Step 3: PaymentRepository 인터페이스에 findAll() 추가**

`samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/repository/PaymentRepository.java`:

```java
package io.github.cokelee777.agent.payment.repository;

import io.github.cokelee777.agent.payment.domain.Payment;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to seeded payments for the sample payment agent.
 */
public interface PaymentRepository {

	/**
	 * Returns all seeded payments in stable insertion order.
	 * @return every seeded payment
	 */
	List<Payment> findAll();

	/**
	 * Resolves a payment by order reference; accepts bare ids or free text containing a
	 * known id.
	 * @param orderNumber user- or LLM-provided value, possibly null or blank
	 * @return the payment row, if any
	 */
	Optional<Payment> findByOrderNumber(String orderNumber);

}
```

- [ ] **Step 4: InMemoryPaymentRepository에 findAll() 구현 추가**

`samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/repository/InMemoryPaymentRepository.java` — `findByOrderNumber` 메서드 위에 추가:

```java
@Override
public List<Payment> findAll() {
    return List.copyOf(paymentStore.values());
}
```

파일 상단에 `import java.util.List;` 추가 (이미 없는 경우).

- [ ] **Step 5: 테스트 실행 — 통과 확인**

```bash
./gradlew :samples:payment-agent:test --tests "io.github.cokelee777.agent.payment.PaymentToolsTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 3: order-agent MCP 서버 설정

**Files:**
- Modify: `samples/order-agent/build.gradle.kts`
- Modify: `samples/order-agent/src/main/resources/application.yml`
- Create: `samples/order-agent/src/main/java/io/github/cokelee777/agent/order/OrderMcpConfiguration.java`
- Create: `samples/order-agent/src/test/java/io/github/cokelee777/agent/order/OrderMcpConfigurationTest.java`

- [ ] **Step 1: 테스트 파일 생성**

`samples/order-agent/src/test/java/io/github/cokelee777/agent/order/OrderMcpConfigurationTest.java`:

```java
package io.github.cokelee777.agent.order;

import io.github.cokelee777.agent.order.repository.InMemoryOrderRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMcpConfigurationTest {

    private final InMemoryOrderRepository repo = new InMemoryOrderRepository();

    private final OrderMcpConfiguration config = new OrderMcpConfiguration();

    @Test
    void mcpResources_returnsOneResourceSpec() {
        List<McpServerFeatures.SyncResourceSpecification> specs = config.mcpResources(repo);
        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).resource().uri()).isEqualTo("orders://list");
    }

    @Test
    void mcpResources_readHandler_returnsAllOrders() {
        List<McpServerFeatures.SyncResourceSpecification> specs = config.mcpResources(repo);
        McpSchema.ReadResourceResult result = specs.get(0)
            .readHandler()
            .apply(null, new McpSchema.ReadResourceRequest("orders://list"));
        String text = ((McpSchema.TextResourceContents) result.contents().get(0)).text();
        assertThat(text).contains("ORD-1001").contains("ORD-1009");
    }

    @Test
    void mcpCompletions_returnsOneCompletionSpec() {
        List<McpServerFeatures.SyncCompletionSpecification> specs = config.mcpCompletions(repo);
        assertThat(specs).hasSize(1);
    }

    @Test
    void mcpCompletions_handler_filtersbyPrefix() {
        List<McpServerFeatures.SyncCompletionSpecification> specs = config.mcpCompletions(repo);
        McpSchema.CompleteResult result = specs.get(0)
            .completionHandler()
            .apply(null, new McpSchema.CompleteRequest(
                null,
                new McpSchema.CompleteRequest.CompleteArgument("orderNumber", "ORD-100")));
        assertThat(result.completion().values()).contains("ORD-1001", "ORD-1002", "ORD-1003");
        assertThat(result.completion().values()).doesNotContain("ORD-1010");
    }

}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 오류 확인**

```bash
./gradlew :samples:order-agent:test --tests "io.github.cokelee777.agent.order.OrderMcpConfigurationTest" 2>&1 | tail -20
```

Expected: `OrderMcpConfiguration` 클래스가 없어서 컴파일 오류.

- [ ] **Step 3: build.gradle.kts 의존성 추가**

`samples/order-agent/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":spring-ai-a2a-starter-agent-common"))
    implementation(project(":spring-ai-a2a-starter-server"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse")
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
}
```

- [ ] **Step 4: application.yml에 MCP 설정 추가**

`samples/order-agent/src/main/resources/application.yml` — 기존 내용 뒤에 추가:

```yaml
server:
  port: 9000

spring:
  ai:
    bedrock:
      aws:
        region: ${BEDROCK_REGION:ap-northeast-2}
      converse:
        chat:
          options:
            model: ${BEDROCK_MODEL_ID:}
    a2a:
      server:
        enabled: true
    mcp:
      server:
        name: order-agent
        version: 1.0.0
        protocol: STREAMABLE

a2a:
  agent-url: ${AGENT_URL:}
  remote:
    agents:
      delivery-agent:
        url: ${DELIVERY_AGENT_URL:}
      payment-agent:
        url: ${PAYMENT_AGENT_URL:}
```

- [ ] **Step 5: OrderMcpConfiguration 클래스 생성**

`samples/order-agent/src/main/java/io/github/cokelee777/agent/order/OrderMcpConfiguration.java`:

```java
package io.github.cokelee777.agent.order;

import io.github.cokelee777.agent.order.domain.Order;
import io.github.cokelee777.agent.order.repository.OrderRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP server beans for the Order Agent.
 *
 * <p>
 * Exposes {@link OrderTools} as MCP Tools, {@code orders://list} as an MCP Resource, and
 * order-number prefix matching as an MCP Completion. Kept separate from
 * {@link OrderAgentConfiguration} so A2A and MCP concerns do not mix.
 * </p>
 */
@Configuration
public class OrderMcpConfiguration {

	/**
	 * Registers {@link OrderTools} methods as MCP tools via {@link MethodToolCallbackProvider}.
	 * @param tools the order tool bean
	 * @return provider that {@code ToolCallbackConverterAutoConfiguration} converts to MCP
	 * tool specs
	 */
	@Bean
	public ToolCallbackProvider mcpTools(OrderTools tools) {
		return MethodToolCallbackProvider.builder().toolObjects(tools).build();
	}

	/**
	 * Exposes the full order list as an MCP resource at {@code orders://list}.
	 * @param repo order repository
	 * @return single-element list consumed by {@code McpServerAutoConfiguration}
	 */
	@Bean
	public List<McpServerFeatures.SyncResourceSpecification> mcpResources(OrderRepository repo) {
		McpSchema.Resource resource = McpSchema.Resource.builder()
			.uri("orders://list")
			.name("전체 주문 목록")
			.description("현재 회원의 모든 주문 내역")
			.mimeType("text/plain")
			.build();
		return List.of(new McpServerFeatures.SyncResourceSpecification(resource,
				(exchange, req) -> new McpSchema.ReadResourceResult(
						List.of(new McpSchema.TextResourceContents(req.uri(), "text/plain",
								repo.findAll().stream().map(Order::toListLine).collect(Collectors.joining("\n")))))));
	}

	/**
	 * Provides order-number prefix completion for {@code orders://list}.
	 * @param repo order repository
	 * @return single-element list consumed by {@code McpServerAutoConfiguration}
	 */
	@Bean
	public List<McpServerFeatures.SyncCompletionSpecification> mcpCompletions(OrderRepository repo) {
		return List.of(new McpServerFeatures.SyncCompletionSpecification(
				new McpSchema.ResourceReference("orders://list"), (exchange, req) -> {
					String prefix = req.argument().value();
					List<String> candidates = repo.findAll()
						.stream()
						.map(Order::orderNumber)
						.filter(n -> n.startsWith(prefix))
						.toList();
					return new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(candidates, candidates.size(), false));
				}));
	}

}
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

```bash
./gradlew :samples:order-agent:test --tests "io.github.cokelee777.agent.order.OrderMcpConfigurationTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 4개 테스트 모두 통과.

- [ ] **Step 7: 전체 order-agent 테스트 확인**

```bash
./gradlew :samples:order-agent:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 4: delivery-agent MCP 서버 설정

**Files:**
- Modify: `samples/delivery-agent/build.gradle.kts`
- Modify: `samples/delivery-agent/src/main/resources/application.yml`
- Create: `samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/DeliveryMcpConfiguration.java`
- Create: `samples/delivery-agent/src/test/java/io/github/cokelee777/agent/delivery/DeliveryMcpConfigurationTest.java`

- [ ] **Step 1: 테스트 파일 생성**

`samples/delivery-agent/src/test/java/io/github/cokelee777/agent/delivery/DeliveryMcpConfigurationTest.java`:

```java
package io.github.cokelee777.agent.delivery;

import io.github.cokelee777.agent.delivery.repository.InMemoryDeliveryRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryMcpConfigurationTest {

    private final InMemoryDeliveryRepository repo = new InMemoryDeliveryRepository();

    private final DeliveryMcpConfiguration config = new DeliveryMcpConfiguration();

    @Test
    void mcpResources_returnsOneResourceSpec() {
        List<McpServerFeatures.SyncResourceSpecification> specs = config.mcpResources(repo);
        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).resource().uri()).isEqualTo("deliveries://list");
    }

    @Test
    void mcpResources_readHandler_returnsAllDeliveries() {
        List<McpServerFeatures.SyncResourceSpecification> specs = config.mcpResources(repo);
        McpSchema.ReadResourceResult result = specs.get(0)
            .readHandler()
            .apply(null, new McpSchema.ReadResourceRequest("deliveries://list"));
        String text = ((McpSchema.TextResourceContents) result.contents().get(0)).text();
        assertThat(text).contains("TRACK-1001").contains("TRACK-2008");
    }

    @Test
    void mcpCompletions_returnsOneCompletionSpec() {
        List<McpServerFeatures.SyncCompletionSpecification> specs = config.mcpCompletions(repo);
        assertThat(specs).hasSize(1);
    }

    @Test
    void mcpCompletions_handler_filtersByPrefix() {
        List<McpServerFeatures.SyncCompletionSpecification> specs = config.mcpCompletions(repo);
        McpSchema.CompleteResult result = specs.get(0)
            .completionHandler()
            .apply(null, new McpSchema.CompleteRequest(
                null,
                new McpSchema.CompleteRequest.CompleteArgument("trackingNumber", "TRACK-1")));
        assertThat(result.completion().values()).contains("TRACK-1001", "TRACK-1002", "TRACK-1003");
        assertThat(result.completion().values()).doesNotContain("TRACK-2007");
    }

}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 오류 확인**

```bash
./gradlew :samples:delivery-agent:test --tests "io.github.cokelee777.agent.delivery.DeliveryMcpConfigurationTest" 2>&1 | tail -20
```

Expected: `DeliveryMcpConfiguration` 없어서 컴파일 오류.

- [ ] **Step 3: build.gradle.kts 의존성 추가**

`samples/delivery-agent/build.gradle.kts` 에 `spring-ai-starter-mcp-server-webmvc` 추가 (기존 의존성 유지):

```kotlin
dependencies {
    implementation(project(":spring-ai-a2a-starter-agent-common"))
    implementation(project(":spring-ai-a2a-starter-server"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
}
```

- [ ] **Step 4: application.yml에 MCP 설정 추가**

`samples/delivery-agent/src/main/resources/application.yml`:

```yaml
server:
  port: 9000

spring:
  ai:
    bedrock:
      aws:
        region: ${BEDROCK_REGION:ap-northeast-2}
      converse:
        chat:
          options:
            model: ${BEDROCK_MODEL_ID:}
    a2a:
      server:
        enabled: true
    mcp:
      server:
        name: delivery-agent
        version: 1.0.0
        protocol: STREAMABLE

a2a:
  agent-url: ${AGENT_URL:}
```

- [ ] **Step 5: DeliveryMcpConfiguration 클래스 생성**

`samples/delivery-agent/src/main/java/io/github/cokelee777/agent/delivery/DeliveryMcpConfiguration.java`:

```java
package io.github.cokelee777.agent.delivery;

import io.github.cokelee777.agent.delivery.domain.Delivery;
import io.github.cokelee777.agent.delivery.repository.DeliveryRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP server beans for the Delivery Agent.
 *
 * <p>
 * Exposes {@link DeliveryTools} as MCP Tools, {@code deliveries://list} as an MCP
 * Resource, and tracking-number prefix matching as an MCP Completion. Kept separate from
 * {@link DeliveryAgentConfiguration} so A2A and MCP concerns do not mix.
 * </p>
 */
@Configuration
public class DeliveryMcpConfiguration {

	/**
	 * Registers {@link DeliveryTools} methods as MCP tools via
	 * {@link MethodToolCallbackProvider}.
	 * @param tools the delivery tool bean
	 * @return provider that {@code ToolCallbackConverterAutoConfiguration} converts to MCP
	 * tool specs
	 */
	@Bean
	public ToolCallbackProvider mcpTools(DeliveryTools tools) {
		return MethodToolCallbackProvider.builder().toolObjects(tools).build();
	}

	/**
	 * Exposes the full delivery list as an MCP resource at {@code deliveries://list}.
	 * @param repo delivery repository
	 * @return single-element list consumed by {@code McpServerAutoConfiguration}
	 */
	@Bean
	public List<McpServerFeatures.SyncResourceSpecification> mcpResources(DeliveryRepository repo) {
		McpSchema.Resource resource = McpSchema.Resource.builder()
			.uri("deliveries://list")
			.name("전체 배송 목록")
			.description("운송장번호별 현재 배송 상태 전체 목록")
			.mimeType("text/plain")
			.build();
		return List.of(new McpServerFeatures.SyncResourceSpecification(resource,
				(exchange, req) -> new McpSchema.ReadResourceResult(
						List.of(new McpSchema.TextResourceContents(req.uri(), "text/plain",
								repo.findAll()
									.stream()
									.map(Delivery::toStatusLine)
									.collect(Collectors.joining("\n")))))));
	}

	/**
	 * Provides tracking-number prefix completion for {@code deliveries://list}.
	 * @param repo delivery repository
	 * @return single-element list consumed by {@code McpServerAutoConfiguration}
	 */
	@Bean
	public List<McpServerFeatures.SyncCompletionSpecification> mcpCompletions(DeliveryRepository repo) {
		return List.of(new McpServerFeatures.SyncCompletionSpecification(
				new McpSchema.ResourceReference("deliveries://list"), (exchange, req) -> {
					String prefix = req.argument().value();
					List<String> candidates = repo.findAll()
						.stream()
						.map(Delivery::trackingNumber)
						.filter(n -> n.startsWith(prefix))
						.toList();
					return new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(candidates, candidates.size(), false));
				}));
	}

}
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

```bash
./gradlew :samples:delivery-agent:test --tests "io.github.cokelee777.agent.delivery.DeliveryMcpConfigurationTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 4개 테스트 모두 통과.

- [ ] **Step 7: 전체 delivery-agent 테스트 확인**

```bash
./gradlew :samples:delivery-agent:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 5: payment-agent MCP 서버 설정

**Files:**
- Modify: `samples/payment-agent/build.gradle.kts`
- Modify: `samples/payment-agent/src/main/resources/application.yml`
- Create: `samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/PaymentMcpConfiguration.java`
- Create: `samples/payment-agent/src/test/java/io/github/cokelee777/agent/payment/PaymentMcpConfigurationTest.java`

- [ ] **Step 1: 테스트 파일 생성**

`samples/payment-agent/src/test/java/io/github/cokelee777/agent/payment/PaymentMcpConfigurationTest.java`:

```java
package io.github.cokelee777.agent.payment;

import io.github.cokelee777.agent.payment.repository.InMemoryPaymentRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMcpConfigurationTest {

    private final InMemoryPaymentRepository repo = new InMemoryPaymentRepository();

    private final PaymentMcpConfiguration config = new PaymentMcpConfiguration();

    @Test
    void mcpResources_returnsOneResourceSpec() {
        List<McpServerFeatures.SyncResourceSpecification> specs = config.mcpResources(repo);
        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).resource().uri()).isEqualTo("payments://list");
    }

    @Test
    void mcpResources_readHandler_returnsAllPayments() {
        List<McpServerFeatures.SyncResourceSpecification> specs = config.mcpResources(repo);
        McpSchema.ReadResourceResult result = specs.get(0)
            .readHandler()
            .apply(null, new McpSchema.ReadResourceRequest("payments://list"));
        String text = ((McpSchema.TextResourceContents) result.contents().get(0)).text();
        assertThat(text).contains("ORD-1001").contains("ORD-1009");
    }

    @Test
    void mcpCompletions_returnsOneCompletionSpec() {
        List<McpServerFeatures.SyncCompletionSpecification> specs = config.mcpCompletions(repo);
        assertThat(specs).hasSize(1);
    }

    @Test
    void mcpCompletions_handler_filtersByPrefix() {
        List<McpServerFeatures.SyncCompletionSpecification> specs = config.mcpCompletions(repo);
        McpSchema.CompleteResult result = specs.get(0)
            .completionHandler()
            .apply(null, new McpSchema.CompleteRequest(
                null,
                new McpSchema.CompleteRequest.CompleteArgument("orderNumber", "ORD-100")));
        assertThat(result.completion().values()).contains("ORD-1001", "ORD-1002", "ORD-1003");
        assertThat(result.completion().values()).doesNotContain("ORD-1010");
    }

}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 오류 확인**

```bash
./gradlew :samples:payment-agent:test --tests "io.github.cokelee777.agent.payment.PaymentMcpConfigurationTest" 2>&1 | tail -20
```

Expected: `PaymentMcpConfiguration` 없어서 컴파일 오류.

- [ ] **Step 3: build.gradle.kts 의존성 추가**

`samples/payment-agent/build.gradle.kts` 전체 내용:

```kotlin
dependencies {
    implementation(project(":spring-ai-a2a-starter-agent-common"))
    implementation(project(":spring-ai-a2a-starter-server"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse")
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
}
```

- [ ] **Step 4: application.yml에 MCP 설정 추가**

`samples/payment-agent/src/main/resources/application.yml` 전체 내용:

```yaml
server:
  port: 9000

spring:
  ai:
    bedrock:
      aws:
        region: ${BEDROCK_REGION:ap-northeast-2}
      converse:
        chat:
          options:
            model: ${BEDROCK_MODEL_ID:}
    a2a:
      server:
        enabled: true
    mcp:
      server:
        name: payment-agent
        version: 1.0.0
        protocol: STREAMABLE

a2a:
  agent-url: ${AGENT_URL:}
```

- [ ] **Step 5: PaymentMcpConfiguration 클래스 생성**

`samples/payment-agent/src/main/java/io/github/cokelee777/agent/payment/PaymentMcpConfiguration.java`:

```java
package io.github.cokelee777.agent.payment;

import io.github.cokelee777.agent.payment.domain.Payment;
import io.github.cokelee777.agent.payment.repository.PaymentRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP server beans for the Payment Agent.
 *
 * <p>
 * Exposes {@link PaymentTools} as MCP Tools, {@code payments://list} as an MCP Resource,
 * and order-number prefix matching as an MCP Completion. Kept separate from
 * {@link PaymentAgentConfiguration} so A2A and MCP concerns do not mix.
 * </p>
 */
@Configuration
public class PaymentMcpConfiguration {

	/**
	 * Registers {@link PaymentTools} methods as MCP tools via
	 * {@link MethodToolCallbackProvider}.
	 * @param tools the payment tool bean
	 * @return provider that {@code ToolCallbackConverterAutoConfiguration} converts to MCP
	 * tool specs
	 */
	@Bean
	public ToolCallbackProvider mcpTools(PaymentTools tools) {
		return MethodToolCallbackProvider.builder().toolObjects(tools).build();
	}

	/**
	 * Exposes the full payment list as an MCP resource at {@code payments://list}.
	 * @param repo payment repository
	 * @return single-element list consumed by {@code McpServerAutoConfiguration}
	 */
	@Bean
	public List<McpServerFeatures.SyncResourceSpecification> mcpResources(PaymentRepository repo) {
		McpSchema.Resource resource = McpSchema.Resource.builder()
			.uri("payments://list")
			.name("전체 결제 목록")
			.description("주문번호별 결제 및 환불 상태 전체 목록")
			.mimeType("text/plain")
			.build();
		return List.of(new McpServerFeatures.SyncResourceSpecification(resource,
				(exchange, req) -> new McpSchema.ReadResourceResult(
						List.of(new McpSchema.TextResourceContents(req.uri(), "text/plain",
								repo.findAll()
									.stream()
									.map(Payment::toStatusLine)
									.collect(Collectors.joining("\n")))))));
	}

	/**
	 * Provides order-number prefix completion for {@code payments://list}.
	 * @param repo payment repository
	 * @return single-element list consumed by {@code McpServerAutoConfiguration}
	 */
	@Bean
	public List<McpServerFeatures.SyncCompletionSpecification> mcpCompletions(PaymentRepository repo) {
		return List.of(new McpServerFeatures.SyncCompletionSpecification(
				new McpSchema.ResourceReference("payments://list"), (exchange, req) -> {
					String prefix = req.argument().value();
					List<String> candidates = repo.findAll()
						.stream()
						.map(Payment::orderNumber)
						.filter(n -> n.startsWith(prefix))
						.toList();
					return new McpSchema.CompleteResult(
							new McpSchema.CompleteResult.CompleteCompletion(candidates, candidates.size(), false));
				}));
	}

}
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

```bash
./gradlew :samples:payment-agent:test --tests "io.github.cokelee777.agent.payment.PaymentMcpConfigurationTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, 4개 테스트 모두 통과.

- [ ] **Step 7: 전체 payment-agent 테스트 확인**

```bash
./gradlew :samples:payment-agent:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 6: 전체 빌드 검증

- [ ] **Step 1: 전체 프로젝트 빌드**

```bash
./gradlew build 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. 모든 모듈 컴파일 및 테스트 통과.