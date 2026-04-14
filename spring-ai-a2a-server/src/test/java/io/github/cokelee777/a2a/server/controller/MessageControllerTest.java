package io.github.cokelee777.a2a.server.controller;

import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.StreamingEventKind;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.github.cokelee777.a2a.server.support.A2AServerTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.SubmissionPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice tests for {@link MessageController}.
 */
@WebMvcTest(controllers = MessageController.class)
class MessageControllerTest {

	@MockitoBean
	private RequestHandler requestHandler;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sendMessage_delegatesToRequestHandler_andReturnsResult() throws Exception {
		Task task = A2AServerTestFixtures.taskInState("t-1", "c-1", TaskState.COMPLETED);
		when(this.requestHandler.onMessageSend(any(MessageSendParams.class), any())).thenReturn(task);

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-1",
				  "method": "message/send",
				  "params": {
				    "message": {
				      "kind": "message",
				      "messageId": "m-1",
				      "role": "user",
				      "parts": [{"kind": "text", "text": "hello"}]
				    }
				  }
				}
				""";

		this.mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("rpc-1"))
			.andExpect(jsonPath("$.result.id").value("t-1"));

		verify(this.requestHandler).onMessageSend(any(MessageSendParams.class), any());
	}

	@Test
	void sendMessage_jsonRpcErrorFromHandler_returnedAsJsonRpcErrorResponse() throws Exception {
		when(this.requestHandler.onMessageSend(any(MessageSendParams.class), any()))
			.thenThrow(new TaskNotFoundError(TaskNotFoundError.DEFAULT_CODE, "upstream", null));

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-err",
				  "method": "message/send",
				  "params": {
				    "message": {
				      "kind": "message",
				      "messageId": "m-err",
				      "role": "user",
				      "parts": [{"kind": "text", "text": "x"}]
				    }
				  }
				}
				""";

		this.mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("rpc-err"))
			.andExpect(jsonPath("$.error.code").value(-32001))
			.andExpect(jsonPath("$.error.message").value("upstream"));
	}

	@Test
	void sendMessage_unexpectedException_returnedAsInternalJsonRpcError() throws Exception {
		when(this.requestHandler.onMessageSend(any(MessageSendParams.class), any()))
			.thenThrow(new IllegalStateException("boom"));

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-wrap",
				  "method": "message/send",
				  "params": {
				    "message": {
				      "kind": "message",
				      "messageId": "m-wrap",
				      "role": "user",
				      "parts": [{"kind": "text", "text": "x"}]
				    }
				  }
				}
				""";

		this.mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("rpc-wrap"))
			.andExpect(jsonPath("$.error.code").value(-32603))
			.andExpect(jsonPath("$.error.message").value("Internal error: boom"));
	}

	@Test
	void sendMessage_invalidJson_returnsBadRequest() throws Exception {
		this.mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void sendMessageStream_returnsTextEventStream_andDelegatesRequestHandler() throws Exception {
		TaskStatus completedStatus = new TaskStatus(TaskState.COMPLETED, null, null);
		TaskStatusUpdateEvent completedEvent = new TaskStatusUpdateEvent("task-1", completedStatus, "ctx-1", true,
				null);

		SubmissionPublisher<StreamingEventKind> publisher = new SubmissionPublisher<>();

		when(this.requestHandler.onMessageSendStream(any(), any())).thenAnswer(inv -> {
			new Thread(() -> {
				try {
					Thread.sleep(20);
				}
				catch (InterruptedException ignored) {
				}
				publisher.submit(completedEvent);
				publisher.close();
			}).start();
			return publisher;
		});

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-sse",
				  "method": "message/stream",
				  "params": {
				    "message": {
				      "kind": "message",
				      "messageId": "m-sse",
				      "role": "user",
				      "parts": [{"kind": "text", "text": "hello stream"}]
				    }
				  }
				}
				""";

		var asyncResult = this.mockMvc
			.perform(
					post("/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM).content(body))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(asyncResult))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")));

		verify(this.requestHandler).onMessageSendStream(any(), any());
	}

}
