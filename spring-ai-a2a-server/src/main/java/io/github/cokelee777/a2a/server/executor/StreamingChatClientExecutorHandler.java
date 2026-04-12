package io.github.cokelee777.a2a.server.executor;

import io.a2a.server.agentexecution.RequestContext;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * Executes streaming ChatClient operations with A2A RequestContext.
 */
@FunctionalInterface
public interface StreamingChatClientExecutorHandler {

	/**
	 * Execute and return a token stream.
	 * @param chatClient the Spring AI ChatClient
	 * @param requestContext the A2A RequestContext
	 * @return a {@link Flux} of text tokens
	 */
	Flux<String> executeStream(ChatClient chatClient, RequestContext requestContext);

}