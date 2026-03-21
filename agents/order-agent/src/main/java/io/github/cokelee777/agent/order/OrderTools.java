package io.github.cokelee777.agent.order;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tools for the Order Agent.
 *
 * <p>
 * Exposes {@link #getOrderList} and {@link #checkOrderCancellability} as LLM-callable
 * tools. {@link #getOrderList} returns basic order information without calling downstream
 * agents. {@link #checkOrderCancellability} fetches both delivery status from the
 * Delivery Agent and payment status from the Payment Agent to make a combined judgement.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OrderTools {

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
		return """
				[주문 내역]
				- ORD-1001 | 상품: 노트북 | 금액: 1,500,000원 | 주문일: 2026-03-01 | 운송장: TRACK-1001
				- ORD-1002 | 상품: 마우스 | 금액: 45,000원 | 주문일: 2026-03-10 | 운송장: TRACK-1002
				- ORD-1003 | 상품: 키보드 | 금액: 120,000원 | 주문일: 2026-03-12 | 운송장: TRACK-1003
				""";
	}

	/**
	 * Checks whether the given order can be cancelled by combining order state with
	 * delivery status fetched from the Delivery Agent and payment status fetched from the
	 * Payment Agent.
	 * @param orderNumber the order number to check (e.g., {@code ORD-1001})
	 * @return order state, delivery status, and payment status as plain text for the LLM
	 * to reason about
	 */
	@Tool(description = "주문 취소 가능 여부 확인. 주문 상태, 배송 에이전트에서 조회한 배송 상태, 결제 에이전트에서 조회한 결제 상태를 함께 반환합니다.")
	public String checkOrderCancellability(
			@ToolParam(description = "취소 가능 여부를 확인할 주문번호 (예: ORD-1001)") String orderNumber) {
		String orderState = switch (orderNumber) {
			case String s when s.contains("ORD-1001") -> "ORD-1001 주문 상태: 배송완료 | 운송장: TRACK-1001";
			case String s when s.contains("ORD-1002") -> "ORD-1002 주문 상태: 배송중 (배송 준비 단계) | 운송장: TRACK-1002";
			case String s when s.contains("ORD-1003") -> "ORD-1003 주문 상태: 결제완료 | 운송장: TRACK-1003";
			default -> "해당 주문번호를 찾을 수 없습니다.";
		};

		String trackingNumber = switch (orderNumber) {
			case String s when s.contains("ORD-1001") -> "TRACK-1001";
			case String s when s.contains("ORD-1002") -> "TRACK-1002";
			case String s when s.contains("ORD-1003") -> "TRACK-1003";
			default -> null;
		};

		String deliveryStatus = (trackingNumber != null)
				? deliveryAgentClient.send("운송장번호 " + trackingNumber + "의 배송 상태를 조회해주세요.") : "배송 정보 없음";

		String paymentStatus = paymentAgentClient.send("주문번호 " + orderNumber + "의 결제 상태를 조회해주세요.");

		return orderState + "\n" + deliveryStatus + "\n" + paymentStatus;
	}

}
