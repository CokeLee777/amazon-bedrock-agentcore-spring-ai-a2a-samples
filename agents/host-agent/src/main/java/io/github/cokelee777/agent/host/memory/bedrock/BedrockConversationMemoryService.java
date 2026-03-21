package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
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
 * Amazon Bedrock AgentCore implementation of {@link ConversationMemoryService}.
 *
 * <p>
 * Uses {@code listEvents} to load short-term history and {@code createEvent} to append
 * USER and ASSISTANT turns. Each turn stores two events: one for the user message and one
 * for the assistant response.
 * </p>
 *
 * <p>
 * {@code maxResults} passed to {@code listEvents} is {@code shortTermMaxTurns * 2}
 * because one turn equals one USER event plus one ASSISTANT event.
 * </p>
 */
@Slf4j
public class BedrockConversationMemoryService implements ConversationMemoryService {

	private final BedrockAgentCoreClient client;

	private final BedrockMemoryProperties properties;

	private final AgentCoreEventToMessageConverter converter;

	/**
	 * Creates the service, asserting that {@code memoryId} is provided.
	 * @param client the Bedrock client
	 * @param properties the memory properties
	 * @param converter the event-to-message converter
	 */
	public BedrockConversationMemoryService(BedrockAgentCoreClient client, BedrockMemoryProperties properties,
			AgentCoreEventToMessageConverter converter) {
		Assert.hasText(properties.memoryId(),
				"aws.bedrock.agent-core.memory.memory-id must be set when mode is not 'none'");

		this.client = client;
		this.properties = properties;
		this.converter = converter;
	}

	@Override
	public List<Message> loadHistory(String actorId, String sessionId) {
		try {
			ListEventsRequest request = ListEventsRequest.builder()
				.memoryId(properties.memoryId())
				.actorId(actorId)
				.sessionId(sessionId)
				.includePayloads(true)
				.maxResults(properties.shortTermMaxTurns() * 2)
				.build();
			ListEventsResponse response = client.listEvents(request);
			List<Event> events = Objects.requireNonNullElse(response.events(), Collections.emptyList());
			return converter.toMessages(events);
		}
		catch (Exception ex) {
			log.error("Failed to load history for actor={} session={}", actorId, sessionId, ex);
			throw ex;
		}
	}

	@Override
	public void appendUserTurn(String actorId, String sessionId, String userText) {
		createEvent(actorId, sessionId, userText, Role.USER);
	}

	@Override
	public void appendAssistantTurn(String actorId, String sessionId, String assistantText) {
		createEvent(actorId, sessionId, assistantText, Role.ASSISTANT);
	}

	private void createEvent(String actorId, String sessionId, String text, Role role) {
		try {
			Conversational conversational = Conversational.builder().content(Content.fromText(text)).role(role).build();
			PayloadType payload = PayloadType.fromConversational(conversational);
			CreateEventRequest request = CreateEventRequest.builder()
				.memoryId(properties.memoryId())
				.actorId(actorId)
				.sessionId(sessionId)
				.eventTimestamp(Instant.now())
				.payload(payload)
				.build();
			client.createEvent(request);
		}
		catch (Exception ex) {
			log.error("Failed to append {} turn for actor={} session={}", role, actorId, sessionId, ex);
			throw ex;
		}
	}

}
