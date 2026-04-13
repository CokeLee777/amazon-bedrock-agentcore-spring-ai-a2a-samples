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
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link AgentExecutor} implementation that uses
 * {@link StreamingChatClientExecutorHandler} to run agent logic. Forwards each token from
 * the {@link Flux} as incremental {@link io.a2a.spec.TaskArtifactUpdateEvent} updates
 * (single artifact id, {@code append} / {@code lastChunk}) and completes the task when
 * the stream ends.
 */
@Slf4j
public class StreamingAgentExecutor implements AgentExecutor {

	private final ChatClient chatClient;

	private final StreamingChatClientExecutorHandler streamingHandler;

	private final AtomicReference<@Nullable Disposable> activeStream = new AtomicReference<>();

	public StreamingAgentExecutor(ChatClient chatClient, StreamingChatClientExecutorHandler streamingHandler) {
		this.chatClient = chatClient;
		this.streamingHandler = streamingHandler;
	}

	@Override
	public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
		Disposable subscription = null;
		CountDownLatch latch = new CountDownLatch(1);

		TaskUpdater updater = new TaskUpdater(context, eventQueue);
		AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();

		try {
			if (context.getTask() == null) {
				updater.submit();
			}
			updater.startWork();

			Flux<String> chunks = this.streamingHandler.executeStream(this.chatClient, context);
			ChunkedTextArtifactEmitter artifactEmitter = new ChunkedTextArtifactEmitter(updater, null);
			subscription = chunks.subscribe(artifactEmitter::onNext, e -> {
				failure.compareAndSet(null, e);
				latch.countDown();
			}, () -> {
				try {
					artifactEmitter.flushFinal();
				}
				finally {
					latch.countDown();
				}
			});

			this.activeStream.set(subscription);
			latch.await();

			Throwable e = failure.get();
			if (e != null) {
				if (Exceptions.isCancel(e)) {
					return;
				}
				throw new JSONRPCError(-32603, "Agent execution failed: " + e.getMessage(), null);
			}

			if (!artifactEmitter.hasEmittedArtifact()) {
				updater.addArtifact(List.of(new TextPart("")));
			}

			try {
				updater.complete();
			}
			catch (IllegalStateException ex) {
				log.debug("Skipping complete() — task already in a terminal state: {}", ex.getMessage());
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new JSONRPCError(-32603, "Agent execution interrupted: " + e.getMessage(), null);
		}
		catch (Exception e) {
			log.error("Error executing streaming agent task", e);
			throw new JSONRPCError(-32603, "Agent execution failed: " + e.getMessage(), null);
		}
		finally {
			if (subscription != null) {
				this.activeStream.compareAndSet(subscription, null);
			}
		}
	}

	/**
	 * Buffers one text chunk so the stream tail can be emitted with
	 * {@code lastChunk=true}.
	 * <p>
	 * Mutations run on the Reactor subscriber thread;
	 * {@link StreamingAgentExecutor#execute} reads {@link #hasEmittedArtifact()} only
	 * after {@link CountDownLatch#await()}, which synchronizes-with the terminal signal
	 * and makes field writes visible.
	 * </p>
	 * <p>
	 * If the constructor is given a {@code null} {@code artifactId}, a random
	 * {@link UUID} string is chosen once and reused for every
	 * {@link TaskUpdater#addArtifact} call for that stream (single logical artifact with
	 * {@code append}/{@code lastChunk}).
	 * </p>
	 */
	private static final class ChunkedTextArtifactEmitter {

		private final TaskUpdater updater;

		private final String artifactId;

		private boolean artifactEmitted;

		private @Nullable String pendingChunk;

		/**
		 * @param updater the task updater that enqueues A2A artifact events; must not be
		 * {@code null}
		 * @param artifactId stable id for incremental artifact updates; if {@code null},
		 * a new random {@link UUID} string is generated and used for the lifetime of this
		 * emitter
		 */
		ChunkedTextArtifactEmitter(TaskUpdater updater, @Nullable String artifactId) {
			this.updater = updater;
			this.artifactId = Objects.requireNonNullElse(artifactId, UUID.randomUUID().toString());
		}

		/**
		 * Queues the next token: emits any previously buffered value as a non-final
		 * chunk, then stores this value for a later {@link #flushFinal()}.
		 * @param chunk nullable token from the model stream
		 */
		void onNext(String chunk) {
			String pending = this.pendingChunk;
			if (pending != null) {
				emitArtifactPart(pending, false);
			}
			this.pendingChunk = chunk;
		}

		/**
		 * Emits the last buffered chunk with {@code lastChunk=true}, if any.
		 */
		void flushFinal() {
			String pending = this.pendingChunk;
			this.pendingChunk = null;
			if (pending != null) {
				emitArtifactPart(pending, true);
			}
		}

		/**
		 * Whether at least one {@code TaskArtifactUpdate} has been enqueued.
		 * @return true after the first successful {@link TaskUpdater#addArtifact} call
		 */
		boolean hasEmittedArtifact() {
			return this.artifactEmitted;
		}

		private void emitArtifactPart(String text, boolean lastChunk) {
			boolean append = this.artifactEmitted;
			this.updater.addArtifact(List.of(new TextPart(text)), this.artifactId, null, null, append, lastChunk);
			this.artifactEmitted = true;
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

		Disposable disposable = this.activeStream.getAndSet(null);
		if (disposable != null) {
			disposable.dispose();
		}

		TaskUpdater updater = new TaskUpdater(context, eventQueue);
		updater.cancel();
	}

}
