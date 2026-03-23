package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockAgentCoreEventToMessageConverterTest {

	private BedrockAgentCoreEventToMessageConverter converter;

	@BeforeEach
	void setUp() {
		converter = new BedrockAgentCoreEventToMessageConverter();
	}

	@Test
	void emptyList_returnsEmpty() {
		assertThat(converter.toMessages(List.of())).isEmpty();
	}

	@Test
	void userEvent_returnsUserMessage() {
		Event event = userEvent("hello", Instant.now());
		List<Message> messages = converter.toMessages(List.of(event));
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("hello");
	}

	@Test
	void assistantEvent_returnsAssistantMessage() {
		Event event = assistantEvent("hi there", Instant.now());
		List<Message> messages = converter.toMessages(List.of(event));
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("hi there");
	}

	@Test
	void multipleEvents_sortedByTimestamp() {
		Instant t1 = Instant.ofEpochSecond(1000);
		Instant t2 = Instant.ofEpochSecond(2000);
		Event assistant = assistantEvent("reply", t2);
		Event user = userEvent("question", t1);
		List<Message> messages = converter.toMessages(List.of(assistant, user));
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void unknownPayload_isSkipped() {
		Event event = Event.builder().eventTimestamp(Instant.now()).build();
		assertThat(converter.toMessages(List.of(event))).isEmpty();
	}

	private Event userEvent(String text, Instant timestamp) {
		return buildEvent(text, Role.USER, timestamp);
	}

	private Event assistantEvent(String text, Instant timestamp) {
		return buildEvent(text, Role.ASSISTANT, timestamp);
	}

	private Event buildEvent(String text, Role role, Instant timestamp) {
		Conversational conversational = Conversational.builder().content(Content.fromText(text)).role(role).build();
		PayloadType payload = PayloadType.fromConversational(conversational);
		return Event.builder().eventTimestamp(timestamp).payload(List.of(payload)).build();
	}

}
