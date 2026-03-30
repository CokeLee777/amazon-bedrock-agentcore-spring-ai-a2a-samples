package io.github.cokelee777.agent.host.invocation;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Request payload for {@code POST /invocations}.
 *
 * <p>
 * {@code conversationId} is optional in JSON — omit it on the first message and a UUID is
 * assigned in the compact constructor; echo the
 * {@link InvocationResponse#conversationId()} on later requests to continue the same chat
 * memory thread.
 * </p>
 *
 * @param prompt the user message; must not be blank
 * @param conversationId the chat memory conversation id; {@code null} or omitted to
 * generate a UUID
 */
public record InvocationRequest(@NotBlank(message = "prompt must not be blank") String prompt,
		@Nullable String conversationId) {

	public InvocationRequest {
		conversationId = Objects.requireNonNullElse(conversationId, UUID.randomUUID().toString());
	}

}
