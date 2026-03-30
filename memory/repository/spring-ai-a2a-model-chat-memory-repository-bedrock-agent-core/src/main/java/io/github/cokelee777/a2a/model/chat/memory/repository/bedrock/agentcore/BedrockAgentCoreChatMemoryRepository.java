package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteMemoryRecordRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListSessionsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import software.amazon.awssdk.services.bedrockagentcore.model.SearchCriteria;
import software.amazon.awssdk.services.bedrockagentcore.model.SessionSummary;

/**
 * {@link AdvancedBedrockAgentCoreChatMemoryRepository} backed by Amazon Bedrock AgentCore
 * Memory. {@code conversationId} is the Bedrock {@code sessionId}; {@code actorId} comes
 * from {@link BedrockAgentCoreChatMemoryConfig}.
 *
 * @author Chaemin Lee
 * @see BedrockAgentCoreChatMemoryConfig
 */
public final class BedrockAgentCoreChatMemoryRepository implements AdvancedBedrockAgentCoreChatMemoryRepository {

	private static final Logger logger = LoggerFactory.getLogger(BedrockAgentCoreChatMemoryRepository.class);

	/** Metadata key storing Bedrock event id on round-tripped messages. */
	public static final String METADATA_KEY_EVENT_ID = "bedrockEventId";

	private final BedrockAgentCoreChatMemoryConfig config;

	private final BedrockAgentCoreClient bedrockAgentCoreClient;

	/**
	 * Creates a repository from config.
	 * @param config configuration including client, memory id, actor, limits
	 */
	public BedrockAgentCoreChatMemoryRepository(BedrockAgentCoreChatMemoryConfig config) {
		Assert.notNull(config, "config must not be null");
		this.config = config;
		this.bedrockAgentCoreClient = config.getBedrockAgentCoreClient();
	}

