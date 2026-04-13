package io.github.cokelee777.a2a.server.executor;

import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.Event;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import io.github.cokelee777.a2a.server.support.A2AServerTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link DefaultAgentExecutor} task lifecycle and cancellation rules.
 */
class DefaultAgentExecutorTest {

	private final ServerCallContext callContext = new ServerCallContext(null, Map.of(), Set.of());

	@Test
	void execute_withoutTask_submitsThenCompletesWithArtifact() {
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "hello");
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).containsExactly(TaskState.SUBMITTED, TaskState.WORKING, TaskState.COMPLETED);
		assertThat(artifactTexts(events)).containsExactly("hello");
		assertThat(artifactAppendFlags(events)).containsExactly(false);
		assertThat(artifactLastChunkFlags(events)).containsExactly(false);
		assertThat(lastStatusFinal(events)).isTrue();
	}

	@Test
	void execute_withExistingTask_skipsSubmitted() {
		Task existing = A2AServerTestFixtures.taskInState("task-1", "ctx-1", TaskState.SUBMITTED);
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", existing, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "x");
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).containsExactly(TaskState.WORKING, TaskState.COMPLETED);
	}

	@Test
	void execute_withExistingTask_working_skipsSubmitted() {
		Task existing = A2AServerTestFixtures.taskInState("task-1", "ctx-1", TaskState.WORKING);
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", existing, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "x");
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).doesNotContain(TaskState.SUBMITTED).contains(TaskState.COMPLETED);
		assertThat(artifactTexts(events)).containsExactly("x");
	}

	@Test
	void execute_nullHandlerResponse_usesEmptyTextArtifact() {
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> null);
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(artifactTexts(events)).containsExactly("");
		assertThat(statusStates(events)).containsExactly(TaskState.SUBMITTED, TaskState.WORKING, TaskState.COMPLETED);
		assertThat(lastStatusFinal(events)).isTrue();
	}

	@Test
	void execute_emptyString_emitsEmptyTextArtifact() {
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "");
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(artifactTexts(events)).containsExactly("");
		assertThat(statusStates(events)).containsExactly(TaskState.SUBMITTED, TaskState.WORKING, TaskState.COMPLETED);
	}

	@Test
	void execute_handlerThrowsJsonRpcError_propagatesUnchanged() {
		EventQueue queue = eventQueue("task-1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			JSONRPCError original = new JSONRPCError(-32001, "application error", null);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> {
				throw original;
			});
			assertThatThrownBy(() -> executor.execute(ctx, queue)).isSameAs(original);
		}
		finally {
			queue.close();
		}
	}

	@Test
	void execute_handlerThrows_wrapsAsJsonRpcError() {
		EventQueue queue = eventQueue("task-1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> {
				throw new IllegalStateException("bad");
			});
			assertThatThrownBy(() -> executor.execute(ctx, queue)).isInstanceOf(JSONRPCError.class).satisfies(t -> {
				JSONRPCError e = (JSONRPCError) t;
				assertThat(e.getCode()).isEqualTo(-32603);
				assertThat(e.getMessage()).contains("Agent execution failed: bad");
			});
		}
		finally {
			queue.close();
		}
	}

	@Test
	void execute_handlerThrowsUncheckedIOException_wrapsAsJsonRpcError() {
		EventQueue queue = eventQueue("task-1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> {
				throw new UncheckedIOException("io failed", new IOException("root"));
			});
			assertThatThrownBy(() -> executor.execute(ctx, queue)).isInstanceOf(JSONRPCError.class).satisfies(t -> {
				JSONRPCError e = (JSONRPCError) t;
				assertThat(e.getCode()).isEqualTo(-32603);
				assertThat(e.getMessage()).contains("Agent execution failed: io failed");
			});
		}
		finally {
			queue.close();
		}
	}

	@Test
	void execute_handlerThrowsAssertionError_propagatesUnchanged() {
		EventQueue queue = eventQueue("task-1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			AssertionError err = new AssertionError("boom");
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> {
				throw err;
			});
			assertThatThrownBy(() -> executor.execute(ctx, queue)).isSameAs(err);
		}
		finally {
			queue.close();
		}
	}

	@Test
	void cancel_whenCompleted_throwsTaskNotCancelableError() {
		Task task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.COMPLETED);
		EventQueue queue = eventQueue("t1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "");
			assertThatThrownBy(() -> executor.cancel(ctx, queue)).isInstanceOf(TaskNotCancelableError.class);
		}
		finally {
			queue.close();
		}
	}

	@Test
	void cancel_whenCanceled_throwsTaskNotCancelableError() {
		Task task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.CANCELED);
		EventQueue queue = eventQueue("t1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "");
			assertThatThrownBy(() -> executor.cancel(ctx, queue)).isInstanceOf(TaskNotCancelableError.class);
		}
		finally {
			queue.close();
		}
	}

	@Test
	void cancel_whenWorking_emitsCanceled() {
		Task task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.WORKING);
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("t1", events);
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "");
			executor.cancel(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).contains(TaskState.CANCELED);
	}

	@Test
	void cancel_whenSubmitted_emitsCanceled() {
		Task task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.SUBMITTED);
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("t1", events);
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "");
			executor.cancel(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).contains(TaskState.CANCELED);
	}

	@Test
	void cancel_whenFailed_emitsCanceled() {
		Task task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.FAILED);
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("t1", events);
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			ChatClient chatClient = mock(ChatClient.class);
			DefaultAgentExecutor executor = new DefaultAgentExecutor(chatClient, (c, rc) -> "");
			executor.cancel(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).contains(TaskState.CANCELED);
	}

	private static EventQueue eventQueue(String taskId, List<Event> sink) {
		InMemoryTaskStore taskStore = new InMemoryTaskStore();
		return new EventQueue.EventQueueBuilder().queueSize(64)
			.taskId(taskId)
			.hook(item -> sink.add(item.getEvent()))
			.taskStateProvider(taskStore)
			.build();
	}

	private static List<TaskState> statusStates(List<Event> events) {
		return events.stream()
			.filter(TaskStatusUpdateEvent.class::isInstance)
			.map(e -> ((TaskStatusUpdateEvent) e).getStatus().state())
			.toList();
	}

	private static List<String> artifactTexts(List<Event> events) {
		return events.stream()
			.filter(TaskArtifactUpdateEvent.class::isInstance)
			.map(e -> ((TaskArtifactUpdateEvent) e).getArtifact()
				.parts()
				.stream()
				.filter(TextPart.class::isInstance)
				.map(p -> ((TextPart) p).getText())
				.toList())
			.flatMap(List::stream)
			.toList();
	}

	private static List<Boolean> artifactAppendFlags(List<Event> events) {
		return events.stream()
			.filter(TaskArtifactUpdateEvent.class::isInstance)
			.map(e -> Boolean.TRUE.equals(((TaskArtifactUpdateEvent) e).isAppend()))
			.toList();
	}

	private static List<Boolean> artifactLastChunkFlags(List<Event> events) {
		return events.stream()
			.filter(TaskArtifactUpdateEvent.class::isInstance)
			.map(e -> Boolean.TRUE.equals(((TaskArtifactUpdateEvent) e).isLastChunk()))
			.toList();
	}

	private static boolean lastStatusFinal(List<Event> events) {
		List<TaskStatusUpdateEvent> statusEvents = events.stream()
			.filter(TaskStatusUpdateEvent.class::isInstance)
			.map(TaskStatusUpdateEvent.class::cast)
			.toList();
		assertThat(statusEvents).isNotEmpty();
		return statusEvents.get(statusEvents.size() - 1).isFinal();
	}

}
