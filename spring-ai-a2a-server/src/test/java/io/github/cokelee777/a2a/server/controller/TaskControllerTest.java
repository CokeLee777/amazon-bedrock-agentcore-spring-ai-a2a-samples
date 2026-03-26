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
import org.assertj.core.util.Throwables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

		this.mockMvc.perform(get("/tasks/task-x").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("task-x"))
			.andExpect(jsonPath("$.status.state").value("working"));

		verify(this.requestHandler).onGetTask(eq(new TaskQueryParams("task-x")), any());
	}

	@Test
	void cancelTask_delegatesToRequestHandler() throws Exception {
		Task task = A2AServerTestFixtures.taskInState("task-x", "ctx-x", TaskState.CANCELED);
		when(this.requestHandler.onCancelTask(any(TaskIdParams.class), any())).thenReturn(task);

		this.mockMvc.perform(post("/tasks/task-x/cancel").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.state").value("canceled"));

		verify(this.requestHandler).onCancelTask(eq(new TaskIdParams("task-x")), any());
	}

	@Test
	void getTask_jsonRpcErrorFromHandler_propagates() throws Exception {
		when(this.requestHandler.onGetTask(any(TaskQueryParams.class), any()))
			.thenThrow(new JSONRPCError(-32001, "no task", null));

		Throwable thrown = catchThrowable(
				() -> this.mockMvc.perform(get("/tasks/missing").accept(MediaType.APPLICATION_JSON)));

		assertThat(thrown).hasRootCauseInstanceOf(JSONRPCError.class);
	}

	@Test
	void getTask_unexpectedException_wrappedAsInternalJsonRpcError() throws Exception {
		when(this.requestHandler.onGetTask(any(TaskQueryParams.class), any()))
			.thenThrow(new RuntimeException("db down"));

		Throwable thrown = catchThrowable(
				() -> this.mockMvc.perform(get("/tasks/broken").accept(MediaType.APPLICATION_JSON)));

		assertThat(thrown).hasRootCauseInstanceOf(JSONRPCError.class);
		JSONRPCError error = (JSONRPCError) Throwables.getRootCause(thrown);
		assertThat(error.getCode()).isEqualTo(-32603);
		assertThat(error.getMessage()).contains("Internal error: db down");
	}

}
