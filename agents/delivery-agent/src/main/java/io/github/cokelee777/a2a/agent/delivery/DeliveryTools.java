package io.github.cokelee777.a2a.agent.delivery;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tools for the Delivery Agent.
 *
 * <p>
 * Exposes {@link #trackDelivery} as an LLM-callable tool. Mock data mirrors the original
 * {@code TrackDeliverySkillExecutor}.
 * </p>
 */
@Component
public class DeliveryTools {

	/**
	 * Returns mock delivery status for the given tracking number.
	 * @param trackingNumber the shipment tracking number (e.g., {@code TRACK-1001})
	 * @return delivery status as plain text
	 */
	@Tool(description = "운송장번호로 배송 상태 조회")
	public String trackDelivery(@ToolParam(description = "조회할 운송장번호 (예: TRACK-1001)") String trackingNumber) {
		return switch (trackingNumber) {
			case String s when s.contains("TRACK-1001") -> "TRACK-1001 배송 상태: 배송완료 — 2026-03-05 14:32 수령인 인도 완료";
			case String s when s.contains("TRACK-1002") -> "TRACK-1002 배송 상태: 배송중 — 현재 서울 물류센터에서 출발 (2026-03-12 09:15)";
			case String s when s.contains("TRACK-1003") -> "TRACK-1003 배송 상태: 배송준비중 — 아직 발송되지 않았습니다";
			default -> "해당 운송장번호의 배송 정보를 찾을 수 없습니다.";
		};
	}

}
