package io.github.cokelee777.a2a.agent.common.autoconfigure;

import io.a2a.spec.AgentCard;
import io.github.cokelee777.a2a.agent.common.LazyAgentCard;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry of {@link LazyAgentCard} instances keyed by logical configuration name (e.g.
 * YAML map keys under {@code a2a.remote.agents}). Construct with
 * {@link RemoteAgentProperties}.
 *
 * <p>
 * Thread-safe: uses a {@link ConcurrentHashMap} of lazy cards. Offers lookup by
 * configured agent name under {@code a2a.remote.agents} (see
 * {@link #findLazyCardByAgentName(String)}), routing by {@link AgentCard#name() agent
 * name} from the card (see {@link #findCardByAgentName(String)}), and newline-separated
 * JSON for orchestrator system prompts (see {@link #getAgentDescriptions()}).
 * </p>
 */
public class RemoteAgentCardRegistry {

	private final Map<String, LazyAgentCard> lazyCards = new ConcurrentHashMap<>();

	/**
	 * Builds one {@link LazyAgentCard} per entry in
	 * {@link RemoteAgentProperties#agents()}.
	 * @param properties bound {@code a2a.remote.agents}; must not be {@code null}
	 */
	public RemoteAgentCardRegistry(RemoteAgentProperties properties) {
		Assert.notNull(properties, "properties must not be null");

		properties.agents().forEach((key, value) -> lazyCards.put(key, new LazyAgentCard(value.url())));
	}

	/**
	 * Returns the {@link LazyAgentCard} for an agent name that appears as a key under
	 * {@code a2a.remote.agents} in configuration (e.g. {@code "payment-agent"}). This is
	 * not the same as {@link AgentCard#name()} from the agent card; use
	 * {@link #findCardByAgentName(String)} when resolving the name the model supplies
	 * from the routable agent metadata.
	 * @param agentName key under {@code a2a.remote.agents}
	 * @return the corresponding {@link LazyAgentCard}
	 * @throws IllegalArgumentException if {@code agentName} is not configured
	 */
	public LazyAgentCard findLazyCardByAgentName(String agentName) {
		Assert.hasText(agentName, "agentName must not be empty");

		LazyAgentCard card = lazyCards.get(agentName);
		if (card == null) {
			throw new IllegalArgumentException("No entry under a2a.remote.agents for: " + agentName);
		}
		return card;
	}

	/**
	 * Finds the first resolved {@link AgentCard} whose {@link AgentCard#name() agent
	 * name} equals {@code agentName} (case-sensitive, exact match). This is the routable
	 * name from {@code /.well-known/agent-card.json}, not the {@code a2a.remote.agents}
	 * map key used by {@link #findLazyCardByAgentName(String)}.
	 *
	 * <p>
	 * For each registered {@link LazyAgentCard}, uses {@link LazyAgentCard#peek()} when
	 * populated; otherwise {@link LazyAgentCard#get()} for that entry only. Stops at the
	 * first name match; if multiple agents share a name, which one wins is undefined
	 * because {@link Map#values()} iteration order is not specified.
	 * </p>
	 * @param agentName value from the model; must match the downstream card agent name
	 * @return the matching card, or {@code null} if no configured agent exposes that name
	 */
	public @Nullable AgentCard findCardByAgentName(String agentName) {
		Assert.hasText(agentName, "agentName must not be empty");

		for (LazyAgentCard lazy : lazyCards.values()) {
			Optional<AgentCard> card = lazy.peek();
			if (card.isEmpty()) {
				card = lazy.get();
			}
			if (card.isPresent() && agentName.equals(card.get().name())) {
				return card.get();
			}
		}
		return null;
	}

	/**
	 * Returns a JSON-formatted description of all available agents for the system prompt.
	 *
	 * <p>
	 * Uses {@link LazyAgentCard#peek()} only when at least one card is already cached, so
	 * steady-state invocations avoid redundant fetches. If nothing is cached yet (e.g.
	 * downstream agents were down at startup), attempts {@link LazyAgentCard#get()} for
	 * each configured agent so the first user turn sees up-to-date routable agents.
	 * </p>
	 * @return newline-separated JSON objects {@code {"name": "...", "description":
	 * "..."}} for each included card; empty string when no agent could be resolved
	 */
	public String getAgentDescriptions() {
		return resolveAgentCardsForDescriptions().stream()
			.map(card -> String.format("{\"name\": \"%s\", \"description\": \"%s\"}", card.name(),
					Objects.requireNonNullElse(card.description(), "No description")))
			.collect(Collectors.joining("\n"));
	}

	/**
	 * Resolves which {@link AgentCard} instances to include when building
	 * {@link #getAgentDescriptions()}.
	 * @return {@link #peekCachedAgentCards()} when non-empty; otherwise the result of
	 * calling {@link LazyAgentCard#get()} on every registered lazy card (maybe a subset
	 * if some agents remain unreachable)
	 */
	private List<AgentCard> resolveAgentCardsForDescriptions() {
		List<AgentCard> cached = peekCachedAgentCards();
		if (!cached.isEmpty()) {
			return cached;
		}
		return lazyCards.values().stream().map(LazyAgentCard::get).flatMap(Optional::stream).toList();
	}

	/**
	 * Snapshot of cards already in memory without triggering network I/O.
	 * @return all non-empty {@link LazyAgentCard#peek()} results (order follows
	 * {@link Map#values()})
	 */
	public List<AgentCard> peekCachedAgentCards() {
		return lazyCards.values().stream().map(LazyAgentCard::peek).flatMap(Optional::stream).toList();
	}

}
