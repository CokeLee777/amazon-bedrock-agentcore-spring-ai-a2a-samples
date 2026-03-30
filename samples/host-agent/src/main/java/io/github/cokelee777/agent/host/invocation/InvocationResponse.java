package io.github.cokelee777.agent.host.invocation;

import org.springframework.util.Assert;

/**
 * Response payload for {@code POST /invocations}.
 *
 * <p>
 * {@code conversationId} is always non-null. Clients must persist it and send it back on
 * subsequent requests to continue the conversation.
 * </p>
 *
 * @param content the assistant response text
 * @param conversationId the conversation identifier used for this invocation (chat memory
 * key)
 */
public record InvocationResponse(String content, String conversationId) {

	public InvocationResponse {
		Assert.notNull(content, "content must not be null");
		Assert.notNull(conversationId, "conversationId must not be null");
	}

}
