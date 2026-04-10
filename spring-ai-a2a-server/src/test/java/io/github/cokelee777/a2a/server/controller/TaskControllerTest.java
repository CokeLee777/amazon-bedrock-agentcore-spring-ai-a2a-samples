package io.github.cokelee777.a2a.server.controller;

import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.github.cokelee777.a2a.server.support.A2AServerTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice tests for {@link TaskController}.
 */
@WebMvcTest(controllers = TaskController.class)
class TaskControllerTest {

	@MockitoBean
	private RequestHandler requestHandler;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getTask_delegatesToRequestHandler() throws Exception {
		Task task = A2AServerTestFixtures.taskInState("task-x", "ctx-x", TaskState.WORKING);
		when(this.requestHandler.onGetTask(any(TaskQueryParams.class), any())).thenReturn(task);

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-1",
				  "method": "tasks/get",
				  "params": {"id": "task-x"}
				}
				""";

		this.mockMvc.perform(post("/tasks/get").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("rpc-1"))
			.andExpect(jsonPath("$.result.id").value("task-x"))
			.andExpect(jsonPath("$.result.status.state").value("working"));

		verify(this.requestHandler).onGetTask(eq(new TaskQueryParams("task-x")), any());
	}

	@Test
	void cancelTask_delegatesToRequestHandler() throws Exception {
		Task task = A2AServerTestFixtures.taskInState("task-x", "ctx-x", TaskState.CANCELED);
		when(this.requestHandler.onCancelTask(any(TaskIdParams.class), any())).thenReturn(task);

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-2",
				  "method": "tasks/cancel",
				  "params": {"id": "task-x"}
				}
				""";

		this.mockMvc.perform(post("/tasks/cancel").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("rpc-2"))
			.andExpect(jsonPath("$.result.status.state").value("canceled"));

		verify(this.requestHandler).onCancelTask(eq(new TaskIdParams("task-x")), any());
	}

	@Test
	void getTask_jsonRpcErrorFromHandler_returnedAsJsonRpcErrorResponse() throws Exception {
		when(this.requestHandler.onGetTask(any(TaskQueryParams.class), any()))
			.thenThrow(new JSONRPCError(-32001, "no task", null));

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-err",
				  "method": "tasks/get",
				  "params": {"id": "missing"}
				}
				""";

		this.mockMvc.perform(post("/tasks/get").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("rpc-err"))
			.andExpect(jsonPath("$.error.code").value(-32001))
			.andExpect(jsonPath("$.error.message").value("no task"));
	}

	@Test
	void getTask_unexpectedException_returnedAsInternalJsonRpcError() throws Exception {
		when(this.requestHandler.onGetTask(any(TaskQueryParams.class), any()))
			.thenThrow(new RuntimeException("db down"));

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "rpc-fail",
				  "method": "tasks/get",
				  "params": {"id": "broken"}
				}
				""";

		this.mockMvc.perform(post("/tasks/get").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("rpc-fail"))
			.andExpect(jsonPath("$.error.code").value(-32603))
			.andExpect(jsonPath("$.error.message").value("Internal error: db down"));
	}

}
