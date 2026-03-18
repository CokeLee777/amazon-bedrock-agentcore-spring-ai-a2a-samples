package io.github.cokelee777.agent.delivery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryToolsTest {

	private final DeliveryTools tools = new DeliveryTools();

	@Test
	void trackDelivery_track1001_returnsDelivered() {
		String result = tools.trackDelivery("TRACK-1001");
		assertThat(result).contains("배송완료");
	}

	@Test
	void trackDelivery_track1002_returnsInTransit() {
		String result = tools.trackDelivery("TRACK-1002");
		assertThat(result).contains("배송중");
	}

	@Test
	void trackDelivery_track1003_returnsNotShipped() {
		String result = tools.trackDelivery("TRACK-1003");
		assertThat(result).contains("배송준비중");
	}

	@Test
	void trackDelivery_unknown_returnsNotFound() {
		String result = tools.trackDelivery("TRACK-9999");
		assertThat(result).contains("찾을 수 없습니다");
	}

}
