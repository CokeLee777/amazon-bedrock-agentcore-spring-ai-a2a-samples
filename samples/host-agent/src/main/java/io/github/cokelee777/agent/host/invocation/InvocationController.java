package io.github.cokelee777.agent.host.invocation;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

/**
 * REST controller for AgentCore Runtime invocations.
 *
 * <p>
 * Amazon Bedrock AgentCore Runtime forwards user messages to {@code POST /invocations}.
 * This controller delegates to {@link InvocationService} for memory context and LLM
 * routing.
 * </p>
 */
@Slf4j
@RestController
public class InvocationController {

	private final InvocationService invocationService;

	private final Executor sseEmitterTaskExecutor;

	public InvocationController(InvocationService invocationService,
			@Qualifier("sseEmitterTaskExecutor") Executor sseEmitterTaskExecutor) {
		this.invocationService = invocationService;
		this.sseEmitterTaskExecutor = sseEmitterTaskExecutor;
	}

	/**
	 * Handles invocation requests from Amazon Bedrock AgentCore Runtime.
	 * @param request the invocation request containing the user prompt and optional
	 * {@code actorId} and {@code conversationId}
	 * @return the invocation response including assistant content, {@code actorId}, and
	 * {@code conversationId}
	 */
	@PostMapping(path = "/invocations")
	public InvocationResponse invoke(@Valid @RequestBody InvocationRequest request) {
		log.info("Received: prompt={}", request.prompt());
		InvocationResponse response = invocationService.invoke(request);
		log.info("Response: content={}", response.content());
		return response;
	}

	/**
	 * Handles {@code POST /invocations} with {@code Accept: text/event-stream}.
	 * @param request the invocation request
	 * @return an {@link SseEmitter} that streams LLM tokens to the client
	 */
	@PostMapping(path = "/invocations", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter invokeStream(@Valid @RequestBody InvocationRequest request) {
		log.info("Received stream: prompt={}", request.prompt());
		SseEmitter emitter = new SseEmitter(120_000L);
		this.sseEmitterTaskExecutor.execute(() -> this.invocationService.invokeStream(request, emitter));
		emitter.onTimeout(emitter::complete);
		emitter.onError(_ -> emitter.complete());
		return emitter;
	}

}
