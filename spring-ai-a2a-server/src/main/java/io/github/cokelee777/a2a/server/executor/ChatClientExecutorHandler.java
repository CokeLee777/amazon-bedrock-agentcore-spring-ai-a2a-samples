package io.github.cokelee777.a2a.server.executor;

import io.a2a.server.agentexecution.RequestContext;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Executes ChatClient operations with A2A RequestContext for A2A agents.
 *
 * <p>
 * This interface is used internally by {@link DefaultAgentExecutor} for executing
 * ChatClient operations in response to A2A protocol requests.
 *
 */
@FunctionalInterface
public interface ChatClientExecutorHandler {

	/**
	 * Execute and return response.
	 * @param chatClient the Spring AI ChatClient
	 * @param requestContext the A2A RequestContext containing message, task, and context
	 * IDs
	 * @return the response text
	 */
	@Nullable String execute(ChatClient chatClient, RequestContext requestContext);

}
