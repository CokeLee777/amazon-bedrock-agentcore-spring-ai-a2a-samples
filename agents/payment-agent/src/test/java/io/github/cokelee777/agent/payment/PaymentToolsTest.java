package io.github.cokelee777.agent.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentToolsTest {

	private final PaymentTools tools = new PaymentTools();

	@Test
	void getPaymentStatus_ord1001_returnsPaymentInfo() {
		assertThat(tools.getPaymentStatus("ORD-1001")).contains("1,500,000원");
	}

	@Test
	void getPaymentStatus_ord1002_returnsPaymentInfo() {
		assertThat(tools.getPaymentStatus("ORD-1002")).contains("45,000원");
	}

	@Test
	void getPaymentStatus_ord1003_returnsPaymentInfo() {
		assertThat(tools.getPaymentStatus("ORD-1003")).contains("120,000원");
	}

	@Test
	void getPaymentStatus_unknown_returnsNotFound() {
		assertThat(tools.getPaymentStatus("ORD-9999")).contains("찾을 수 없습니다");
	}

}
