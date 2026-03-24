package io.github.cokelee777.agent.order;

import io.github.cokelee777.agent.order.domain.Order;
import io.github.cokelee777.agent.order.domain.OrderStatus;
import io.github.cokelee777.agent.order.remote.DeliveryAgentClient;
import io.github.cokelee777.agent.order.remote.PaymentAgentClient;
import io.github.cokelee777.agent.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Spring AI tools for the Order Agent.
 *
 * <p>
 * Exposes {@link #getOrderList} and {@link #checkOrderCancellability} as LLM-callable
 * tools. {@link #getOrderList} reads from {@link OrderRepository} only.
 * {@link #checkOrderCancellability} resolves the order lifecycle and calls the Delivery
 * and Payment agents only when {@link OrderStatus} says those queries are meaningful;
 * otherwise it returns explicit omission lines for the LLM.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OrderTools {

	private static final String DELIVERY_OMIT_PREFIX = "[배송 조회 생략] ";

	private static final String PAYMENT_OMIT_PREFIX = "[결제 조회 생략] ";

	private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors
		.newThreadPerTaskExecutor(Thread.ofVirtual().name("order-agent-", 1).factory());

	private final OrderRepository orderRepository;

	private final DeliveryAgentClient deliveryAgentClient;

	private final PaymentAgentClient paymentAgentClient;

	/**
	 * Returns the current member's order history with basic order information only.
	 * <p>
	 * Note: memberId is fixed (assumed from session context). Delivery status is not
	 * included here; use {@link #checkOrderCancellability} for detailed status.
	 * </p>
	 * @return formatted order list as plain text
	 */
	@Tool(description = "현재 회원의 주문 내역 목록 조회. 주문번호, 상품명, 금액, 주문일, 운송장번호를 반환합니다.")
	public String getOrderList() {
		return orderRepository.findAll()
			.stream()
			.map(Order::toListLine)
			.collect(Collectors.joining("\n", "[주문 내역]\n", "\n"));
	}

	/**
	 * Checks whether the given order can be cancelled by combining order state with
	 * optional delivery and payment status from downstream agents, depending on lifecycle
	 * phase.
	 * @param orderNumber the order number to check (e.g., {@code ORD-1001})
	 * @return order state, delivery line, and payment line as plain text for the LLM
	 */
	@Tool(description = "주문 취소 가능 여부 확인. 주문 상태와, 단계에 따라 배송·결제 에이전트 조회 결과(또는 조회 생략 사유)를 반환합니다.")
	public String checkOrderCancellability(
			@ToolParam(description = "취소 가능 여부를 확인할 주문번호 (예: ORD-1001)") String orderNumber) {
		Optional<Order> orderOpt = orderRepository.findByOrderNumber(orderNumber);
		if (orderOpt.isEmpty()) {
			return "해당 주문번호를 찾을 수 없습니다.\n" + DELIVERY_OMIT_PREFIX + "등록된 주문이 아니어서 배송 에이전트를 호출하지 않습니다.\n"
					+ PAYMENT_OMIT_PREFIX + "등록된 주문이 아니어서 결제 에이전트를 호출하지 않습니다.";
		}
		Order order = orderOpt.get();
		String orderState = order.toCancellabilityStateLine();
		CompletableFuture<String> deliveryFuture = CompletableFuture.supplyAsync(() -> resolveDeliveryStatus(order),
				VIRTUAL_THREAD_EXECUTOR);
		CompletableFuture<String> paymentFuture = CompletableFuture.supplyAsync(() -> resolvePaymentStatus(order),
				VIRTUAL_THREAD_EXECUTOR);
		String deliveryStatus = deliveryFuture.join();
		String paymentStatus = paymentFuture.join();
		return orderState + "\n" + deliveryStatus + "\n" + paymentStatus;
	}

	private String resolveDeliveryStatus(Order order) {
		OrderStatus phase = order.lifecyclePhase();
		if (!phase.requiresDeliveryAgentQuery()) {
			return DELIVERY_OMIT_PREFIX + phase.deliveryAgentOmissionDetail();
		}
		String tracking = order.trackingNumber();
		if (tracking == null || tracking.isBlank()) {
			return DELIVERY_OMIT_PREFIX + "운송장 번호가 없어 배송 에이전트를 호출하지 않습니다.";
		}
		return deliveryAgentClient.send("운송장번호 " + tracking + "의 배송 상태를 조회해주세요.");
	}

	private String resolvePaymentStatus(Order order) {
		OrderStatus phase = order.lifecyclePhase();
		if (!phase.requiresPaymentAgentQuery()) {
			return PAYMENT_OMIT_PREFIX + phase.paymentAgentOmissionDetail();
		}
		return paymentAgentClient.send("주문번호 " + order.orderNumber() + "의 결제 상태를 조회해주세요.");
	}

}
