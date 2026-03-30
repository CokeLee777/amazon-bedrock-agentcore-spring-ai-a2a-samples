package io.github.cokelee777.agent.host.invocation;

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
	 * {@code conversationId}
	 * @return the invocation response including assistant content and
	 * {@code conversationId}
	 */
	@PostMapping(path = "/invocations")
	public InvocationResponse invoke(@Valid @RequestBody InvocationRequest request) {
		log.info("Received: prompt={}", request.prompt());
		InvocationResponse response = invocationService.invoke(request);
		log.info("Response: content={}", response.content());
		return response;
	}

}
