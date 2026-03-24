package io.github.cokelee777.agent.host.remote;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.github.cokelee777.a2a.agent.common.A2ATransport;
import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Spring {@link Component} that registers the host's downstream A2A agents as Spring AI
 * {@link Tool}-annotated methods, routing orchestration calls over JSON-RPC via
 * {@link A2ATransport}.
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
 * Downstream descriptions for the orchestrator system prompt come from
 * {@link RemoteAgentCardRegistry#getAgentDescriptions()} (see invocation layer).
 * </p>
 *
 * <p>
 * Tool methods are synchronous from the model's perspective: {@link #sendMessage} blocks
 * until the downstream task completes; {@link #sendMessagesParallel} blocks until every
 * delegated call finishes (each runs on a virtual thread).
 * </p>
 *
 * <p>
 * Instances are safe for concurrent use; the registry uses a concurrent map and
 * {@link io.github.cokelee777.a2a.agent.common.LazyAgentCard} coordinates resolution per
 * URL.
 * </p>
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteAgentTools {

	/**
	 * Runs one downstream delegation per virtual thread for {@link #sendMessagesParallel}
	 * without tying up platform thread pools.
	 */
	private static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	private final RemoteAgentCardRegistry remoteAgentCardRegistry;

	/**
	 * Sends one user message derived from {@link RemoteAgentDelegationRequest#task()} to
	 * the agent whose {@link AgentCard#name()} equals
	 * {@link RemoteAgentDelegationRequest#agentName()}.
	 *
	 * <p>
	 * Resolves the card via {@link RemoteAgentCardRegistry#findCardByAgentName(String)}.
	 * Blocks until {@link A2ATransport#send(AgentCard, Message)} completes or fails.
	 * </p>
	 * @param request non-null delegation target and task text
	 * @return downstream text, or a short English error line from
	 * {@link #unknownAgentMessage(String)} if no card matches {@code agentName}
	 */
	@Tool(description = "한 원격 에이전트에 한 건의 작업만 위임합니다. 특정 전문 에이전트에게 맡길 때 사용하세요.")
	public String sendMessage(@ToolParam(
			description = "단일 위임 요청. 에이전트 이름(agentName)과 작업 설명(task)을 포함합니다.") RemoteAgentDelegationRequest request) {
		Assert.notNull(request, "request must not be null");

		String agentName = request.agentName();
		String task = request.task();

		AgentCard agentCard = remoteAgentCardRegistry.findCardByAgentName(agentName);
		if (agentCard == null) {
			return unknownAgentMessage(agentName);
		}

		Message message = A2A.toUserMessage(task);
		return A2ATransport.send(agentCard, message);
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
	 * Runs {@link #sendMessage} once per list element on
	 * {@link #VIRTUAL_THREAD_EXECUTOR}, waits for all to finish, then formats results in
	 * the same order as {@code requests}.
	 *
	 * <p>
	 * Do not use when outputs must be chained (one result feeds the next); use separate
	 * model turns with {@link #sendMessage} instead. Empty {@code requests} yields an
	 * empty string (after {@link String#trim()}).
	 * </p>
	 * @param requests ordered, non-null, no null elements
	 * @return numbered blocks {@code [n] agent: ... / response: ...} joined with newlines
	 */
	@Tool(description = """
			한 번의 호출로 서로 무관한 여러 위임을 병렬로 실행합니다. \
			한 에이전트의 응답이 다른 에이전트 호출의 입력이 되거나 순서가 중요하면 사용하지 말고 sendMessage를 여러 번 호출하세요. \
			반환값은 요청 순서대로 번호가 붙은 응답 블록들의 집계 텍스트입니다.""")
	public String sendMessagesParallel(@ToolParam(
			description = "병렬 위임 요청. 에이전트 이름(agentName)과 작업 설명(task)을 포함합니다.") List<RemoteAgentDelegationRequest> requests) {
		Assert.notNull(requests, "requests must not be null");
		Assert.noNullElements(requests, "requests must contain non-null elements");

		List<CompletableFuture<String>> futures = new ArrayList<>(requests.size());
		for (RemoteAgentDelegationRequest request : requests) {
			futures.add(CompletableFuture.supplyAsync(() -> sendMessage(request), VIRTUAL_THREAD_EXECUTOR));
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

}
