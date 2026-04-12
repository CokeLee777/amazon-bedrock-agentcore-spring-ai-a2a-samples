package io.github.cokelee777.a2a.integrationtests.samples;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.github.cokelee777.agent.payment.PaymentAgentApplication;
import io.github.cokelee777.a2a.agent.common.A2ATransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Payment Agent.
 *
 * <p>
 * Starts the full Payment Agent Spring Boot application with a mocked {@link ChatModel},
 * then verifies A2A protocol compliance (AgentCard endpoint) and end-to-end transport via
 * {@link A2ATransport}.
 * </p>
 */
@SpringBootTest(classes = PaymentAgentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "server.port=19003", "spring.ai.a2a.server.url=http://localhost:19003",
				"spring.autoconfigure.exclude=org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration" })
class PaymentAgentIntegrationTest {

	private static final String BASE_URL = "http://localhost:19003";

	private static final String MOCK_RESPONSE = "ORD-1001: 결제완료, 1,500,000원, 신용카드";

	@MockitoBean
	ChatModel chatModel;

	@Autowired
	AgentCard agentCard;

	@BeforeEach
	void setupChatModelMock() {
		ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(MOCK_RESPONSE))));
		when(chatModel.call(any(Prompt.class))).thenReturn(response);
		when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));
	}

	/**
	 * Verifies AgentCard bean contains correct payment agent metadata.
	 */
	@Test
	void agentCard_hasCorrectMetadata() {
		assertThat(agentCard.name()).isEqualTo("Payment Agent");
		assertThat(agentCard.description()).isNotBlank();
		assertThat(agentCard.skills()).hasSize(2);
		assertThat(agentCard.skills()).anyMatch(skill -> skill.id().equals("list_payments"));
		assertThat(agentCard.skills()).anyMatch(skill -> skill.id().equals("payment_status"));
	}

	/**
	 * Verifies the {@code /.well-known/agent-card.json} HTTP endpoint returns valid JSON
	 * containing the agent name.
	 */
	@Test
	void agentCardEndpoint_returnsValidJson() {
		String json = RestClient.create()
			.get()
			.uri(BASE_URL + "/.well-known/agent-card.json")
			.retrieve()
			.body(String.class);

		assertThat(json).contains("Payment Agent");
		assertThat(json).contains("payment_status");
	}

	/**
	 * Verifies {@link A2ATransport#sendStream} sends a message through the full A2A
	 * streaming stack and returns the agent's text response.
	 */
	@Test
	void send_returnsAgentResponse() {
		Message message = A2A.toUserMessage("주문번호 ORD-1001 결제 상태 조회해줘");

		String result = A2ATransport.sendStream(agentCard, message);

		assertThat(result).isEqualTo(MOCK_RESPONSE);
	}

}
