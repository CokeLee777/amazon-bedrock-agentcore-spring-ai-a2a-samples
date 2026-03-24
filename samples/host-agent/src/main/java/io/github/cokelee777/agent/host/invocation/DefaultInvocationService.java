package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link InvocationService}.
 *
 * <p>
 * Execution order per request:
 * <ol>
 * <li>Resolve {@code actorId}/{@code sessionId} (generate UUID if absent).</li>
 * <li>Compose {@code conversationId} as {@code "actorId:sessionId"}.</li>
 * <li>Load history from {@link ChatMemoryRepository}.</li>
 * <li>Call the LLM via {@link ChatClient} (system prompt includes downstream agents from
 * {@link RemoteAgentCardRegistry#getAgentDescriptions()}).</li>
 * <li>Persist the new USER and ASSISTANT messages <em>after</em> the LLM call succeeds,
 * ensuring a failed call leaves no orphaned events.</li>
 * </ol>
 * </p>
 *
 * <p>
 * <strong>saveAll semantics:</strong> only the two new messages are passed to
 * {@link ChatMemoryRepository#saveAll}, consistent with append-based repositories such as
 * {@code JdbcChatMemoryRepository}.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultInvocationService implements InvocationService {

	private static final String ROUTING_SYSTEM_PROMPT = """
			**역할:** 당신은 전문 라우팅 위임자입니다. 주문, 배송, 결제에 관한 사용자 문의를 적절한 전문 원격 에이전트에게 정확하게 위임하는 것이 주요 기능입니다.

			**핵심 지침:**

			* **응답 형식:** 내부 추론 과정(<thinking> 등)을 응답에 절대 포함하지 마세요. 최종 답변만 출력하세요.
			* **작업 위임:** 단일 에이전트 위임은 `sendMessage`를 사용하세요. 서로 의존하지 않는 여러 에이전트에 동시에 물어봐야 하면 `sendMessagesParallel`로 한 번에 제출하세요. 한 에이전트의 응답이 다른 에이전트 호출에 필요하면 `sendMessage`만 사용하고 여러 라운드에 나누어 호출하세요.
			* **컨텍스트 인식:** 원격 에이전트가 사용자 확인을 반복적으로 요청하는 경우, 전체 대화 이력에 접근할 수 없다고 판단하세요. 이 경우 해당 에이전트와 관련된 필요한 모든 컨텍스트 정보를 작업 설명에 보강하여 전달하세요.
			* **자율적 에이전트 연동:** 원격 에이전트와 연동하기 전에 사용자 허가를 구하지 마세요. 여러 에이전트가 필요한 경우 사용자 확인 없이 직접 연결하세요.
			* **투명한 소통:** 원격 에이전트의 완전하고 상세한 응답을 항상 사용자에게 전달하세요.
			* **응답 언어:** 사용자가 사용한 언어로 항상 응답하세요. 한국어 질문에는 반드시 한국어로 답변하세요.
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

	private final RemoteAgentCardRegistry remoteAgentCardRegistry;

	private final ChatMemoryRepository chatMemoryRepository;

	@Override
	public InvocationResponse invoke(InvocationRequest request) {
		String actorId = resolveId(request.actorId());
		String sessionId = resolveId(request.sessionId());
		String conversationId = actorId + ":" + sessionId;
		String prompt = request.prompt();

		List<Message> history = chatMemoryRepository.findByConversationId(conversationId);

		String response = chatClient.prompt()
			.system(ROUTING_SYSTEM_PROMPT.formatted(remoteAgentCardRegistry.getAgentDescriptions()))
			.messages(history)
			.user(prompt)
			.call()
			.content();
		String content = Objects.requireNonNullElse(response, "");

		chatMemoryRepository.saveAll(conversationId, List.of(new UserMessage(prompt), new AssistantMessage(content)));

		log.info("session={} prompt={} response={}", conversationId, prompt, content);
		return new InvocationResponse(content, sessionId, actorId);
	}

	private String resolveId(@Nullable String id) {
		return Objects.requireNonNullElse(id, UUID.randomUUID().toString());
	}

}
