package io.github.cokelee777.agent.host;

import io.github.cokelee777.agent.host.invocation.InvocationRequest;
import io.github.cokelee777.agent.host.invocation.InvocationResponse;
import io.github.cokelee777.agent.host.invocation.InvocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AgentCore Runtime invocations.
 *
 * <p>
 * Amazon Bedrock AgentCore Runtime forwards user messages to {@code POST /invocations}.
 * This controller delegates each request to {@link InvocationService}, which manages
 * memory context and LLM routing.
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InvocationController {

	private final InvocationService invocationService;

	/**
	 * Handles invocation requests from Amazon Bedrock AgentCore Runtime.
	 * @param request the invocation request containing the user prompt and optional
	 * session identifiers
	 * @return the invocation response including assistant content and session identifiers
	 */
	@PostMapping(path = "/invocations")
	public InvocationResponse invoke(@Valid @RequestBody InvocationRequest request) {
		log.info("Received: prompt={} actorId={} sessionId={}", request.prompt(), request.actorId(),
				request.sessionId());
		InvocationResponse response = invocationService.invoke(request);
		log.info("Response: {}", response.content());
		return response;
	}

}
