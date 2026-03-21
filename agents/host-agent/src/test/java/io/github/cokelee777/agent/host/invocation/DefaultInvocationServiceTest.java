package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.RemoteAgentConnections;
import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.MemoryMode;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultInvocationServiceTest {

	@Mock
	private ConversationMemoryService conversationMemoryService;

	@Mock
	private LongTermMemoryService longTermMemoryService;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec callSpec;

	@Mock
	private RemoteAgentConnections connections;

	@Test
	void modeNone_noMemoryCalls() {
		BedrockMemoryProperties props = new BedrockMemoryProperties(MemoryMode.NONE, "mem", "strat", 10, 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service.invoke(new InvocationRequest("hello", null, null));

		assertThat(response.content()).isEqualTo("reply");
		verifyNoInteractions(conversationMemoryService);
		verifyNoInteractions(longTermMemoryService);
	}

	@Test
	void modeShortTerm_loadsHistoryAndSavesTurnsAfterChatClient() {
		BedrockMemoryProperties props = new BedrockMemoryProperties(MemoryMode.SHORT_TERM, "mem", "strat", 10, 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		when(conversationMemoryService.loadHistory(anyString(), anyString()))
			.thenReturn(List.of(new UserMessage("prev")));
		setupChatClientChain("ok");
		when(connections.getAgentDescriptions()).thenReturn("");

		service.invoke(new InvocationRequest("hi", "actor-1", "session-1"));

		// 저장은 ChatClient 이후에 순서대로 호출된다
		InOrder order = inOrder(chatClient, conversationMemoryService);
		order.verify(chatClient).prompt();
		order.verify(conversationMemoryService).appendUserTurn("actor-1", "session-1", "hi");
		order.verify(conversationMemoryService).appendAssistantTurn("actor-1", "session-1", "ok");
		verifyNoInteractions(longTermMemoryService);
	}

	@Test
	void modeLongTerm_retrievesRelevantAndSavesTurns() {
		BedrockMemoryProperties props = new BedrockMemoryProperties(MemoryMode.LONG_TERM, "mem", "strat", 10, 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		when(longTermMemoryService.retrieveRelevant(anyString(), anyString())).thenReturn(List.of("past info"));
		setupChatClientChain("response");
		when(connections.getAgentDescriptions()).thenReturn("");

		service.invoke(new InvocationRequest("query", "actor-1", "session-1"));

		verify(longTermMemoryService).retrieveRelevant("actor-1", "query");
		verify(conversationMemoryService, never()).loadHistory(anyString(), anyString());
		verify(conversationMemoryService).appendUserTurn(anyString(), anyString(), eq("query"));
	}

	@Test
	void chatClientFailure_noMemorySaved() {
		BedrockMemoryProperties props = new BedrockMemoryProperties(MemoryMode.BOTH, "mem", "strat", 10, 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		when(conversationMemoryService.loadHistory(anyString(), anyString())).thenReturn(List.of());
		when(longTermMemoryService.retrieveRelevant(anyString(), anyString())).thenReturn(List.of());
		when(connections.getAgentDescriptions()).thenReturn("");
		// ChatClient 호출 시 예외 발생
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(anyList())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenThrow(new RuntimeException("LLM error"));

		assertThatThrownBy(() -> service.invoke(new InvocationRequest("hi", "actor-1", "session-1")))
			.isInstanceOf(RuntimeException.class);

		verify(conversationMemoryService, never()).appendUserTurn(anyString(), anyString(), anyString());
		verify(conversationMemoryService, never()).appendAssistantTurn(anyString(), anyString(), anyString());
	}

	@Test
	void noSessionId_generatesNewSessionIdInResponse() {
		BedrockMemoryProperties props = new BedrockMemoryProperties(MemoryMode.NONE, "mem", "strat", 10, 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		setupChatClientChain("hi");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service.invoke(new InvocationRequest("hello", null, null));

		assertThat(response.sessionId()).isNotBlank();
		assertThat(response.actorId()).isNotBlank();
	}

	@Test
	void providedSessionId_returnsSameSessionIdInResponse() {
		BedrockMemoryProperties props = new BedrockMemoryProperties(MemoryMode.NONE, "mem", "strat", 10, 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service.invoke(new InvocationRequest("hi", "actor-1", "sess-42"));

		assertThat(response.sessionId()).isEqualTo("sess-42");
		assertThat(response.actorId()).isEqualTo("actor-1");
	}

	// ChatClient mock chain helper
	private void setupChatClientChain(String content) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(anyList())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callSpec);
		when(callSpec.content()).thenReturn(content);
	}

}
