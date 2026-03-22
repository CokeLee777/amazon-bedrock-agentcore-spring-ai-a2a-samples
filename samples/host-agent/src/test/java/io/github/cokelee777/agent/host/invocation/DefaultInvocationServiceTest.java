package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.RemoteAgentConnections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultInvocationServiceTest {

	@Mock
	private ChatMemoryRepository chatMemoryRepository;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec callSpec;

	@Mock
	private RemoteAgentConnections connections;

	@Test
	void invoke_loadsHistoryAndSavesNewMessagesAfterLlmCall() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of(new UserMessage("prev")));
		setupChatClientChain("ok");
		when(connections.getAgentDescriptions()).thenReturn("");

		service().invoke(new InvocationRequest("hi", "actor-1", "session-1"));

		InOrder order = inOrder(chatClient, chatMemoryRepository);
		order.verify(chatClient).prompt();
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
		order.verify(chatMemoryRepository).saveAll(eq("actor-1:session-1"), captor.capture());
		List<Message> saved = captor.getValue();
		assertThat(saved).hasSize(2);
		assertThat(saved.get(0)).isInstanceOf(UserMessage.class);
		assertThat(saved.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void invoke_savesOnlyTwoNewMessages() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		service().invoke(new InvocationRequest("hello", "a", "s"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
		verify(chatMemoryRepository).saveAll(anyString(), captor.capture());
		assertThat(captor.getValue()).hasSize(2);
	}

	@Test
	void invoke_nullActorId_generatesUuid() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("hi");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service().invoke(new InvocationRequest("hello", null, null));

		assertThat(response.actorId()).isNotBlank();
		assertThat(response.sessionId()).isNotBlank();
	}

	@Test
	void invoke_providedIds_returnsSameIds() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service().invoke(new InvocationRequest("hi", "actor-1", "sess-42"));

		assertThat(response.actorId()).isEqualTo("actor-1");
		assertThat(response.sessionId()).isEqualTo("sess-42");
	}

	@Test
	void invoke_alwaysReturnsNonNullIds() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("hi");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service().invoke(new InvocationRequest("hello", null, null));

		assertThat(response.sessionId()).isNotNull();
		assertThat(response.actorId()).isNotNull();
	}

	@Test
	void invoke_llmFailure_noMemorySaved() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		when(connections.getAgentDescriptions()).thenReturn("");
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(anyList())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenThrow(new RuntimeException("LLM error"));

		assertThatThrownBy(() -> service().invoke(new InvocationRequest("hi", "actor-1", "session-1")))
			.isInstanceOf(RuntimeException.class);

		verify(chatMemoryRepository, never()).saveAll(anyString(), any());
	}

	private DefaultInvocationService service() {
		return new DefaultInvocationService(chatClient, connections, chatMemoryRepository);
	}

	private void setupChatClientChain(String content) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(anyList())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callSpec);
		when(callSpec.content()).thenReturn(content);
	}

}
