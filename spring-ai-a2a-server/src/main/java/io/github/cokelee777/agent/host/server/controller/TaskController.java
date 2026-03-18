package io.github.cokelee777.agent.host.server.controller;

import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskQueryParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * REST controller for A2A task operations.
 */
@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

	private final RequestHandler requestHandler;

	/**
	 * Returns task status and results.
	 */
	@GetMapping(path = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public Task getTask(@PathVariable String taskId) throws JSONRPCError {
		log.debug("Getting task: {}", taskId);

		try {
			// TODO: Add support for auth context, state, and extensions
			ServerCallContext context = new ServerCallContext(null, Map.of(), Set.of());
			TaskQueryParams params = new TaskQueryParams(taskId);

			Task task = requestHandler.onGetTask(params, context);
			log.debug("Task retrieved: {} - state: {}", taskId, task.getStatus().state());
			return task;
		}
		catch (JSONRPCError e) {
			log.error("Error getting task: {}", taskId, e);
			throw e;
		}
		catch (Exception e) {
			log.error("Unexpected error getting task: {}", taskId, e);
			throw new JSONRPCError(-32603, "Internal error: " + e.getMessage(), null);
		}
	}

	/**
	 * Cancels a running task.
	 */
	@PostMapping(path = "/{taskId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
	public Task cancelTask(@PathVariable String taskId) throws JSONRPCError {
		log.debug("Cancelling task: {}", taskId);

		try {
			// TODO: Add support for auth context, state, and extensions
			ServerCallContext context = new ServerCallContext(null, Map.of(), Set.of());
			TaskIdParams params = new TaskIdParams(taskId);

			Task task = requestHandler.onCancelTask(params, context);
			log.debug("Task cancelled: {}", taskId);
			return task;
		}
		catch (JSONRPCError e) {
			log.error("Error cancelling task: {}", taskId, e);
			throw e;
		}
		catch (Exception e) {
			log.error("Unexpected error cancelling task: {}", taskId, e);
			throw new JSONRPCError(-32603, "Internal error: " + e.getMessage(), null);
		}
	}

}
