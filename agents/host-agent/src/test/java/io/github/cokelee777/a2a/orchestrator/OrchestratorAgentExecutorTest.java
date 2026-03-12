package io.github.cokelee777.a2a.orchestrator;

import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrchestratorAgentExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class OrchestratorAgentExecutorTest {

	@Mock
	private ChatOrchestrator chatOrchestrator;

	@Mock
	private RequestContext requestContext;

	@Mock
	private EventQueue eventQueue;

	@Mock
	private TaskUpdater mockUpdater;

	private OrchestratorAgentExecutor executor;

	@BeforeEach
	void setUp() {
		executor = spy(new OrchestratorAgentExecutor(chatOrchestrator));
		doReturn(mockUpdater).when(executor).createTaskUpdater(any(), any());
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	private Message buildMessage(String text) {
		return new Message.Builder().messageId("m1").role(Message.Role.USER).parts(List.of(new TextPart(text))).build();
	}

	@Test
	void execute_withSessionHeader_usesHeaderValue() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(OrchestratorAgentExecutor.SESSION_HEADER, "session-abc");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		when(requestContext.getMessage()).thenReturn(buildMessage("텍스트"));
		when(chatOrchestrator.handle(any(ChatRequest.class))).thenReturn(new ChatResponse("응답"));

		executor.execute(requestContext, eventQueue);

		verify(chatOrchestrator)
			.handle(argThat(r -> "텍스트".equals(r.userMessage()) && "session-abc".equals(r.sessionId())));
	}

	@Test
	void execute_withBlankHeader_fallsBackToUuid() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(OrchestratorAgentExecutor.SESSION_HEADER, "   ");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		when(requestContext.getMessage()).thenReturn(buildMessage("텍스트"));
		when(chatOrchestrator.handle(any(ChatRequest.class))).thenReturn(new ChatResponse("응답"));

		executor.execute(requestContext, eventQueue);

		verify(chatOrchestrator).handle(any(ChatRequest.class));
	}

	@Test
	void execute_withNoServletContext_fallsBackToUuid() throws Exception {
		RequestContextHolder.resetRequestAttributes();
		when(requestContext.getMessage()).thenReturn(buildMessage("텍스트"));
		when(chatOrchestrator.handle(any(ChatRequest.class))).thenReturn(new ChatResponse("응답"));

		executor.execute(requestContext, eventQueue);

		verify(chatOrchestrator).handle(any(ChatRequest.class));
	}

	@Test
	void execute_success_callsAddArtifactAndComplete() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		when(requestContext.getMessage()).thenReturn(buildMessage("텍스트"));
		when(chatOrchestrator.handle(any(ChatRequest.class))).thenReturn(new ChatResponse("응답"));

		executor.execute(requestContext, eventQueue);

		verify(mockUpdater).startWork();
		verify(mockUpdater).addArtifact(any());
		verify(mockUpdater).complete();
		verify(mockUpdater, never()).fail();
	}

	@Test
	void execute_chatOrchestratorThrows_callsFail() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		when(requestContext.getMessage()).thenReturn(buildMessage("텍스트"));
		when(chatOrchestrator.handle(any(ChatRequest.class))).thenThrow(new RuntimeException("LLM error"));

		executor.execute(requestContext, eventQueue);

		verify(mockUpdater).startWork();
		verify(mockUpdater).fail();
		verify(mockUpdater, never()).complete();
	}

	@Test
	void cancel_callsEmitterCancel() throws Exception {
		executor.cancel(requestContext, eventQueue);

		verify(mockUpdater).cancel();
	}

}