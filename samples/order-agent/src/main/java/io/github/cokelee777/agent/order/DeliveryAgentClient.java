package io.github.cokelee777.agent.order;

import io.a2a.A2A;
import io.a2a.spec.Message;
import io.github.cokelee777.agent.common.A2ATransport;
import io.github.cokelee777.agent.common.LazyAgentCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * A2A client for communicating with the Delivery Agent.
 *
 * <p>
 * Uses {@link LazyAgentCard} to resolve the Delivery Agent's card on startup, retrying on
 * first use if startup resolution fails.
 * </p>
 */
@Slf4j
@Component
public class DeliveryAgentClient {

	private final LazyAgentCard lazyCard;

	/**
	 * Creates a client targeting the delivery agent URL from properties.
	 * @param properties the remote agent connection properties
	 */
	public DeliveryAgentClient(RemoteAgentProperties properties) {
		Assert.notNull(properties, "properties must not be null");

		lazyCard = new LazyAgentCard(properties.agents().get("delivery-agent").url());
	}

	/**
	 * Sends {@code task} to the Delivery Agent and returns the text response.
	 * @param task the task description to send
	 * @return the delivery agent's response, or an error message if unavailable
	 */
	public String send(String task) {
		Assert.notNull(task, "task must not be null");

		return lazyCard.get().map(card -> {
			Message message = A2A.toUserMessage(task);
			return A2ATransport.send(card, message);
		}).orElse("배송 에이전트에 연결할 수 없습니다.");
	}

}
