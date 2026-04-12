package io.github.cokelee777.agent.order.remote;

import io.a2a.A2A;
import io.a2a.spec.Message;
import io.github.cokelee777.a2a.agent.common.A2ATransport;
import io.github.cokelee777.a2a.agent.common.LazyAgentCard;
import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * A2A client for communicating with the Delivery Agent.
 *
 * <p>
 * Uses {@link LazyAgentCard} to resolve the Delivery Agent's card on startup, retrying on
 * first use if startup resolution fails.
 * </p>
 *
 * <p>
 * Registered when {@code spring.ai.a2a.remote.agents.delivery-agent.url} is set.
 * </p>
 */
@Component
public class DeliveryAgentClient {

	private final LazyAgentCard lazyCard;

	/**
	 * Creates a client targeting the delivery agent via the shared registry.
	 * @param remoteAgentCardRegistry registry of configured downstream agents
	 */
	public DeliveryAgentClient(RemoteAgentCardRegistry remoteAgentCardRegistry) {
		Assert.notNull(remoteAgentCardRegistry, "remoteAgentCardRegistry must not be null");

		lazyCard = remoteAgentCardRegistry.findLazyCardByAgentName("delivery-agent");
	}

	/**
	 * Sends {@code task} to the Delivery Agent and returns the text response.
	 * @param task the task description to send
	 * @return the delivery agent's response, or an error message if unavailable
	 */
	public String send(String task) {
		Assert.notNull(task, "task must not be null");

		return lazyCard.get().map(card -> {
			Message message = A2A.toAgentMessage(task);
			return A2ATransport.sendStream(card, message);
		}).orElse("배송 에이전트에 연결할 수 없습니다.");
	}

}
