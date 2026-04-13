package io.github.cokelee777.a2a.server.executor;

import io.a2a.server.agentexecution.RequestContext;
import org.jspecify.annotations.Nullable;

/**
 * Executes agent logic with A2A RequestContext for A2A agents.
 *
 * <p>
 * This interface is used internally by {@link DefaultAgentExecutor} for executing
 * ChatClient operations in response to A2A protocol requests. Implementations are
 * responsible for obtaining a {@link org.springframework.ai.chat.client.ChatClient}
 * via constructor injection or lambda closure — not as a method parameter.
 *
 */
@FunctionalInterface
public interface ChatClientExecutorHandler {

	/**
	 * Execute and return response.
	 * @param requestContext the A2A RequestContext containing message, task, and context
	 * IDs
	 * @return the response text
	 */
	@Nullable String execute(RequestContext requestContext);

}
