package io.github.cokelee777.a2a.agent.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderToolsTest {

	private DeliveryAgentClient deliveryAgentClient;

	private PaymentAgentClient paymentAgentClient;

	private OrderTools tools;

	@BeforeEach
	void setUp() {
		deliveryAgentClient = mock(DeliveryAgentClient.class);
		paymentAgentClient = mock(PaymentAgentClient.class);
		tools = new OrderTools(deliveryAgentClient, paymentAgentClient);
	}

	@Test
	void getOrderList_returnsAllOrdersWithDeliveryStatus() {
		when(deliveryAgentClient.send(anyString())).thenReturn("배송완료");

		String result = tools.getOrderList();

		assertThat(result).contains("ORD-1001").contains("ORD-1002").contains("ORD-1003");
		assertThat(result).contains("TRACK-1001").contains("TRACK-1002").contains("TRACK-1003");
		assertThat(result).contains("배송완료");
	}

	@Test
	void checkOrderCancellability_ord1001_returnsOrderAndPaymentStatus() {
		when(paymentAgentClient.send(anyString())).thenReturn("ORD-1001 결제 상태: 결제완료 — 1,500,000원 (카드결제, 2026-03-01)");

		String result = tools.checkOrderCancellability("ORD-1001");

		assertThat(result).contains("ORD-1001").contains("배송완료").contains("결제완료");
	}

	@Test
	void checkOrderCancellability_ord1002_returnsOrderAndPaymentStatus() {
		when(paymentAgentClient.send(anyString())).thenReturn("ORD-1002 결제 상태: 결제완료 — 45,000원 (카드결제, 2026-03-10)");

		String result = tools.checkOrderCancellability("ORD-1002");

		assertThat(result).contains("ORD-1002").contains("배송중").contains("결제완료");
	}

	@Test
	void checkOrderCancellability_ord1003_returnsOrderAndPaymentStatus() {
		when(paymentAgentClient.send(anyString())).thenReturn("ORD-1003 결제 상태: 결제완료 — 120,000원 (간편결제, 2026-03-12)");

		String result = tools.checkOrderCancellability("ORD-1003");

		assertThat(result).contains("ORD-1003").contains("결제완료");
	}

	@Test
	void checkOrderCancellability_unknown_returnsNotFound() {
		String result = tools.checkOrderCancellability("ORD-9999");

		assertThat(result).contains("찾을 수 없습니다");
	}

}
