package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.MemoryMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockConversationMemoryServiceTest {

	@Mock
	private BedrockAgentCoreClient client;

	private BedrockMemoryProperties properties;

	private AgentCoreEventToMessageConverter converter;

	private BedrockConversationMemoryService service;

	@BeforeEach
	void setUp() {
		properties = new BedrockMemoryProperties(MemoryMode.BOTH, "mem-1", "strategy-1", 5, 4);
		converter = new AgentCoreEventToMessageConverter();
		service = new BedrockConversationMemoryService(client, properties, converter);
	}

	@Test
	void loadHistory_callsListEventsWithCorrectParams() {
		ListEventsResponse response = ListEventsResponse.builder().events(List.of()).build();
		when(client.listEvents(any(ListEventsRequest.class))).thenReturn(response);

		service.loadHistory("actor-1", "session-1");

		ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(client).listEvents(captor.capture());
		ListEventsRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo("mem-1");
		assertThat(req.actorId()).isEqualTo("actor-1");
		assertThat(req.sessionId()).isEqualTo("session-1");
		assertThat(req.includePayloads()).isTrue();
		// shortTermMaxTurns=5 → maxResults=10 (5턴 × 2이벤트/턴)
		assertThat(req.maxResults()).isEqualTo(10);
	}

	@Test
	void loadHistory_convertsEventsToMessages() {
		Event userEvent = buildEvent("hi", Role.USER, Instant.ofEpochSecond(1000));
		Event assistantEvent = buildEvent("hello", Role.ASSISTANT, Instant.ofEpochSecond(2000));
		ListEventsResponse response = ListEventsResponse.builder().events(List.of(assistantEvent, userEvent)).build();
		when(client.listEvents(any(ListEventsRequest.class))).thenReturn(response);

		List<Message> messages = service.loadHistory("actor-1", "session-1");

		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void appendUserTurn_callsCreateEventWithUserRole() {
		when(client.createEvent(any(CreateEventRequest.class))).thenReturn(CreateEventResponse.builder().build());

		service.appendUserTurn("actor-1", "session-1", "hello");

		ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(client).createEvent(captor.capture());
		CreateEventRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo("mem-1");
		assertThat(req.actorId()).isEqualTo("actor-1");
		assertThat(req.sessionId()).isEqualTo("session-1");
		assertThat(req.payload()).hasSize(1);
		assertThat(req.payload().get(0).conversational().role()).isEqualTo(Role.USER);
		assertThat(req.payload().get(0).conversational().content().text()).isEqualTo("hello");
	}

	@Test
	void appendAssistantTurn_callsCreateEventWithAssistantRole() {
		when(client.createEvent(any(CreateEventRequest.class))).thenReturn(CreateEventResponse.builder().build());

		service.appendAssistantTurn("actor-1", "session-1", "got it");

		ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(client).createEvent(captor.capture());
		CreateEventRequest req = captor.getValue();
		assertThat(req.payload().get(0).conversational().role()).isEqualTo(Role.ASSISTANT);
	}

	private Event buildEvent(String text, Role role, Instant timestamp) {
		Conversational conv = Conversational.builder().content(Content.fromText(text)).role(role).build();
		return Event.builder().eventTimestamp(timestamp).payload(List.of(PayloadType.fromConversational(conv))).build();
	}

}
