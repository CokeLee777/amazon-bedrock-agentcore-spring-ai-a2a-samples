package io.github.cokelee777.a2a.orchestrator;

import org.springframework.util.Assert;

/**
 * Encapsulates the output of {@link ChatOrchestrator#handle(ChatRequest)}.
 *
 * @param content LLM-generated reply text; must not be null
 */
public record ChatResponse(String content) {

	/**
	 * Creates a {@code ChatResponse} and validates that {@code content} is not
	 * {@code null}.
	 */
	public ChatResponse {
		Assert.notNull(content, "content must not be null");
	}

}
