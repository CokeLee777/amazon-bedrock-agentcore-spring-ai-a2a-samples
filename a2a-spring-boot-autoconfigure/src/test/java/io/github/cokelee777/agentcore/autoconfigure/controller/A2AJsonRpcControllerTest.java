package io.github.cokelee777.agentcore.autoconfigure.controller;

import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link A2AJsonRpcController} using Spring MVC test slice.
 *
 * <p>
 * Verifies HTTP status codes for valid requests, malformed JSON, unknown methods, and
 * handler exceptions without starting a full application context.
 * </p>
 */
@WebMvcTest(A2AJsonRpcController.class)
class A2AJsonRpcControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RequestHandler requestHandler;

	// @formatter:off
	private static final String VALID_MESSAGE_SEND_BODY = """
			{
			  "jsonrpc": "2.0",
			  "id": "1",
			  "method": "message/send",
			  "params": {
			    "message": {
			      "kind": "message",
			      "messageId": "msg-1",
			      "role": "user",
			      "parts": [{"kind": "text", "text": "hello"}]
			    }
			  }
			}
			""";

	private static final String UNKNOWN_METHOD_BODY = """
			{
			  "jsonrpc": "2.0",
			  "id": "1",
			  "method": "UnknownMethod",
			  "params": {}
			}
			""";
	// @formatter:on

	/**
	 * A valid {@code message/send} request with a mocked handler response returns HTTP
	 * 200.
	 */
	@Test
	void validMessageSendReturns200() throws Exception {
		Task task = new Task.Builder().id("task-1")
			.contextId("ctx-1")
			.status(new TaskStatus(TaskState.COMPLETED))
			.build();
		when(requestHandler.onMessageSend(any(), any())).thenReturn(task);

		mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(VALID_MESSAGE_SEND_BODY))
			.andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
			.andExpect(status().isOk());
	}

	/**
	 * A request body with malformed JSON cannot be parsed and returns HTTP 500.
	 */
	@Test
	void malformedJsonReturns500() throws Exception {
		mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content("{invalid-json}"))
			.andExpect(status().isInternalServerError());
	}

	/**
	 * A well-formed JSON-RPC request with an unrecognized method returns HTTP 500.
	 */
	@Test
	void unknownMethodReturns500() throws Exception {
		mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(UNKNOWN_METHOD_BODY))
			.andExpect(status().isInternalServerError());
	}

	/**
	 * When the {@link RequestHandler} throws a {@link RuntimeException}, the controller
	 * catches it and returns HTTP 500.
	 */
	@Test
	void requestHandlerThrowsReturns500() throws Exception {
		when(requestHandler.onMessageSend(any(), any())).thenThrow(new RuntimeException("handler error"));

		mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(VALID_MESSAGE_SEND_BODY))
			.andExpect(status().isInternalServerError());
	}

}
