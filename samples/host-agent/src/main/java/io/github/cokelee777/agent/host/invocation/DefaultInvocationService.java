package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry;
import io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.AdvancedBedrockAgentCoreChatMemoryRepository;
import io.github.cokelee777.agent.host.remote.RemoteAgentTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link InvocationService}.
 *
 * <p>
 * When {@link ChatMemoryRepository} is an
 * {@link AdvancedBedrockAgentCoreChatMemoryRepository}, load and save use actor-scoped
 * overloads; otherwise the base {@link ChatMemoryRepository} methods are used (e.g.
 * in-memory tests).
 * </p>
 *
 * <p>
 * Execution order per request:
 * <ol>
 * <li>Resolve {@link InvocationRequest#actorId()} and
 * {@link InvocationRequest#conversationId()} (UUIDs assigned in the request record when
 * omitted).</li>
 * <li>Load history (actor-scoped when the repository supports it).</li>
 * <li>Call the LLM via {@link ChatClient} (system prompt includes downstream agents from
 * {@link RemoteAgentCardRegistry#getAgentDescriptions()}).</li>
 * <li>Persist the full message list <em>after</em> the LLM call succeeds.</li>
 * </ol>
 * </p>
 *
 * <p>
 * <strong>saveAll semantics:</strong> passes the complete conversation (existing history
 * plus the new user prompt and assistant reply) so implementations that replace events
 * per call behave correctly.
 * </p>
 */
@Slf4j
@Service
public class DefaultInvocationService implements InvocationService {

	private static final String ROUTING_SYSTEM_PROMPT = """
			**역할:** 당신은 전문 라우팅 위임자입니다. 주문, 배송, 결제에 관한 사용자 문의를 적절한 전문 원격 에이전트에게 정확하게 위임하는 것이 주요 기능입니다.

			**핵심 지침:**

			* **응답 형식:** 내부 추론 과정(<thinking> 등)을 응답에 절대 포함하지 마세요. 최종 답변만 출력하세요.
			* **작업 위임:** 단일 에이전트 위임은 `delegateToRemoteAgent`를 사용하세요. 서로 의존하지 않는 여러 에이전트에 동시에 물어봐야 하면 `delegateToRemoteAgentsParallel`로 한 번에 제출하세요. 한 에이전트의 응답이 다른 에이전트 호출에 필요하면 `delegateToRemoteAgent`만 사용하고 여러 라운드에 나누어 호출하세요.
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

	private final ChatClient streamingChatClient;

	private final RemoteAgentCardRegistry remoteAgentCardRegistry;

	private final ChatMemoryRepository chatMemoryRepository;

	public DefaultInvocationService(@Qualifier("chatClient") ChatClient chatClient,
			@Qualifier("streamingChatClient") ChatClient streamingChatClient,
			RemoteAgentCardRegistry remoteAgentCardRegistry, ChatMemoryRepository chatMemoryRepository) {
		this.chatClient = chatClient;
		this.streamingChatClient = streamingChatClient;
		this.remoteAgentCardRegistry = remoteAgentCardRegistry;
		this.chatMemoryRepository = chatMemoryRepository;
	}

	@Override
	public InvocationResponse invoke(InvocationRequest request) {
		Assert.notNull(request, "request must not be null");

		String prompt = request.prompt();
		String actorId = request.actorId();
		String conversationId = request.conversationId();

		List<Message> history;
		if (this.chatMemoryRepository instanceof AdvancedBedrockAgentCoreChatMemoryRepository advanced) {
			history = advanced.findByConversationId(actorId, conversationId);
		}
		else {
			history = this.chatMemoryRepository.findByConversationId(conversationId);
		}

		RemoteAgentTools tools = new RemoteAgentTools(remoteAgentCardRegistry);
		String response = chatClient.prompt()
			.system(ROUTING_SYSTEM_PROMPT.formatted(remoteAgentCardRegistry.getAgentDescriptions()))
			.tools(tools)
			.messages(history)
			.user(prompt)
			.call()
			.content();
		String content = filterThinkingTags(Objects.requireNonNullElse(response, ""));

		List<Message> messages = new ArrayList<>(history);
		messages.add(new UserMessage(prompt));
		messages.add(new AssistantMessage(content));
		if (this.chatMemoryRepository instanceof AdvancedBedrockAgentCoreChatMemoryRepository advanced) {
			advanced.saveAll(actorId, conversationId, messages);
		}
		else {
			this.chatMemoryRepository.saveAll(conversationId, messages);
		}

		return new InvocationResponse(content, actorId, conversationId);
	}

	@Override
	public void invokeStream(InvocationRequest request, SseEmitter emitter) {
		Assert.notNull(request, "request must not be null");

		String prompt = request.prompt();
		String actorId = request.actorId();
		String conversationId = request.conversationId();

		List<Message> history;
		if (this.chatMemoryRepository instanceof AdvancedBedrockAgentCoreChatMemoryRepository advanced) {
			history = advanced.findByConversationId(actorId, conversationId);
		}
		else {
			history = this.chatMemoryRepository.findByConversationId(conversationId);
		}

		RemoteAgentTools tools = new RemoteAgentTools(remoteAgentCardRegistry, emitter);
		StringBuilder accumulated = new StringBuilder();
		AtomicBoolean inThinking = new AtomicBoolean(false);
		AtomicReference<StringBuilder> pendingBuffer = new AtomicReference<>(new StringBuilder());

		this.streamingChatClient.prompt()
			.system(ROUTING_SYSTEM_PROMPT.formatted(remoteAgentCardRegistry.getAgentDescriptions()))
			.tools(tools)
			.messages(history)
			.user(prompt)
			.stream()
			.content()
			.doOnNext(token -> {
				String safe = processStreamToken(token, inThinking, pendingBuffer);
				if (!safe.isEmpty()) {
					accumulated.append(safe);
					try {
						emitter.send(SseEmitter.event().name("token").data(safe));
					}
					catch (IOException e) {
						throw new RuntimeException("Client disconnected", e);
					}
				}
			})
			.doOnComplete(() -> {
				// Flush any buffered content that was held for tag detection
				String remaining = filterThinkingTags(pendingBuffer.get().toString());
				if (!remaining.isEmpty()) {
					accumulated.append(remaining);
					try {
						emitter.send(SseEmitter.event().name("token").data(remaining));
					}
					catch (IOException e) {
						log.warn("Failed to send remaining tokens on complete", e);
					}
				}
				try {
					String content = accumulated.toString();
					List<Message> messages = new ArrayList<>(history);
					messages.add(new UserMessage(prompt));
					messages.add(new AssistantMessage(content));
					if (this.chatMemoryRepository instanceof AdvancedBedrockAgentCoreChatMemoryRepository advanced) {
						advanced.saveAll(actorId, conversationId, messages);
					}
					else {
						this.chatMemoryRepository.saveAll(conversationId, messages);
					}
				}
				catch (Exception e) {
					log.error("Failed to persist conversation history", e);
				}
				finally {
					emitter.complete();
				}
			})
			.doOnError(e -> {
				log.error("Error during streaming invocation", e);
				emitter.completeWithError(e);
			})
			.subscribe();
	}

	/**
	 * Strips all {@code <thinking>...</thinking>} blocks from a completed response
	 * string.
	 */
	private static String filterThinkingTags(String content) {
		if (content.isEmpty()) {
			return "";
		}
		return content.replaceAll("(?s)<thinking>.*?</thinking>\\s*", "").trim();
	}

	/**
	 * Processes a single stream token through a stateful {@code <thinking>} tag filter.
	 * Buffers partial tag boundaries so that tags split across token boundaries are
	 * handled correctly. Returns only the content that is safe to emit (outside thinking
	 * blocks).
	 */
	private static String processStreamToken(String token, AtomicBoolean inThinking,
			AtomicReference<StringBuilder> buffer) {
		buffer.get().append(token);
		String current = buffer.get().toString();
		StringBuilder emit = new StringBuilder();

		while (!current.isEmpty()) {
			if (inThinking.get()) {
				int endIdx = current.indexOf("</thinking>");
				if (endIdx >= 0) {
					current = current.substring(endIdx + "</thinking>".length());
					// Strip leading whitespace after closing tag
					int ws = 0;
					while (ws < current.length() && Character.isWhitespace(current.charAt(ws))) {
						ws++;
					}
					current = current.substring(ws);
					inThinking.set(false);
				}
				else {
					// End tag not yet received — keep buffering
					buffer.set(new StringBuilder(current));
					return emit.toString();
				}
			}
			else {
				int startIdx = current.indexOf("<thinking>");
				if (startIdx >= 0) {
					emit.append(current, 0, startIdx);
					current = current.substring(startIdx + "<thinking>".length());
					inThinking.set(true);
				}
				else {
					// No complete start tag — hold back enough chars for a partial match
					int safeLen = safeEmitLength(current);
					emit.append(current, 0, safeLen);
					current = current.substring(safeLen);
					buffer.set(new StringBuilder(current));
					return emit.toString();
				}
			}
		}
		buffer.set(new StringBuilder(current));
		return emit.toString();
	}

	/**
	 * Returns the number of characters in {@code s} that can be safely emitted without
	 * risking cutting off the start of a {@code <thinking>} or {@code </thinking>} tag.
	 */
	private static int safeEmitLength(String s) {
		// Hold back up to (tag length - 1) chars so a partial tag at the end stays
		// buffered
		int maxHold = "</thinking>".length() - 1; // 10 chars
		if (s.length() <= maxHold) {
			return 0;
		}
		for (int hold = maxHold; hold >= 1; hold--) {
			String suffix = s.substring(s.length() - hold);
			if ("<thinking>".startsWith(suffix) || "</thinking>".startsWith(suffix)) {
				return s.length() - hold;
			}
		}
		return s.length();
	}

}
