package io.github.cokelee777.agent.host.remote;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.github.cokelee777.a2a.agent.common.A2ATransport;
import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Provides the host's downstream A2A agents as Spring AI {@link Tool}-annotated methods,
 * routing orchestration calls over JSON-RPC via {@link A2ATransport}.
 *
 * <p>
 * Each configured URL
 * ({@link io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentProperties})
 * gets one {@link io.github.cokelee777.a2a.agent.common.LazyAgentCard} in
 * {@link RemoteAgentCardRegistry}. Routing uses the card's {@link AgentCard#name()
 * display name} (from {@code /.well-known/agent-card.json}), not the YAML map key.
 * </p>
 *
 * <p>
 * Instances are created per-request by the invocation layer. When an {@link SseEmitter}
 * is supplied, progress events ({@code event: progress}) are sent to the client before
 * each downstream call. Pass {@code null} for non-streaming (blocking) invocations.
 * </p>
 *
 * <p>
 * Tool methods are synchronous from the model's perspective:
 * {@link #delegateToRemoteAgent} blocks until the downstream task completes;
 * {@link #delegateToRemoteAgentsParallel} blocks until every delegated call finishes
 * (each runs on a virtual thread).
 * </p>
 */
@Slf4j
public class RemoteAgentTools {

	/**
	 * Runs one downstream delegation per virtual thread for
	 * {@link #delegateToRemoteAgentsParallel} without tying up platform thread pools.
	 */
	private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors
		.newThreadPerTaskExecutor(Thread.ofVirtual().name("remote-agent-", 1).factory());

	private final RemoteAgentCardRegistry remoteAgentCardRegistry;

	@Nullable private final SseEmitter emitter;

	/**
	 * Creates a non-streaming instance without progress emission.
	 * @param remoteAgentCardRegistry registry for downstream agent cards
	 */
	public RemoteAgentTools(RemoteAgentCardRegistry remoteAgentCardRegistry) {
		this(remoteAgentCardRegistry, null);
	}

	/**
	 * Creates a streaming instance that emits {@code progress} SSE events before each
	 * downstream call.
	 * @param remoteAgentCardRegistry registry for downstream agent cards
	 * @param emitter SSE emitter to send progress events to, or {@code null} to skip
	 */
	public RemoteAgentTools(RemoteAgentCardRegistry remoteAgentCardRegistry, @Nullable SseEmitter emitter) {
		this.remoteAgentCardRegistry = remoteAgentCardRegistry;
		this.emitter = emitter;
	}

	/**
	 * Sends one user message derived from {@link RemoteAgentDelegationRequest#task()} to
	 * the agent whose {@link AgentCard#name()} equals
	 * {@link RemoteAgentDelegationRequest#agentName()}.
	 *
	 * <p>
	 * Emits a {@code progress} SSE event before the call when an emitter is present.
	 * Blocks until {@link A2ATransport#sendStream(AgentCard, Message)} completes or
	 * fails.
	 * </p>
	 * @param request non-null delegation target and task text
	 * @return downstream text, or a short English error line from
	 * {@link #unknownAgentMessage(String)} if no card matches {@code agentName}
	 */
	@Tool(description = "한 원격 에이전트에 한 건의 작업만 위임합니다. 특정 전문 에이전트에게 맡길 때 사용하세요.")
	public String delegateToRemoteAgent(@ToolParam(
			description = "단일 위임 요청. 에이전트 이름(agentName)과 작업 설명(task)을 포함합니다.") RemoteAgentDelegationRequest request) {
		Assert.notNull(request, "request must not be null");

		String agentName = request.agentName();
		String task = request.task();

		AgentCard agentCard = remoteAgentCardRegistry.findCardByAgentName(agentName);
		if (agentCard == null) {
			return unknownAgentMessage(agentName);
		}

		sendProgress("Calling " + agentName + "...");
		Message message = A2A.toUserMessage(task);
		return A2ATransport.sendStream(agentCard, message);
	}

	/**
	 * User-facing error when resolution by agent name fails; lists names from
	 * {@link RemoteAgentCardRegistry#peekCachedAgentCards()} only (peek cache). Agents
	 * not yet successfully resolved are omitted from the list even if configured.
	 * @param agentName the name the model supplied
	 * @return a single English sentence suitable as tool return text
	 */
	private String unknownAgentMessage(String agentName) {
		String availableAgents = remoteAgentCardRegistry.peekCachedAgentCards()
			.stream()
			.map(AgentCard::name)
			.collect(Collectors.joining(", "));
		return "Agent '%s' not found. Available agents: %s".formatted(agentName, availableAgents);
	}

	/**
	 * Runs {@link #delegateToRemoteAgent} once per list element on
	 * {@link #VIRTUAL_THREAD_EXECUTOR}, waits for all to finish, then formats results in
	 * the same order as {@code requests}.
	 *
	 * <p>
	 * Do not use when outputs must be chained (one result feeds the next); use separate
	 * model turns with {@link #delegateToRemoteAgent} instead. Empty {@code requests}
	 * yields an empty string (after {@link String#trim()}).
	 * </p>
	 * @param requests ordered, non-null, no null elements
	 * @return numbered blocks {@code [n] agent: ... / response: ...} joined with newlines
	 */
	@Tool(description = """
			한 번의 호출로 서로 무관한 여러 위임을 병렬로 실행합니다. \
			한 에이전트의 응답이 다른 에이전트 호출의 입력이 되거나 순서가 중요하면 사용하지 말고 delegateToRemoteAgent를 여러 번 호출하세요. \
			반환값은 요청 순서대로 번호가 붙은 응답 블록들의 집계 텍스트입니다.""")
	public String delegateToRemoteAgentsParallel(@ToolParam(
			description = "병렬 위임 요청. 에이전트 이름(agentName)과 작업 설명(task)을 포함합니다.") List<RemoteAgentDelegationRequest> requests) {
		Assert.notNull(requests, "requests must not be null");
		Assert.noNullElements(requests, "requests must contain non-null elements");

		List<CompletableFuture<String>> futures = new ArrayList<>(requests.size());
		for (RemoteAgentDelegationRequest request : requests) {
			futures.add(CompletableFuture.supplyAsync(() -> delegateToRemoteAgent(request), VIRTUAL_THREAD_EXECUTOR));
		}
		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

		StringBuilder out = new StringBuilder();
		for (int i = 0; i < requests.size(); i++) {
			RemoteAgentDelegationRequest request = requests.get(i);
			String name = request.agentName();
			String body = futures.get(i).join();
			out.append("[%d] agent: %s%nresponse:%n%s%n%n".formatted(i + 1, name, body));
		}
		return out.toString().trim();
	}

	/**
	 * Emits a {@code progress} SSE event to the client. No-op when no emitter is present
	 * or the client has already disconnected.
	 * @param message progress text to send
	 */
	private void sendProgress(String message) {
		if (emitter == null) {
			return;
		}
		try {
			emitter.send(SseEmitter.event().name("progress").data(message));
		}
		catch (IOException e) {
			log.warn("Client disconnected during progress emit: {}", message);
		}
	}

}
