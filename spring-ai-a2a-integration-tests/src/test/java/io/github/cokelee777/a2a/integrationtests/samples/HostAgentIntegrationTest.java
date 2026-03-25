package io.github.cokelee777.a2a.integrationtests.samples;

import io.github.cokelee777.agent.host.HostAgentApplication;
import io.github.cokelee777.agent.host.remote.RemoteAgentTools;
import io.github.cokelee777.agent.host.remote.RemoteAgentDelegationRequest;
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
import org.springframework.core.ParameterizedTypeReference;
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
 * and {@link RemoteAgentTools} routing behavior.
 * </p>
 */
@SpringBootTest(classes = HostAgentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "server.port=18080", "spring.ai.a2a.server.enabled=false",
				"spring.autoconfigure.exclude=org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration" })
class HostAgentIntegrationTest {

	private static final String BASE_URL = "http://localhost:18080";

	private static final String MOCK_RESPONSE = "주문 ORD-1001을 성공적으로 조회했습니다.";

	@MockitoBean
	ChatModel chatModel;

	@Autowired
	RemoteAgentTools remoteAgentTools;

	@BeforeEach
	void setupChatModelMock() {
		ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(MOCK_RESPONSE))));
		when(chatModel.call(any(Prompt.class))).thenReturn(response);
	}

	/**
	 * Verifies {@code POST /invocations} returns JSON with assistant content and session
	 * identifiers.
	 */
	@Test
	void invoke_returnsLlmResponse() {
		Map<String, String> response = RestClient.create()
			.post()
			.uri(BASE_URL + "/invocations")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("prompt", "주문 목록 조회해줘"))
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		assertThat(response).containsEntry("content", MOCK_RESPONSE);
		assertThat(response).containsKeys("sessionId", "actorId");
		assertThat(response.get("sessionId")).isNotBlank();
		assertThat(response.get("actorId")).isNotBlank();
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
		assertThat(systemContent).contains("delegateToRemoteAgent");
		assertThat(systemContent).contains("delegateToRemoteAgentsParallel");
	}

	/**
	 * Verifies {@link RemoteAgentTools#delegateToRemoteAgent} returns a formatted error
	 * when the requested agent name is not found.
	 */
	@Test
	void delegateToRemoteAgent_unknownAgent_returnsErrorMessage() {
		String result = remoteAgentTools
			.delegateToRemoteAgent(new RemoteAgentDelegationRequest("Unknown Agent", "some task"));

		assertThat(result).contains("Agent 'Unknown Agent' not found");
		assertThat(result).contains("Available agents:");
	}

}
