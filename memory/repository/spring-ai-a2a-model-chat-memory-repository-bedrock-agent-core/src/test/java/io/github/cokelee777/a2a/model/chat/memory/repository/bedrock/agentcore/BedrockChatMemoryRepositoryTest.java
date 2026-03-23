package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

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
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockChatMemoryRepositoryTest {

	private static final String MEMORY_ID = "mem-test";

	private static final String ACTOR_ID = "actor-1";

	private static final String SESSION_ID = "sess-1";

	private static final String CONVERSATION_ID = ACTOR_ID + ":" + SESSION_ID;

	@Mock
	private BedrockAgentCoreClient client;

	private BedrockChatMemoryRepository repository;

	@BeforeEach
	void setUp() {
		BedrockChatMemoryRepositoryConfig config = BedrockChatMemoryRepositoryConfig.builder()
			.memoryId(MEMORY_ID)
			.maxTurns(10)
			.build();
		BedrockAgentCoreEventToMessageConverter converter = new BedrockAgentCoreEventToMessageConverter();
		repository = new BedrockChatMemoryRepository(client, config, converter);
	}

	@Test
	void findByConversationId_parsesCompositeKeyAndCallsListEvents() {
		when(client.listEvents(isA(ListEventsRequest.class)))
			.thenReturn(ListEventsResponse.builder().events(List.of()).build());

		repository.findByConversationId(CONVERSATION_ID);

		ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(client).listEvents(captor.capture());
		ListEventsRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo(MEMORY_ID);
		assertThat(req.actorId()).isEqualTo(ACTOR_ID);
		assertThat(req.sessionId()).isEqualTo(SESSION_ID);
	}

	@Test
	void findByConversationId_maxResultsIsMaxTurnsTimesTwo() {
		when(client.listEvents(isA(ListEventsRequest.class)))
			.thenReturn(ListEventsResponse.builder().events(List.of()).build());

		repository.findByConversationId(CONVERSATION_ID);

		ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(client).listEvents(captor.capture());
		assertThat(captor.getValue().maxResults()).isEqualTo(20); // 10 * 2
	}

	@Test
	void findByConversationId_returnsSortedMessages() {
		Instant t1 = Instant.ofEpochSecond(1000);
		Instant t2 = Instant.ofEpochSecond(2000);
		Event userEvent = buildEvent("hi", Role.USER, t1);
		Event assistantEvent = buildEvent("hello", Role.ASSISTANT, t2);
		// 역순으로 반환해도 오름차순 정렬되어야 한다
		when(client.listEvents(isA(ListEventsRequest.class)))
			.thenReturn(ListEventsResponse.builder().events(List.of(assistantEvent, userEvent)).build());

		List<Message> messages = repository.findByConversationId(CONVERSATION_ID);

		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void saveAll_callsCreateEventForEachMessage() {
		List<Message> messages = List.of(new UserMessage("hello"), new AssistantMessage("hi"));

		repository.saveAll(CONVERSATION_ID, messages);

		verify(client, times(2)).createEvent(isA(CreateEventRequest.class));
	}

	@Test
	void saveAll_userMessage_setsRoleUser() {
		repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("test")));

		ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(client).createEvent(captor.capture());
		CreateEventRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo(MEMORY_ID);
		assertThat(req.actorId()).isEqualTo(ACTOR_ID);
		assertThat(req.sessionId()).isEqualTo(SESSION_ID);
		assertThat(req.payload().getFirst().conversational().role()).isEqualTo(Role.USER);
	}

	@Test
	void saveAll_emptyList_noApiCalls() {
		repository.saveAll(CONVERSATION_ID, List.of());

		verify(client, never()).createEvent(isA(CreateEventRequest.class));
	}

	@Test
	void deleteByConversationId_noApiCalls() {
		repository.deleteByConversationId(CONVERSATION_ID);

		verify(client, never()).createEvent(isA(CreateEventRequest.class));
		verify(client, never()).listEvents(isA(ListEventsRequest.class));
	}

	@Test
	void findConversationIds_returnsEmpty() {
		assertThat(repository.findConversationIds()).isEmpty();
	}

	private Event buildEvent(String text, Role role, Instant timestamp) {
		Conversational conv = Conversational.builder().content(Content.fromText(text)).role(role).build();
		return Event.builder().eventTimestamp(timestamp).payload(List.of(PayloadType.fromConversational(conv))).build();
	}

}
