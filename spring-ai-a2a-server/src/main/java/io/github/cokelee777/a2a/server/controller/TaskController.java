package io.github.cokelee777.a2a.server.controller;

import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.InternalError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskQueryParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
	@PostMapping(path = "/get", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public GetTaskResponse getTask(@RequestBody GetTaskRequest request) {
		TaskQueryParams params = request.getParams();
		log.debug("Received getTask request - id: {}", request.getId());

		try {
			// TODO: Add support for auth context and extensions
			ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());

			Task task = this.requestHandler.onGetTask(params, context);
			log.debug("Task retrieved successfully - id: {}", request.getId());
			return new GetTaskResponse(request.getId(), task);
		}
		catch (JSONRPCError e) {
			log.error("Error getting task - id: {}", request.getId(), e);
			return new GetTaskResponse(request.getId(), e);
		}
		catch (Exception e) {
			log.error("Unexpected error getting task - id: {}", request.getId(), e);
			return new GetTaskResponse(request.getId(), new InternalError("Internal error: " + e.getMessage()));
		}
	}

	/**
	 * Cancels a running task.
	 */
	@PostMapping(path = "/cancel", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public CancelTaskResponse cancelTask(@RequestBody CancelTaskRequest request) {
		TaskIdParams params = request.getParams();
		log.debug("Received cancelTask request - id: {}", request.getId());

		try {
			// TODO: Add support for auth context and extensions
			ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());

			Task task = this.requestHandler.onCancelTask(params, context);
			log.debug("Task cancelled successfully - id: {}", request.getId());
			return new CancelTaskResponse(request.getId(), task);
		}
		catch (JSONRPCError e) {
			log.error("Error cancelling task - id: {}", request.getId(), e);
			return new CancelTaskResponse(request.getId(), e);
		}
		catch (Exception e) {
			log.error("Unexpected error cancelling task - id: {}", request.getId(), e);
			return new CancelTaskResponse(request.getId(), new InternalError("Internal error: " + e.getMessage()));
		}
	}

}
