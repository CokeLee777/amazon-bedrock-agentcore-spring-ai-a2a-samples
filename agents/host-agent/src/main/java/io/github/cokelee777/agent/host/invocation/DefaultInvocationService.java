package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.RemoteAgentConnections;
import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.MemoryMode;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link InvocationService}.
 *
 * <p>
 * Execution order per request:
 * <ol>
 * <li>Resolve {@code actorId}/{@code sessionId} (generate if absent).</li>
 * <li>Load memory context according to the configured {@link MemoryMode}.</li>
 * <li>Assemble system prompt and call the LLM via {@link ChatClient}.</li>
 * <li>Persist USER and ASSISTANT turns <em>after</em> the LLM call succeeds, ensuring a
 * failed call leaves no orphaned events and allows clean retry.</li>
 * </ol>
 * </p>
 *
 * <p>
 * <strong>Timestamp trade-off:</strong> {@code appendUserTurn} is called after the LLM
 * returns, so the stored {@code eventTimestamp} reflects "time of persistence" rather
 * than "time of utterance". Relative ordering (USER → ASSISTANT) is always preserved.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultInvocationService implements InvocationService {

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

	private final ConversationMemoryService conversationMemoryService;

	private final LongTermMemoryService longTermMemoryService;

	private final ChatClient chatClient;

	private final RemoteAgentConnections connections;

	private final BedrockMemoryProperties properties;

	@Override
	public InvocationResponse invoke(InvocationRequest request) {
		String actorId = Objects.requireNonNullElse(request.actorId(), UUID.randomUUID().toString());
		String sessionId = Objects.requireNonNullElse(request.sessionId(), UUID.randomUUID().toString());
		MemoryMode mode = properties.mode();

		// 1. Collect context (short-term history and/or long-term retrieval).
		List<Message> history = loadHistory(actorId, sessionId, mode);
		List<String> relevantMemories = retrieveRelevant(actorId, request.prompt(), mode);

		// 2. Assemble the system prompt (routing + optional memory block).
		String systemPrompt = buildSystemPrompt(relevantMemories);

		// 3. Call ChatClient; on failure, propagate without persisting turns (clean
		// retry).
		String response = chatClient.prompt()
			.system(systemPrompt)
			.messages(history)
			.user(request.prompt())
			.call()
			.content();
		String content = Objects.requireNonNullElse(response, "");

		// 4. Persist user and assistant turns after a successful ChatClient call.
		if (mode != MemoryMode.NONE) {
			conversationMemoryService.appendUserTurn(actorId, sessionId, request.prompt());
			conversationMemoryService.appendAssistantTurn(actorId, sessionId, content);
		}

		log.info("actorId={} sessionId={} prompt={} response={}", actorId, sessionId, request.prompt(), content);
		return new InvocationResponse(content, sessionId, actorId);
	}

	private List<Message> loadHistory(String actorId, String sessionId, MemoryMode mode) {
		if (mode == MemoryMode.SHORT_TERM || mode == MemoryMode.BOTH) {
			return conversationMemoryService.loadHistory(actorId, sessionId);
		}
		return Collections.emptyList();
	}

	private List<String> retrieveRelevant(String actorId, String prompt, MemoryMode mode) {
		if (mode == MemoryMode.LONG_TERM || mode == MemoryMode.BOTH) {
			return longTermMemoryService.retrieveRelevant(actorId, prompt);
		}
		return Collections.emptyList();
	}

	private String buildSystemPrompt(List<String> relevantMemories) {
		String agentDescriptions = connections.getAgentDescriptions();
		String base = String.format(ROUTING_SYSTEM_PROMPT, agentDescriptions);
		if (relevantMemories.isEmpty()) {
			return base;
		}
		String memoriesBlock = "\n\n**관련 기억:**\n" + String.join("\n- ", relevantMemories);
		return base + memoriesBlock;
	}

}
