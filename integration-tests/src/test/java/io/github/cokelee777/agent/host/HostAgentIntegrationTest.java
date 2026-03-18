package io.github.cokelee777.agent.host;

import io.github.cokelee777.agent.host.HostAgentApplication;
import io.github.cokelee777.agent.host.RemoteAgentConnections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Host Agent (orchestrator).
 *
 * <p>
 * Starts the full Host Agent Spring Boot application with a mocked {@link ChatModel},
 * then verifies the {@code POST /invocations} endpoint, dynamic system prompt generation,
 * and {@link RemoteAgentConnections} routing behaviour.
 * </p>
 */
@SpringBootTest(classes = HostAgentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "server.port=18080", "spring.ai.a2a.server.enabled=false",
				"remote.agents.order-agent.url=http://localhost:19901",
				"remote.agents.delivery-agent.url=http://localhost:19902",
				"remote.agents.payment-agent.url=http://localhost:19903",
				"spring.autoconfigure.exclude=org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration" })
class HostAgentIntegrationTest {

	private static final String BASE_URL = "http://localhost:18080";

	private static final String MOCK_RESPONSE = "주문 ORD-1001을 성공적으로 조회했습니다.";

	@MockitoBean
	ChatModel chatModel;

	@Autowired
	RemoteAgentConnections connections;

	@BeforeEach
	void setupChatModelMock() {
		ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(MOCK_RESPONSE))));
		when(chatModel.call(any(Prompt.class))).thenReturn(response);
	}

	/**
	 * Verifies {@code POST /invocations} returns the LLM response as a plain string.
	 */
	@Test
	void invoke_returnsLlmResponse() {
		String response = RestClient.create()
			.post()
			.uri(BASE_URL + "/invocations")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("prompt", "주문 목록 조회해줘"))
			.retrieve()
			.body(String.class);

		assertThat(response).isEqualTo(MOCK_RESPONSE);
	}

	/**
	 * Verifies that the system prompt sent to the LLM contains the routing guidance
	 * section, confirming it is built dynamically on each request.
	 */
	@Test
	void invoke_systemPromptContainsRoutingGuidance() {
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

		RestClient.create()
			.post()
			.uri(BASE_URL + "/invocations")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("prompt", "배송 현황 알려줘"))
			.retrieve()
			.body(String.class);

		verify(chatModel).call(promptCaptor.capture());

		String systemContent = promptCaptor.getValue()
			.getInstructions()
			.stream()
			.filter(msg -> MessageType.SYSTEM.equals(msg.getMessageType()))
			.map(org.springframework.ai.content.Content::getText)
			.findFirst()
			.orElse("");

		assertThat(systemContent).contains("에이전트 라우터");
		assertThat(systemContent).contains("sendMessage");
	}

	/**
	 * Verifies {@link RemoteAgentConnections#sendMessage} returns a formatted error when
	 * the requested agent name is not found.
	 */
	@Test
	void sendMessage_unknownAgent_returnsErrorMessage() {
		String result = connections.sendMessage("Unknown Agent", "some task");

		assertThat(result).contains("Agent 'Unknown Agent' not found");
		assertThat(result).contains("Available agents:");
	}

}
