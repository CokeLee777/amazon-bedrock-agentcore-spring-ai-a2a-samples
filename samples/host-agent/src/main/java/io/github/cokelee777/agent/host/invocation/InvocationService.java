package io.github.cokelee777.agent.host.invocation;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Orchestrates a single user turn against the host
 * {@link org.springframework.ai.chat.client.ChatClient}.
 *
 * <p>
 * Resolves {@code actorId} and {@code conversationId} (generating UUIDs when absent via
 * {@link InvocationRequest}), loads memory context, runs the model, and persists the
 * updated transcript.
 * </p>
 */
public interface InvocationService {

	/**
	 * Processes one invocation.
	 * @param request the prompt and optional ids
	 * @return assistant text and the effective ids
	 */
	InvocationResponse invoke(InvocationRequest request);

	/**
	 * Runs the orchestrator via SSE streaming and saves conversation history on
	 * completion.
	 * @param request the invocation request
	 * @param emitter the SSE emitter to which tokens are pushed
	 */
	void invokeStream(InvocationRequest request, SseEmitter emitter);

}