	@Override
	public List<String> findConversationIds() {
		ListSessionsRequest request = ListSessionsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.actorId(this.config.getActorId())
			.maxResults(this.config.getMaxResults())
			.build();
		try {
			return this.bedrockAgentCoreClient.listSessionsPaginator(request)
				.sessionSummaries()
				.stream()
				.limit(this.config.getMaxResults())
				.map(SessionSummary::sessionId)
				.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to list conversation ids", e);
		}
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId must not be empty");
		return findEventsByConversationId(conversationId,
				Comparator.comparing(Event::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
			.stream()
			.map(BedrockAgentCoreChatMemoryRepository::toMessage)
			.toList();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId must not be empty");
		Assert.notNull(messages, "messages must not be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		deleteByConversationId(conversationId);
		for (Message message : messages) {
			CreateEventRequest request = CreateEventRequest.builder()
				.memoryId(this.config.getMemoryId())
				.actorId(this.config.getActorId())
				.sessionId(conversationId)
				.eventTimestamp(Instant.now())
				.payload(toPayload(message))
				.build();
			try {
				this.bedrockAgentCoreClient.createEvent(request);
			}
			catch (Exception e) {
				throw new IllegalStateException("Failed to save event for conversationId=" + conversationId, e);
			}
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId must not be empty");
		List<Event> events = findEventsByConversationId(conversationId, null);
		for (Event event : events) {
			DeleteEventRequest request = DeleteEventRequest.builder()
				.memoryId(this.config.getMemoryId())
				.actorId(this.config.getActorId())
				.sessionId(conversationId)
				.eventId(event.eventId())
				.build();
			try {
				this.bedrockAgentCoreClient.deleteEvent(request);
			}
			catch (Exception e) {
				throw new IllegalStateException("Failed to delete event for conversationId=" + conversationId, e);
			}
		}
	}

	private List<Event> findEventsByConversationId(String conversationId, @Nullable Comparator<Event> comparator) {
		ListEventsRequest request = ListEventsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.actorId(this.config.getActorId())
			.sessionId(conversationId)
			.includePayloads(true)
			.build();
		try {
			Stream<Event> stream = this.bedrockAgentCoreClient.listEventsPaginator(request).events().stream();
			if (comparator != null) {
				stream = stream.sorted(comparator);
			}
			return stream.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to list events for conversationId=" + conversationId, e);
		}
	}

	private static PayloadType toPayload(Message message) {
		Role role = switch (message.getMessageType()) {
			case ASSISTANT -> Role.ASSISTANT;
			case TOOL -> Role.TOOL;
			case SYSTEM -> Role.OTHER;
			default -> Role.USER;
		};
		return PayloadType.fromConversational(
				Conversational.builder().role(role).content(Content.fromText(message.getText())).build());
	}

	private static Message toMessage(Event event) {
		String text = "";
		Role role = Role.USER;
		if (event.hasPayload()) {
			for (PayloadType payload : event.payload()) {
				Conversational conv = payload.conversational();
				if (conv != null) {
					Content content = conv.content();
					if (content != null && content.text() != null) {
						text = content.text();
					}
					if (conv.role() != null) {
						role = conv.role();
					}
					break;
				}
			}
		}
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(METADATA_KEY_EVENT_ID, event.eventId());
		return switch (role) {
			case ASSISTANT -> AssistantMessage.builder().content(text).properties(metadata).build();
			case USER -> UserMessage.builder().text(text).metadata(metadata).build();
			case TOOL -> ToolResponseMessage.builder().responses(List.of()).metadata(metadata).build();
			case OTHER -> SystemMessage.builder().text(text).metadata(metadata).build();
			default -> {
				logger.warn("Unknown role '{}', defaulting to UserMessage", role);
				yield UserMessage.builder().text(text).metadata(metadata).build();
			}
		};
	}

	@Override
	public List<MemoryRecordSummary> retrieveMemoryRecords(String namespace, String searchQuery) {
		Assert.hasText(namespace, "namespace must not be empty");
		Assert.hasText(searchQuery, "searchQuery must not be empty");
		RetrieveMemoryRecordsRequest request = RetrieveMemoryRecordsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.namespace(namespace)
			.searchCriteria(SearchCriteria.builder().searchQuery(searchQuery).build())
			.build();
		try {
			return this.bedrockAgentCoreClient.retrieveMemoryRecordsPaginator(request)
				.memoryRecordSummaries()
				.stream()
				.limit(this.config.getMaxResults())
				.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to retrieve memory records for namespace=" + namespace, e);
		}
	}

	@Override
	public List<MemoryRecordSummary> listMemoryRecords(String namespace) {
		Assert.hasText(namespace, "namespace must not be empty");
		ListMemoryRecordsRequest request = ListMemoryRecordsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.namespace(namespace)
			.build();
		try {
			return this.bedrockAgentCoreClient.listMemoryRecordsPaginator(request)
				.memoryRecordSummaries()
				.stream()
				.limit(this.config.getMaxResults())
				.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to list memory records for namespace=" + namespace, e);
		}
	}

	@Override
	public void deleteMemoryRecord(String memoryRecordId) {
		Assert.hasText(memoryRecordId, "memoryRecordId must not be empty");
		DeleteMemoryRecordRequest request = DeleteMemoryRecordRequest.builder()
			.memoryId(this.config.getMemoryId())
			.memoryRecordId(memoryRecordId)
			.build();
		try {
			this.bedrockAgentCoreClient.deleteMemoryRecord(request);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to delete memory record: " + memoryRecordId, e);
		}
	}

	/**
	 * Creates a new builder.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** Builder for {@link BedrockAgentCoreChatMemoryRepository}. */
	public static final class Builder {

		private @Nullable BedrockAgentCoreClient bedrockAgentCoreClient;

		private @Nullable String memoryId;

		private String actorId = BedrockAgentCoreChatMemoryConfig.DEFAULT_ACTOR_ID;

		private int maxResults = BedrockAgentCoreChatMemoryConfig.DEFAULT_MAX_RESULTS;

		private Builder() {
		}

		public Builder bedrockAgentCoreClient(BedrockAgentCoreClient bedrockAgentCoreClient) {
			this.bedrockAgentCoreClient = bedrockAgentCoreClient;
			return this;
		}

		public Builder memoryId(String memoryId) {
			this.memoryId = memoryId;
			return this;
		}

		public Builder actorId(String actorId) {
			this.actorId = actorId;
			return this;
		}

		public Builder maxResults(int maxResults) {
			this.maxResults = maxResults;
			return this;
		}

		public BedrockAgentCoreChatMemoryRepository build() {
			Assert.notNull(this.bedrockAgentCoreClient, "bedrockAgentCoreClient must not be null");
			Assert.hasText(this.memoryId, "memoryId must not be empty");
			Assert.hasText(this.actorId, "actorId must not be empty");
			Assert.isTrue(this.maxResults > 0 && this.maxResults <= 100,
					"maxResults must be between 1 and 100 (inclusive)");
			BedrockAgentCoreChatMemoryConfig cfg = BedrockAgentCoreChatMemoryConfig.builder()
				.bedrockAgentCoreClient(this.bedrockAgentCoreClient)
				.memoryId(this.memoryId)
				.actorId(this.actorId)
				.maxResults(this.maxResults)
				.build();
			return new BedrockAgentCoreChatMemoryRepository(cfg);
		}

	}

}
