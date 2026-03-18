package io.github.cokelee777.a2a.agent.host;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AgentCore Runtime invocations.
 *
 * <p>
 * Amazon Bedrock AgentCore Runtime forwards user messages to {@code POST /invocations}.
 * This controller delegates each request to the {@link ChatClient}, which routes to
 * downstream A2A agents via {@link RemoteAgentConnections}.
 * </p>
 *
 * <p>
 * The routing system prompt is built on every request so that agent descriptions always
 * reflect the latest loaded {@link io.a2a.spec.AgentCard AgentCards} — including agents
 * that were unavailable at startup and resolved lazily.
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InvocationsController {

	private static final String ROUTING_SYSTEM_PROMPT = """
			**역할:** 당신은 전문 라우팅 위임자입니다. 주문, 배송, 결제에 관한 사용자 문의를 적절한 전문 원격 에이전트에게 정확하게 위임하는 것이 주요 기능입니다.

			**핵심 지침:**

			* **작업 위임:** `sendMessage` 함수를 사용하여 원격 에이전트에 작업을 할당하세요.
			* **컨텍스트 인식:** 원격 에이전트가 사용자 확인을 반복적으로 요청하는 경우, 전체 대화 이력에 접근할 수 없다고 판단하세요. 이 경우 해당 에이전트와 관련된 필요한 모든 컨텍스트 정보를 작업 설명에 보강하여 전달하세요.
			* **자율적 에이전트 연동:** 원격 에이전트와 연동하기 전에 사용자 허가를 구하지 마세요. 여러 에이전트가 필요한 경우 사용자 확인 없이 직접 연결하세요.
			* **투명한 소통:** 원격 에이전트의 완전하고 상세한 응답을 항상 사용자에게 전달하세요.
			* **사용자 확인 릴레이:** 원격 에이전트가 확인을 요청하고 사용자가 아직 제공하지 않은 경우, 이 확인 요청을 사용자에게 릴레이하세요.
			* **집중적인 정보 공유:** 원격 에이전트에게는 관련 컨텍스트 정보만 제공하세요. 불필요한 세부사항은 피하세요.
			* **중복 확인 금지:** 원격 에이전트에게 정보나 작업의 확인을 요청하지 마세요.
			* **도구 의존:** 사용 가능한 도구에 전적으로 의존하여 사용자 요청을 처리하세요. 가정을 기반으로 응답을 생성하지 마세요. 정보가 불충분한 경우 사용자에게 명확한 설명을 요청하세요.
			* **최근 상호작용 우선:** 요청을 처리할 때 대화의 가장 최근 부분에 주로 집중하세요.

			**에이전트 라우터:**

			사용 가능한 에이전트:
			%s
			""";

	private final ChatClient chatClient;

	private final RemoteAgentConnections connections;

	/**
	 * Handles invocation requests from Amazon Bedrock AgentCore Runtime.
	 *
	 * <p>
	 * Builds a dynamic system prompt using the currently loaded agent descriptions so
	 * that agents resolved lazily after startup are included.
	 * </p>
	 * @param request the invocation request containing the user prompt
	 * @return the generated response text from the LLM
	 */
	@PostMapping(path = "/invocations")
	public String invoke(@Valid @RequestBody InvocationRequest request) {
		log.info("Received: {}", request.prompt());
		String systemPrompt = String.format(ROUTING_SYSTEM_PROMPT, connections.getAgentDescriptions());
		String response = chatClient.prompt().system(systemPrompt).user(request.prompt()).call().content();
		log.info("Response: {}", response);
		return response != null ? response : "";
	}

	/**
	 * Request payload used by AgentCore Runtime when invoking the agent.
	 *
	 * @param prompt the user prompt that should be processed by the agent
	 */
	public record InvocationRequest(@NotBlank(message = "prompt must no be blank") String prompt) {

	}

}
