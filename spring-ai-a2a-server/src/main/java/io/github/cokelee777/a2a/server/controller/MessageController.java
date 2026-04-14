package io.github.cokelee777.a2a.server.controller;

import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.EventKind;
import io.a2a.spec.InternalError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SendStreamingMessageResponse;
import io.a2a.spec.StreamingEventKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;

/**
 * REST controller for A2A message sending.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MessageController {

	private final RequestHandler requestHandler;

	/**
	 * Sends a message to the agent and returns the result.
	 */
	@PostMapping(path = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public SendMessageResponse sendMessage(@RequestBody SendMessageRequest request) {
		MessageSendParams params = request.getParams();
		log.debug("Received sendMessage request - id: {}", request.getId());

		try {
			// TODO: Add support for auth context and extensions
			ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());

			EventKind result = this.requestHandler.onMessageSend(params, context);

			log.debug("Message processed successfully - id: {}", request.getId());
			return new SendMessageResponse(request.getId(), result);
		}
		catch (JSONRPCError e) {
			log.error("Error processing message - id: {}", request.getId(), e);
			return new SendMessageResponse(request.getId(), e);
		}
		catch (Exception e) {
			log.error("Unexpected error processing message - id: {}", request.getId(), e);
			return new SendMessageResponse(request.getId(), new InternalError("Internal error: " + e.getMessage()));
		}
	}

	/**
	 * Handles {@code POST /} with {@code Accept: text/event-stream}. Subscribes to
	 * {@link RequestHandler#onMessageSendStream} and emits each event as SSE.
	 */
	@PostMapping(path = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter sendMessageStream(@RequestBody SendStreamingMessageRequest request) {
		SseEmitter emitter = new SseEmitter(120_000L);
		log.debug("Received sendMessageStream request - id: {}", request.getId());

		try {
			ServerCallContext context = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
			Flow.Publisher<StreamingEventKind> publisher = this.requestHandler.onMessageSendStream(request.getParams(),
					context);

			publisher.subscribe(new Flow.Subscriber<>() {

				private Flow.Subscription subscription;

				@Override
				public void onSubscribe(Flow.Subscription subscription) {
					this.subscription = subscription;
					subscription.request(Long.MAX_VALUE);
				}

				@Override
				public void onNext(StreamingEventKind event) {
					try {
						emitter.send(SseEmitter.event()
							.name(event.getKind())
							.data(new SendStreamingMessageResponse(request.getId(), event)));
					}
					catch (IOException e) {
						log.warn("Client disconnected during SSE emit: {}", e.getMessage());
						this.subscription.cancel();
					}
				}

				@Override
				public void onError(Throwable throwable) {
					log.error("SSE stream error - id: {}", request.getId(), throwable);
					emitter.completeWithError(throwable);
				}

				@Override
				public void onComplete() {
					emitter.complete();
				}
			});
		}
		catch (JSONRPCError e) {
			log.error("JSON-RPC error in sendMessageStream - id: {}", request.getId(), e);
			emitter.completeWithError(e);
		}
		catch (Exception e) {
			log.error("Unexpected error in sendMessageStream - id: {}", request.getId(), e);
			emitter.completeWithError(e);
		}

		emitter.onTimeout(emitter::complete);
		emitter.onError(_ -> emitter.complete());
		return emitter;
	}

}