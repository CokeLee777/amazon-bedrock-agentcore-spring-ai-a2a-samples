package io.github.cokelee777.a2a.orchestrator;

import org.springframework.util.Assert;

/**
 * Encapsulates the input to {@link ChatOrchestrator#handle(ChatRequest)}.
 *
 * @param userMessage raw text submitted by the caller; must not be blank
 * @param sessionId conversation scope identifier; must not be blank
 */
public record ChatRequest(String userMessage, String sessionId) {

	/**
	 * Creates a {@code ChatRequest} and validates that neither field is blank.
	 */
	public ChatRequest {
		Assert.hasText(userMessage, "userMessage must not be blank");
		Assert.hasText(sessionId, "sessionId must not be blank");
	}

}
