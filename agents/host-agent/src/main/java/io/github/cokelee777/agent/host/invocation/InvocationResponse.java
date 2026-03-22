package io.github.cokelee777.agent.host.invocation;

import org.springframework.util.Assert;

/**
 * Response payload for {@code POST /invocations}.
 *
 * <p>
 * {@code sessionId} and {@code actorId} are always non-null. Clients must persist these
 * values to continue the conversation in subsequent requests.
 * </p>
 *
 * @param content the assistant response text
 * @param sessionId the session identifier used for this invocation
 * @param actorId the actor identifier used for this invocation
 */
public record InvocationResponse(String content, String sessionId, String actorId) {

	public InvocationResponse {
		Assert.notNull(content, "content must not be null");
	}

}
