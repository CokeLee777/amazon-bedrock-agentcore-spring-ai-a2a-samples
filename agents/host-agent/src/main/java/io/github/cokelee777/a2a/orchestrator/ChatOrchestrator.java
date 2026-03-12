package io.github.cokelee777.a2a.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Orchestrates user requests by invoking the configured {@link ChatClient} with
 * session-scoped conversation memory.
 *
 * <p>
 * The {@link ChatClient} is pre-configured with a system prompt, A2A tool functions, and
 * a {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor}. This
 * service simply binds the correct session ID to each call so the LLM maintains
 * conversation context across turns.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ChatOrchestrator {

	private final ChatClient chatClient;

	/**
	 * Sends the user message in {@code request} to the LLM and returns the generated
	 * response.
	 * @param request the chat request containing the user message and session ID
	 * @return a {@link ChatResponse} with the LLM reply, or a fallback message if the
	 * call fails
	 */
	public ChatResponse handle(ChatRequest request) {
		Assert.notNull(request, "request must not be null");
		try {
			String content = chatClient.prompt()
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, request.sessionId()))
				.user(request.userMessage())
				.call()
				.content();
			return new ChatResponse(content != null ? content : "응답을 생성하지 못했습니다.");
		}
		catch (Exception e) {
			return new ChatResponse("처리 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

}
