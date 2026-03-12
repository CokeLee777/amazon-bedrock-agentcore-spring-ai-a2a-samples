package io.github.cokelee777.a2a.orchestrator.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool that forwards order-related queries to the downstream order A2A agent.
 *
 * <p>
 * The LLM invokes {@link #getOrderList} or {@link #checkOrderCancellability} based on the
 * user's intent; each method translates the call into an A2A {@code message/send} request
 * via {@link A2aTool#sendRequest}.
 * </p>
 */
@Component
public class OrderAgentTool extends A2aTool {

	/**
	 * Request parameters for the order-list skill.
	 *
	 * @param memberId the member whose orders to retrieve
	 */
	public record OrderListRequest(String memberId) {
	}

	/**
	 * Request parameters for the order-cancellability skill.
	 *
	 * @param orderNumber the order number to check (e.g., {@code ORD-1001})
	 */
	public record OrderCancellabilityRequest(String orderNumber) {
	}

	/**
	 * Creates the tool pointing at the order agent URL.
	 * @param agentUrl value of {@code a2a.order-agent-url}
	 */
	public OrderAgentTool(@Value("${a2a.order-agent-url}") String agentUrl) {
		super(agentUrl);
	}

	/**
	 * Retrieves the order history for the given member.
	 * @param request contains the member ID
	 * @return order list text from the downstream order agent
	 */
	@Tool(description = "해당 회원의 주문 내역(목록)을 조회합니다. 현재 사용자 ID(memberId)가 필요합니다.")
	public String getOrderList(OrderListRequest request) {
		return sendRequest("order_list", "MEMBER-" + request.memberId() + " 주문내역 조회");
	}

	/**
	 * Checks whether the given order can be cancelled by consulting delivery and payment
	 * status in the downstream order agent.
	 * @param request contains the order number (e.g., {@code ORD-1001})
	 * @return cancellability verdict text from the downstream order agent
	 */
	@Tool(description = "주문 취소 가능 여부 확인. 주문번호(ORD-xxxx)가 필요합니다.")
	public String checkOrderCancellability(OrderCancellabilityRequest request) {
		return sendRequest("order_cancellability_check", request.orderNumber() + " 취소 가능 여부 확인");
	}

}
