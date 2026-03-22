package io.github.cokelee777.agent.common;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.Optional;

/**
 * Lazily loads and caches an {@link AgentCard} from a downstream A2A agent URL.
 *
 * <p>
 * Attempts to fetch the {@link AgentCard} immediately on construction. If the fetch fails
 * (e.g., the downstream agent is not yet available), the URL is retained and
 * {@link #get()} retries the fetch on every subsequent call until it succeeds.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * LazyAgentCard lazyCard = new LazyAgentCard("http://localhost:9002");
 * lazyCard.get().ifPresentOrElse(
 *     card -> A2ATransport.send(card, message),
 *     () -> log.warn("Agent not available yet")
 * );
 * }</pre>
 */
@Slf4j
public class LazyAgentCard {

	private final String agentUrl;

	private @Nullable volatile AgentCard card;

	/**
	 * Creates a {@link LazyAgentCard} for the given URL and immediately attempts to fetch
	 * the {@link AgentCard}.
	 * @param agentUrl the base URL of the downstream A2A agent
	 */
	public LazyAgentCard(String agentUrl) {
		Assert.notNull(agentUrl, "agentUrl must not be null");

		this.agentUrl = agentUrl;
		tryFetchAgentCard();
	}

	/**
	 * Returns the cached {@link AgentCard}, retrying the fetch if not yet loaded.
	 *
	 * <p>
	 * If the card was not successfully loaded at construction time, this method attempts
	 * to fetch it again on every call until it succeeds. Use this method when the caller
	 * intends to actually communicate with the agent (e.g., inside {@code sendMessage}).
	 * For read-only status checks use {@link #peek()} to avoid redundant network calls.
	 * </p>
	 * @return an {@link Optional} containing the {@link AgentCard}, or empty if the agent
	 * is still unreachable
	 */
	public Optional<AgentCard> get() {
		if (card == null) {
			tryFetchAgentCard();
		}
		return Optional.ofNullable(card);
	}

	/**
	 * Returns the currently cached {@link AgentCard} without triggering a fetch.
	 *
	 * <p>
	 * Unlike {@link #get()}, this method never issues a network request. It is intended
	 * for informational queries (e.g., building system prompts, listing agent names)
	 * where a missing card should simply be omitted rather than retried.
	 * </p>
	 * @return an {@link Optional} containing the {@link AgentCard}, or empty if not yet
	 * loaded
	 */
	public Optional<AgentCard> peek() {
		return Optional.ofNullable(card);
	}

	private void tryFetchAgentCard() {
		try {
			String path = new URI(agentUrl).getPath();
			AgentCard fetchedCard = A2A.getAgentCard(agentUrl, path + ".well-known/agent-card.json", null);

			log.info("Resolved agent card '{}' from {}", fetchedCard.name(), agentUrl);

			card = fetchedCard;
		}
		catch (Exception e) {
			log.warn("Failed to resolve agent card from {}: {}", agentUrl, e.getMessage());
		}
	}

}
