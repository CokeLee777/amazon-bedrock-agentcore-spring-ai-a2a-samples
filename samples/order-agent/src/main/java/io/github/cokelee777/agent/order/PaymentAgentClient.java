package io.github.cokelee777.agent.order;

import io.a2a.A2A;
import io.a2a.spec.Message;
import io.github.cokelee777.a2a.agent.common.A2ATransport;
import io.github.cokelee777.a2a.agent.common.LazyAgentCard;
import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * A2A client for communicating with the Payment Agent.
 *
 * <p>
 * Uses {@link LazyAgentCard} to resolve the Payment Agent's card on startup, retrying on
 * first use if startup resolution fails.
 * </p>
 *
 * <p>
 * Registered when {@code a2a.remote.agents.payment-agent.url} is set.
 * </p>
 */
@Component
public class PaymentAgentClient {

	private final LazyAgentCard lazyCard;

	/**
	 * Creates a client targeting the payment agent via the shared registry.
	 * @param remoteAgentCardRegistry registry of configured downstream agents
	 */
	public PaymentAgentClient(RemoteAgentCardRegistry remoteAgentCardRegistry) {
		Assert.notNull(remoteAgentCardRegistry, "remoteAgentCardRegistry must not be null");

		lazyCard = remoteAgentCardRegistry.findLazyCardByAgentName("payment-agent");
	}

	/**
	 * Sends {@code task} to the Payment Agent and returns the text response.
	 * @param task the task description to send
	 * @return the payment agent's response, or an error message if unavailable
	 */
	public String send(String task) {
		Assert.notNull(task, "task must not be null");

		return lazyCard.get().map(card -> {
			Message message = A2A.toAgentMessage(task);
			return A2ATransport.send(card, message);
		}).orElse("결제 에이전트에 연결할 수 없습니다.");
	}

}
