package io.github.cokelee777.a2a.agent.common;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendConfiguration;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import io.github.cokelee777.a2a.agent.common.util.TextExtractor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Utility class for sending a {@link Message} to a downstream A2A agent and collecting
 * the full text response.
 *
 * <p>
 * All methods are static; this class is not meant to be instantiated.
 * </p>
 */
@Slf4j
public class A2ATransport {

	/**
	 * Sends {@code message} to the downstream agent identified by {@code agentCard} and
	 * waits up to 60 seconds for the response.
	 *
	 * <p>
	 * Returns an error message string when the agent task fails, the timeout elapses, or
	 * any exception occurs.
	 * </p>
	 * @param agentCard the target agent's {@link AgentCard}
	 * @param message the A2A {@link Message} to send
	 * @return the concatenated text from all response artifacts, or an error message if
	 * the call fails
	 */
	public static String send(AgentCard agentCard, Message message) {
		try {
			AtomicReference<String> result = new AtomicReference<>("");
			BiConsumer<ClientEvent, AgentCard> consumer = (event, card) -> {
				if (event instanceof TaskEvent taskEvent) {
					Task task = taskEvent.getTask();
					log.debug("Received task response: status={}", task.getStatus().state());

					if (TaskState.FAILED.equals(task.getStatus().state())) {
						return;
					}
					result.set(TextExtractor.extractTextFromTask(task));
				}
			};

			// Create client with consumer via builder
			ClientConfig clientConfig = new ClientConfig.Builder().setAcceptedOutputModes(List.of("text")).build();
			Client client = Client.builder(agentCard)
				.clientConfig(clientConfig)
				.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
				.addConsumers(List.of(consumer))
				.build();

			client.sendMessage(message);

			log.debug("Agent '{}' response: {}", agentCard.name(), result.get());
			return result.get();
		}
		catch (Exception e) {
			log.error("Error sending message to agent '{}': {}", agentCard.name(), e.getMessage());
			return String.format("Error communicating with agent '%s': %s", agentCard.name(), e.getMessage());
		}
	}

	/**
	 * Sends {@code message} to the downstream agent via SSE streaming and waits up to 60
	 * seconds for the full response.
	 * @param agentCard the target agent's {@link AgentCard}
	 * @param message the A2A {@link Message} to send
	 * @return the concatenated text from all response artifacts, or an error message if
	 * the call fails
	 */
	public static String sendStream(AgentCard agentCard, Message message) {
		try {
			CompletableFuture<String> future = new CompletableFuture<>();
			StringBuilder accumulated = new StringBuilder();

			MessageSendParams params = new MessageSendParams(message,
					new MessageSendConfiguration(List.of("text"), null, null, null), null);

			JSONRPCTransport transport = new JSONRPCTransport(agentCard);
			transport.sendMessageStreaming(params, event -> {
				if (event instanceof TaskArtifactUpdateEvent taskArtifactUpdateEvent) {
					taskArtifactUpdateEvent.getArtifact().parts().forEach(part -> {
						if (part instanceof TextPart textPart) {
							accumulated.append(textPart.getText());
						}
					});
				}
				else if (event instanceof TaskStatusUpdateEvent status && status.isFinal()) {
					future.complete(accumulated.toString());
				}
			}, throwable -> {
				// SSEEventListener calls this handler with null on normal stream
				// completion (onComplete), and with the actual exception on error.
				// TCP-level failures (e.g. connection refused) are not propagated
				// by the SDK — handled via the future timeout below.
				if (throwable != null) {
					future.completeExceptionally(throwable);
				}
			}, null);

			String result = future.get(60, TimeUnit.SECONDS);
			log.debug("Agent '{}' streaming response: {}", agentCard.name(), result);
			return result;
		}
		catch (Exception e) {
			log.error("Error streaming from agent '{}': {}", agentCard.name(), e.getMessage());
			return String.format("Error communicating with agent '%s': %s", agentCard.name(), e.getMessage());
		}
	}

}
