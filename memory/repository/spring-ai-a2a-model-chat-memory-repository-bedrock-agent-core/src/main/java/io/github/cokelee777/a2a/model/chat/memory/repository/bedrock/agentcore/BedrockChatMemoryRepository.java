package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Amazon Bedrock AgentCore implementation of {@link ChatMemoryRepository}.
 *
 * <p>
 * Uses {@code listEvents} to load conversation history and {@code createEvent} to append
 * new messages (append-only, same semantics as {@code JdbcChatMemoryRepository.saveAll}).
 * </p>
 *
 * <p>
 * {@code conversationId} format: {@code "actorId:sessionId"}.
 * </p>
 *
 * <p>
 * Pagination is not handled: only the most recent {@code maxTurns * 2} events are loaded.
 * {@code findConversationIds} and {@code deleteByConversationId} throw
 * {@link UnsupportedOperationException} because Bedrock AgentCore Memory does not expose
 * listing or deletion APIs for events.
 * </p>
 */
@Slf4j
public class BedrockChatMemoryRepository implements ChatMemoryRepository {

	private static final String DELIMITER = ":";

	private final BedrockAgentCoreClient client;

	private final BedrockChatMemoryRepositoryConfig config;

	private final BedrockAgentCoreEventToMessageConverter converter;

	/**
	 * Creates a new repository.
	 * @param client the Bedrock AgentCore data-plane client
	 * @param config the repository configuration
	 * @param converter converts AgentCore events to Spring AI messages
	 */
	public BedrockChatMemoryRepository(BedrockAgentCoreClient client, BedrockChatMemoryRepositoryConfig config,
			BedrockAgentCoreEventToMessageConverter converter) {
		this.client = client;
		this.config = config;
		this.converter = converter;
	}

	/**
	 * Not supported: Bedrock AgentCore Memory has no API to list conversation
	 * identifiers.
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public List<String> findConversationIds() {
		throw new UnsupportedOperationException(
				"findConversationIds is not supported by Bedrock AgentCore Memory (no list API)");
	}

	/**
	 * Loads conversation history for the given {@code conversationId}.
	 * @param conversationId in the format {@code "actorId:sessionId"}
	 * @return messages sorted by eventTimestamp ascending (oldest first)
	 */
	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId must not be empty");

		String[] parts = parseConversationId(conversationId);
		try {
			ListEventsRequest request = ListEventsRequest.builder()
				.memoryId(config.memoryId())
				.actorId(parts[0])
				.sessionId(parts[1])
				.includePayloads(true)
				.maxResults(config.maxTurns() * 2)
				.build();
			ListEventsResponse response = client.listEvents(request);
			List<Event> events = Objects.requireNonNullElse(response.events(), Collections.emptyList());
			return converter.toMessages(events);
		}
		catch (Exception ex) {
			log.error("Failed to load history for conversationId={}", conversationId, ex);
			throw ex;
		}
	}

	/**
	 * Appends each message as a new event in Bedrock AgentCore Memory (append-only,
	 * consistent with {@code JdbcChatMemoryRepository} semantics).
	 * @param conversationId in the format {@code "actorId:sessionId"}
	 * @param messages the new messages to append; ignored if empty
	 */
	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId must not be empty");
		Assert.notNull(messages, "messages must not be null");
		Assert.noNullElements(messages, "messages must contain non-null elements");

		String[] parts = parseConversationId(conversationId);
		for (Message message : messages) {
			try {
				Role role = (message instanceof UserMessage) ? Role.USER : Role.ASSISTANT;
				Conversational conversational = Conversational.builder()
					.content(Content.fromText(message.getText()))
					.role(role)
					.build();
				CreateEventRequest request = CreateEventRequest.builder()
					.memoryId(config.memoryId())
					.actorId(parts[0])
					.sessionId(parts[1])
					.eventTimestamp(Instant.now())
					.payload(PayloadType.fromConversational(conversational))
					.build();
				client.createEvent(request);
			}
			catch (Exception ex) {
				log.error("Failed to save message for conversationId={}", conversationId, ex);
				throw ex;
			}
		}
	}

	/**
	 * Not supported: Bedrock AgentCore Memory has no API to delete events.
	 * @param conversationId the conversation identifier
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public void deleteByConversationId(String conversationId) {
		throw new UnsupportedOperationException(
				"deleteByConversationId is not supported by Bedrock AgentCore Memory (no delete API); conversationId="
						+ conversationId);
	}

	private String[] parseConversationId(String conversationId) {
		String[] parts = conversationId.split(DELIMITER, 2);
		Assert.isTrue(parts.length == 2,
				"conversationId must be in format 'actorId:sessionId', got: " + conversationId);
		return parts;
	}

}
