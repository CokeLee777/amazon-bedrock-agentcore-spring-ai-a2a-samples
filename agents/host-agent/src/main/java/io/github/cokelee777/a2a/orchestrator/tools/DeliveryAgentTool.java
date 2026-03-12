package io.github.cokelee777.a2a.orchestrator.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool that forwards delivery-tracking queries to the downstream delivery A2A
 * agent.
 *
 * <p>
 * The LLM invokes {@link #trackDelivery} when the user asks about shipment status; the
 * tracking number is passed through to the delivery agent via
 * {@link A2aTool#sendRequest}.
 * </p>
 */
@Component
public class DeliveryAgentTool extends A2aTool {

	/**
	 * Request parameters for the delivery-tracking skill.
	 *
	 * @param trackingNumber the tracking number to look up (e.g., {@code TRACK-1001})
	 */
	public record DeliveryRequest(String trackingNumber) {
	}

	/**
	 * Creates the tool pointing at the delivery agent URL.
	 * @param agentUrl value of {@code a2a.delivery-agent-url}
	 */
	public DeliveryAgentTool(@Value("${a2a.delivery-agent-url}") String agentUrl) {
		super(agentUrl);
	}

	/**
	 * Retrieves the current delivery status for the given tracking number.
	 * @param request contains the tracking number (e.g., {@code TRACK-1001})
	 * @return delivery status text from the downstream delivery agent
	 */
	@Tool(description = "배송 조회. 운송장번호(TRACK-xxxx)가 필요합니다.")
	public String trackDelivery(DeliveryRequest request) {
		return sendRequest("track_delivery", request.trackingNumber() + " 배송 조회");
	}

}
