package io.github.cokelee777.a2a.server.executor;

import io.a2a.server.agentexecution.RequestContext;
import reactor.core.publisher.Flux;

/**
 * Executes streaming agent logic with A2A RequestContext.
 *
 * <p>
 * Implementations are responsible for obtaining a
 * {@link org.springframework.ai.chat.client.ChatClient} via constructor injection or
 * lambda closure — not as a method parameter.
 */
@FunctionalInterface
public interface StreamingChatClientExecutorHandler {

	/**
	 * Execute and return a token stream.
	 * @param requestContext the A2A RequestContext
	 * @return a {@link Flux} of text tokens
	 */
	Flux<String> executeStream(RequestContext requestContext);

}
