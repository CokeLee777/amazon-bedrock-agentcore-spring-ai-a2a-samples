package io.github.cokelee777.agentcore.autoconfigure.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.DeleteTaskPushNotificationConfigRequest;
import io.a2a.spec.DeleteTaskPushNotificationConfigResponse;
import io.a2a.spec.EventKind;
import io.a2a.spec.GetTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskPushNotificationConfigResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.IdJsonMappingException;
import io.a2a.spec.InternalError;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.InvalidParamsJsonMappingException;
import io.a2a.spec.InvalidRequestError;
import io.a2a.spec.JSONParseError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.ListTaskPushNotificationConfigRequest;
import io.a2a.spec.ListTaskPushNotificationConfigResponse;
import io.a2a.spec.MethodNotFoundError;
import io.a2a.spec.MethodNotFoundJsonMappingException;
import io.a2a.spec.NonStreamingJSONRPCRequest;
import io.a2a.spec.NonStreamingJSONRPCRequestDeserializer;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SetTaskPushNotificationConfigRequest;
import io.a2a.spec.SetTaskPushNotificationConfigResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskPushNotificationConfig;
import io.a2a.spec.UnsupportedOperationError;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * REST controller handling A2A Protocol JSON-RPC requests at {@code POST /}.
 *
 * <p>
 * Incoming request bodies are parsed via Jackson {@link ObjectMapper} with a registered
 * {@link NonStreamingJSONRPCRequestDeserializer} and dispatched to the appropriate
 * {@link RequestHandler} method. Error conditions produce JSON-RPC error responses.
 * </p>
 */
@RestController
public class A2AJsonRpcController {

	private final RequestHandler requestHandler;

	private final ObjectMapper objectMapper;

	/**
	 * Create a new {@link A2AJsonRpcController}.
	 * @param requestHandler the request handler dispatching A2A JSON-RPC calls
	 */
	@SuppressWarnings({ "SpringJavaInjectionPointsAutowiringInspection", "unchecked", "rawtypes" })
	public A2AJsonRpcController(RequestHandler requestHandler) {
		this.requestHandler = requestHandler;
		SimpleModule module = new SimpleModule();
		module.addDeserializer((Class) NonStreamingJSONRPCRequest.class, new NonStreamingJSONRPCRequestDeserializer());
		this.objectMapper = new ObjectMapper().registerModule(module).registerModule(new JavaTimeModule());
	}

	/**
	 * Handles a synchronous A2A JSON-RPC request and returns the serialized response.
	 *
	 * <p>
	 * On success, returns {@code 200 OK} with a JSON-RPC result body. On failure (parse,
	 * dispatch, or handler error), returns {@code 500} with a JSON-RPC error response
	 * body in most cases. If serializing that error response fails, the response body may
	 * be a plain text message instead of JSON-RPC.
	 * </p>
	 * @param body the raw JSON-RPC request body
	 * @return {@code 200} with result JSON, or {@code 500} with error JSON (or plain text
	 * on serialization failure)
	 */
	@PostMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> handle(@RequestBody String body) {
		Object request;
		JSONRPCErrorResponse error = null;

		try {
			ServerCallContext context = createCallContext();
			request = objectMapper.readValue(body, NonStreamingJSONRPCRequest.class);
			if (request instanceof NonStreamingJSONRPCRequest<?> nonStreamingRequest) {
				Object response = processNonStreamingRequest(nonStreamingRequest, context);
				return ResponseEntity.ok(objectMapper.writeValueAsString(response));
			}
		}
		catch (JSONRPCError e) {
			error = new JSONRPCErrorResponse(e);
		}
		catch (InvalidParamsJsonMappingException e) {
			error = new JSONRPCErrorResponse(e.getId(), new InvalidParamsError(null, e.getMessage(), null));
		}
		catch (MethodNotFoundJsonMappingException e) {
			error = new JSONRPCErrorResponse(e.getId(), new MethodNotFoundError(null, e.getMessage(), null));
		}
		catch (IdJsonMappingException e) {
			error = new JSONRPCErrorResponse(e.getId(), new InvalidRequestError(null, e.getMessage(), null));
		}
		catch (JsonMappingException e) {
			error = new JSONRPCErrorResponse(new InvalidRequestError(null, e.getMessage(), null));
		}
		catch (JsonProcessingException e) {
			error = new JSONRPCErrorResponse(new JSONParseError(e.getMessage()));
		}
		catch (Throwable t) {
			error = new JSONRPCErrorResponse(new InternalError(t.getMessage()));
		}

		try {
			error = Objects.requireNonNullElseGet(error,
					() -> new JSONRPCErrorResponse(new UnsupportedOperationError()));
			return ResponseEntity.internalServerError().body(objectMapper.writeValueAsString(error));
		}
		catch (Exception e) {
			error = new JSONRPCErrorResponse(new InternalError("serialization failed"));
			return ResponseEntity.internalServerError().body(error.getError().getMessage());
		}
	}

	private ServerCallContext createCallContext() {
		return new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
	}

	private Object processNonStreamingRequest(NonStreamingJSONRPCRequest<?> request, ServerCallContext context) {
		Object requestId = request.getId();
		if (request instanceof GetTaskRequest req) {
			Task task = requestHandler.onGetTask(req.getParams(), context);
			return new GetTaskResponse(requestId, task);
		}
		if (request instanceof CancelTaskRequest req) {
			Task task = requestHandler.onCancelTask(req.getParams(), context);
			return new CancelTaskResponse(requestId, task);
		}
		if (request instanceof SetTaskPushNotificationConfigRequest req) {
			TaskPushNotificationConfig config = requestHandler.onSetTaskPushNotificationConfig(req.getParams(),
					context);
			return new SetTaskPushNotificationConfigResponse(requestId, config);
		}
		if (request instanceof GetTaskPushNotificationConfigRequest req) {
			TaskPushNotificationConfig config = requestHandler.onGetTaskPushNotificationConfig(req.getParams(),
					context);
			return new GetTaskPushNotificationConfigResponse(requestId, config);
		}
		if (request instanceof ListTaskPushNotificationConfigRequest req) {
			List<TaskPushNotificationConfig> configs = requestHandler.onListTaskPushNotificationConfig(req.getParams(),
					context);
			return new ListTaskPushNotificationConfigResponse(requestId, configs);
		}
		if (request instanceof SendMessageRequest req) {
			EventKind result = requestHandler.onMessageSend(req.getParams(), context);
			return new SendMessageResponse(requestId, result);
		}
		if (request instanceof DeleteTaskPushNotificationConfigRequest req) {
			requestHandler.onDeleteTaskPushNotificationConfig(req.getParams(), context);
			return new DeleteTaskPushNotificationConfigResponse(request.getId());
		}
		return new JSONRPCErrorResponse(requestId, new UnsupportedOperationError());
	}

}