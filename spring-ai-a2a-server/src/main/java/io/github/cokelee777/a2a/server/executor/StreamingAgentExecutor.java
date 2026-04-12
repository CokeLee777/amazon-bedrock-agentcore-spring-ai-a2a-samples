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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link AgentExecutor} implementation that uses
 * {@link StreamingChatClientExecutorHandler} to run agent logic. Accumulates the token
 * stream and emits a single artifact.
 */
@Slf4j
public class StreamingAgentExecutor implements AgentExecutor {

	private final ChatClient chatClient;

	private final StreamingChatClientExecutorHandler streamingHandler;

	public StreamingAgentExecutor(ChatClient chatClient, StreamingChatClientExecutorHandler streamingHandler) {
		this.chatClient = chatClient;
		this.streamingHandler = streamingHandler;
	}

	@Override
	public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		TaskUpdater updater = new TaskUpdater(context, eventQueue);
		try {
			if (context.getTask() == null) {
				updater.submit();
			}
			updater.startWork();

			String response = this.streamingHandler.executeStream(this.chatClient, context)
				.collect(Collectors.joining())
				.blockOptional()
				.orElse("");
			log.debug("Streaming response accumulated: {} chars", response.length());
			updater.addArtifact(List.of(new TextPart(response)));
			updater.complete();
		}
		catch (Exception e) {
			log.error("Error executing streaming agent task", e);
			throw new JSONRPCError(-32603, "Agent execution failed: " + e.getMessage(), null);
		}
	}

	@Override
	public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		log.debug("Cancelling streaming task: {}", context.getTaskId());

		final Task task = context.getTask();

		if (task.getStatus().state() == TaskState.CANCELED) {
			throw new TaskNotCancelableError();
		}

		if (task.getStatus().state() == TaskState.COMPLETED) {
			throw new TaskNotCancelableError();
		}

		TaskUpdater updater = new TaskUpdater(context, eventQueue);
		updater.cancel();
	}

}
