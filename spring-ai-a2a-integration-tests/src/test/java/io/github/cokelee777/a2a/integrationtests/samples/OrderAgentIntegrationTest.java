package io.github.cokelee777.a2a.integrationtests.samples;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.github.cokelee777.agent.order.OrderAgentApplication;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Order Agent.
 *
 * <p>
 * Starts the full Order Agent Spring Boot application with a mocked {@link ChatModel},
 * then verifies A2A protocol compliance (AgentCard endpoint) and end-to-end transport via
 * {@link A2ATransport}.
 * </p>
 */
@SpringBootTest(classes = OrderAgentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "server.port=19001", "spring.ai.a2a.server.url=http://localhost:19001",
				"spring.autoconfigure.exclude=org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration" })
class OrderAgentIntegrationTest {

	private static final String BASE_URL = "http://localhost:19001";

	private static final String MOCK_RESPONSE = "ORD-1001: 노트북, 1,500,000원, 배송완료";

	@MockitoBean
	ChatModel chatModel;

	@Autowired
	AgentCard agentCard;

	@BeforeEach
	void setupChatModelMock() {
		ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(MOCK_RESPONSE))));
		when(chatModel.call(any(Prompt.class))).thenReturn(response);
	}

	/**
	 * Verifies AgentCard bean contains correct order agent metadata.
	 */
	@Test
	void agentCard_hasCorrectMetadata() {
		assertThat(agentCard.name()).isEqualTo("Order Agent");
		assertThat(agentCard.description()).isNotBlank();
		assertThat(agentCard.skills()).hasSize(2);
		assertThat(agentCard.skills()).anyMatch(skill -> skill.id().equals("order_list"));
		assertThat(agentCard.skills()).anyMatch(skill -> skill.id().equals("order_cancellability_check"));
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

		assertThat(json).contains("Order Agent");
		assertThat(json).contains("order_list");
	}

	/**
	 * Verifies {@link A2ATransport#send} sends a message through the full A2A stack and
	 * returns the agent's text response.
	 */
	@Test
	void send_returnsAgentResponse() {
		Message message = A2A.toUserMessage("회원 user-1의 주문 목록 조회해줘");

		String result = A2ATransport.send(agentCard, message);

		assertThat(result).isEqualTo(MOCK_RESPONSE);
	}

}
