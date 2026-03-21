package io.github.cokelee777.agent.host.invocation;

import org.springframework.util.Assert;

/**
 * Response payload for {@code POST /invocations}.
 *
 * <p>
 * {@code sessionId} and {@code actorId} are always non-null. Clients must persist these
 * values and include them in every subsequent request to continue the conversation.
 * </p>
 *
 * @param content the assistant response text
 * @param sessionId the session identifier used for this invocation
 * @param actorId the actor identifier used for this invocation
 */
public record InvocationResponse(String content, String sessionId, String actorId) {

    public InvocationResponse {
        Assert.hasText(content, "content must not be blank");
        Assert.hasText(sessionId, "sessionId must not be blank");
        Assert.hasText(actorId, "actorId must not be blank");
    }
}
