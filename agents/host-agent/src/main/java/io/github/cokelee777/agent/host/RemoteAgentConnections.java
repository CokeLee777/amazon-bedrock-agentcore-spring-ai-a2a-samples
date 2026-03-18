package io.github.cokelee777.agent.host;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.github.cokelee777.agent.common.A2ATransport;
import io.github.cokelee777.agent.common.LazyAgentCard;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Spring AI tool component that routes requests to downstream A2A agents.
 *
 * <p>
 * Replaces the three separate {@code *AgentTool} classes with a single
 * {@link #sendMessage} tool that dispatches by {@code agentName}.
 * </p>
 *
 * <p>
 * Uses {@link LazyAgentCard} per agent so that cards not resolved at startup are retried
 * on the first actual invocation.
 * </p>
 */
@Slf4j
@Component
public class RemoteAgentConnections {

	/** Keyed by config key (e.g., {@code "order-agent"}). */
	private final Map<String, LazyAgentCard> lazyCards = new ConcurrentHashMap<>();

	/**
	 * Initialises one {@link LazyAgentCard} per downstream agent configured in
	 * {@link RemoteAgentProperties}.
	 * @param properties the remote agent connection properties
	 */
	public RemoteAgentConnections(RemoteAgentProperties properties) {
		properties.agents().forEach((key, value) -> lazyCards.put(key, new LazyAgentCard(value.url())));
	}

	/**
	 * Sends {@code task} to the downstream agent named {@code agentName} and returns its
	 * response.
	 *
	 * <p>
	 * Looks up the agent by card name. If not yet loaded, {@link LazyAgentCard#get()}
	 * retries resolution automatically.
	 * </p>
	 * @param agentName the target agent name
	 * @param task the comprehensive task description and context to send to the agent
	 * @return the agent's text response, or an error message if routing fails
	 */
	@Tool(description = "원격 에이전트에 작업을 위임합니다. 전문 에이전트에게 작업을 분배할 때 사용하세요.")
	public String sendMessage(@ToolParam(description = "작업을 위임할 에이전트의 이름") String agentName,
			@ToolParam(description = "에이전트에게 전달할 포괄적인 작업 설명 및 컨텍스트") String task) {
		AgentCard agentCard = findByName(agentName);
		if (agentCard == null) {
			String availableAgents = loadedCards().stream().map(AgentCard::name).collect(Collectors.joining(", "));
			return String.format("Agent '%s' not found. Available agents: %s", agentName, availableAgents);
		}

		Message message = A2A.toUserMessage(task);
		return A2ATransport.send(agentCard, message);
	}

	/**
	 * Returns a JSON-formatted description of all available agents for the system prompt.
	 */
	public String getAgentDescriptions() {
		return loadedCards().stream()
			.map(card -> String.format("{\"name\": \"%s\", \"description\": \"%s\"}", card.name(),
					card.description() != null ? card.description() : "No description"))
			.collect(Collectors.joining("\n"));
	}

	/**
	 * Finds a loaded {@link AgentCard} by its name, triggering lazy resolution for all
	 * unloaded cards.
	 */
	private @Nullable AgentCard findByName(String agentName) {
		return lazyCards.values()
			.stream()
			.map(LazyAgentCard::get)
			.flatMap(java.util.Optional::stream)
			.filter(card -> agentName.equals(card.name()))
			.findFirst()
			.orElse(null);
	}

	private List<AgentCard> loadedCards() {
		return lazyCards.values().stream().map(LazyAgentCard::peek).flatMap(java.util.Optional::stream).toList();
	}

}