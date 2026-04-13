package io.github.cokelee777.a2a.server.executor;

import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.Event;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import io.github.cokelee777.a2a.server.support.A2AServerTestFixtures;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StreamingAgentExecutor} task lifecycle and cancellation rules.
 */
class StreamingAgentExecutorTest {

	private final ServerCallContext callContext = new ServerCallContext(null, Map.of(), Set.of());

	@Test
	void execute_withoutTask_submitsThenCompletesWithArtifact() {
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.just("hello", " world"));
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).containsExactly(TaskState.SUBMITTED, TaskState.WORKING, TaskState.COMPLETED);
		assertThat(artifactTexts(events)).containsExactly("hello", " world");
		assertThat(String.join("", artifactTexts(events))).isEqualTo("hello world");
		assertThat(artifactAppendFlags(events)).containsExactly(false, true);
		assertThat(artifactLastChunkFlags(events)).containsExactly(false, true);
		assertThat(lastStatusFinal(events)).isTrue();
	}

	@Test
	void execute_withExistingTask_skipsSubmitted() {
		var existing = A2AServerTestFixtures.taskInState("task-1", "ctx-1", TaskState.SUBMITTED);
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", existing, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.just("x"));
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).containsExactly(TaskState.WORKING, TaskState.COMPLETED);
	}

	@Test
	void execute_emptyFlux_emitsEmptyArtifact() {
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.empty());
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
	void execute_reactorCancelError_returnsWithoutJsonRpcError() {
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.error(Exceptions.failWithCancel()));
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(statusStates(events)).contains(TaskState.WORKING).doesNotContain(TaskState.COMPLETED);
	}

	@Test
	void execute_fluxError_wrapsAsJsonRpcError() {
		EventQueue queue = eventQueue("task-1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(
					rc -> Flux.error(new RuntimeException("stream error")));
			assertThatThrownBy(() -> executor.execute(ctx, queue)).isInstanceOf(JSONRPCError.class).satisfies(t -> {
				JSONRPCError e = (JSONRPCError) t;
				assertThat(e.getCode()).isEqualTo(-32603);
				assertThat(e.getMessage()).contains("stream error");
			});
		}
		finally {
			queue.close();
		}
	}

	@Test
	void execute_singleChunk_emitsAppendFalseAndLastChunkTrue() {
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("task-1", events);
		try {
			RequestContext ctx = new RequestContext(null, "task-1", "ctx-1", null, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.just("only"));
			executor.execute(ctx, queue);
		}
		finally {
			queue.close();
		}

		assertThat(artifactTexts(events)).containsExactly("only");
		assertThat(artifactAppendFlags(events)).containsExactly(false);
		assertThat(artifactLastChunkFlags(events)).containsExactly(true);
	}

	@Test
	void cancel_whenCanceled_throwsTaskNotCancelableError() {
		var task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.CANCELED);
		EventQueue queue = eventQueue("t1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.just(""));
			assertThatThrownBy(() -> executor.cancel(ctx, queue)).isInstanceOf(TaskNotCancelableError.class);
		}
		finally {
			queue.close();
		}
	}

	@Test
	void cancel_whenCompleted_throwsTaskNotCancelableError() {
		var task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.COMPLETED);
		EventQueue queue = eventQueue("t1", new ArrayList<>());
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.just(""));
			assertThatThrownBy(() -> executor.cancel(ctx, queue)).isInstanceOf(TaskNotCancelableError.class);
		}
		finally {
			queue.close();
		}
	}

	@Test
	void cancel_whenWorking_emitsCanceled() {
		var task = A2AServerTestFixtures.taskInState("t1", "c1", TaskState.WORKING);
		List<Event> events = new ArrayList<>();
		EventQueue queue = eventQueue("t1", events);
		try {
			RequestContext ctx = new RequestContext(null, "t1", "c1", task, null, this.callContext);
			StreamingAgentExecutor executor = new StreamingAgentExecutor(rc -> Flux.just(""));
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