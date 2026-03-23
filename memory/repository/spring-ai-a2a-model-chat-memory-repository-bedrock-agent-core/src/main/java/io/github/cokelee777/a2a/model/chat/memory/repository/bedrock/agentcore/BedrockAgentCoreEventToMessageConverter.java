package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Converts Bedrock AgentCore {@link Event} objects to Spring AI {@link Message} objects.
 *
 * <p>
 * Only {@code Conversational} payload events with {@code USER} or {@code ASSISTANT} roles
 * are converted. Events with unknown or missing payloads are silently skipped. The
 * returned list is sorted by {@code eventTimestamp} ascending.
 * </p>
 *
 * <p>
 * This class is not annotated with {@code @Component}. It is registered as a
 * {@code @Bean} inside
 * {@link io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.autoconfigure.BedrockChatMemoryAutoConfiguration}.
 * </p>
 */
public class BedrockAgentCoreEventToMessageConverter {

	/**
	 * Converts a list of {@link Event} objects to Spring AI {@link Message} objects,
	 * sorted by {@code eventTimestamp} ascending.
	 * @param events the raw events from AgentCore Memory
	 * @return sorted list of messages; empty list if input is empty or all events are
	 * skipped
	 */
	public List<Message> toMessages(List<Event> events) {
		Assert.notNull(events, "events must not be null");

		return events.stream()
			.sorted(Comparator.comparing(Event::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
			.mapMulti((Event event, Consumer<Message> downstream) -> {
				Message message = toMessage(event);
				if (message != null) {
					downstream.accept(message);
				}
			})
			.toList();
	}

	/**
	 * Converts a single {@link Event} when it carries a supported conversational payload.
	 * @param event the AgentCore event
	 * @return a {@link Message}, or {@code null} when the event should be skipped
	 */
	private @Nullable Message toMessage(Event event) {
		Assert.notNull(event, "event must not be null");

		if (event.payload() == null || event.payload().isEmpty()) {
			return null;
		}
		PayloadType payload = event.payload().getFirst();
		if (payload.conversational() == null) {
			return null;
		}
		Conversational conversational = payload.conversational();
		String text = conversational.content() != null ? conversational.content().text() : "";
		Role role = conversational.role();
		if (Role.USER.equals(role)) {
			return new UserMessage(Objects.requireNonNullElse(text, ""));
		}
		if (Role.ASSISTANT.equals(role)) {
			return new AssistantMessage(Objects.requireNonNullElse(text, ""));
		}
		return null;
	}

}
