package io.github.cokelee777.agent.payment;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tools for the Payment Agent.
 *
 * <p>
 * Exposes {@link #getPaymentStatus} as an LLM-callable tool. Mock data mirrors the
 * original {@code PaymentStatusSkillExecutor}.
 * </p>
 */
@Component
public class PaymentTools {

	/**
	 * Returns mock payment status for the given order number.
	 * @param orderNumber the order number (e.g., {@code ORD-1001})
	 * @return payment status as plain text
	 */
	@Tool(description = "주문번호로 결제/환불 상태 조회")
	public String getPaymentStatus(@ToolParam(description = "결제/환불 상태를 조회할 주문번호 (예: ORD-1001)") String orderNumber) {
		return switch (orderNumber) {
			case String s when s.contains("ORD-1001") -> "ORD-1001 결제 상태: 결제완료 — 1,500,000원 (카드결제, 2026-03-01)";
			case String s when s.contains("ORD-1002") -> "ORD-1002 결제 상태: 결제완료 — 45,000원 (카드결제, 2026-03-10)";
			case String s when s.contains("ORD-1003") -> "ORD-1003 결제 상태: 결제완료 — 120,000원 (간편결제, 2026-03-12)";
			default -> "해당 주문번호의 결제 정보를 찾을 수 없습니다.";
		};
	}

}
