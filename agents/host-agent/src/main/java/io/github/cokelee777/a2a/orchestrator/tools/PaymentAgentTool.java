package io.github.cokelee777.a2a.orchestrator.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool that forwards payment-status queries to the downstream payment A2A
 * agent.
 *
 * <p>
 * The LLM invokes {@link #getPaymentStatus} when the user asks about payment or refund
 * status; the order number is forwarded to the payment agent via
 * {@link A2aTool#sendRequest}.
 * </p>
 */
@Component
public class PaymentAgentTool extends A2aTool {

	/**
	 * Request parameters for the payment-status skill.
	 *
	 * @param orderNumber the order number to check (e.g., {@code ORD-1001})
	 */
	public record PaymentStatusRequest(String orderNumber) {
	}

	/**
	 * Creates the tool pointing at the payment agent URL.
	 * @param agentUrl value of {@code a2a.payment-agent-url}
	 */
	public PaymentAgentTool(@Value("${a2a.payment-agent-url}") String agentUrl) {
		super(agentUrl);
	}

	/**
	 * Retrieves the payment or refund status for the given order.
	 * @param request contains the order number (e.g., {@code ORD-1001})
	 * @return payment status text from the downstream payment agent
	 */
	@Tool(description = "결제/환불 상태 확인. 주문번호(ORD-xxxx)가 필요합니다.")
	public String getPaymentStatus(PaymentStatusRequest request) {
		return sendRequest("payment_status", request.orderNumber() + " 결제 상태 확인");
	}

}
