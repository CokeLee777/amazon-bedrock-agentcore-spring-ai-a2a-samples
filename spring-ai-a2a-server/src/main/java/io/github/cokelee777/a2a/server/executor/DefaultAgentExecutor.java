package io.github.cokelee777.a2a.server.executor;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Task;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * Base class for A2A AgentExecutors using Spring AI ChatClient.
 *
 * <p>
 * This executor handles Task-based execution, managing the complete task lifecycle:
 * <ul>
 * <li>Creates and submits task via {@link TaskUpdater}</li>
 * <li>Extracts user message from A2A protocol using {@code TextExtractor}</li>
 * <li>Delegates to {@link ChatClientExecutorHandler} for agent-specific logic</li>
 * <li>Wraps response as task artifact and completes the task</li>
 * </ul>
 *
 * <p>
 * Implementations only need to provide a {@link ChatClientExecutorHandler} that takes a
 * {@link RequestContext} and returns a String response. All A2A protocol complexity and
 * task management is handled by this class.
 */
@Slf4j
public class DefaultAgentExecutor implements AgentExecutor {

	private final ChatClientExecutorHandler chatClientExecutorHandler;

	public DefaultAgentExecutor(ChatClientExecutorHandler chatClientExecutorHandler) {
		this.chatClientExecutorHandler = chatClientExecutorHandler;
	}

	@Override
	public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		TaskUpdater updater = new TaskUpdater(context, eventQueue);

		try {
			if (context.getTask() == null) {
				updater.submit();
			}
			updater.startWork();

			String response = this.chatClientExecutorHandler.execute(context);

			response = Objects.requireNonNullElse(response, "");

			updater.addArtifact(List.of(new TextPart(response)), null, null, null);
			updater.complete();
		}
		catch (Exception e) {
			log.error("Error executing agent task", e);
			throw new JSONRPCError(-32603, "Agent execution failed: " + e.getMessage(), null);
		}
	}

	@Override
	public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		log.debug("Cancelling task: {}", context.getTaskId());

		final Task task = context.getTask();

		if (task.getStatus().state() == TaskState.CANCELED) {
			// task already canceled
			throw new TaskNotCancelableError();
		}

		if (task.getStatus().state() == TaskState.COMPLETED) {
			// task already completed
			throw new TaskNotCancelableError();
		}

		TaskUpdater updater = new TaskUpdater(context, eventQueue);
		updater.cancel();
	}

}
