package io.github.cokelee777.agent.order;

import io.a2a.A2A;
import io.a2a.spec.Message;
import io.github.cokelee777.agent.common.A2ATransport;
import io.github.cokelee777.agent.common.LazyAgentCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * A2A client for communicating with the Payment Agent.
 *
 * <p>
 * Uses {@link LazyAgentCard} to resolve the Payment Agent's card on startup, retrying on
 * first use if startup resolution fails.
 * </p>
 */
@Slf4j
@Component
public class PaymentAgentClient {

	private final LazyAgentCard lazyCard;

	/**
	 * Creates a client targeting the payment agent URL from properties.
	 * @param properties the remote agent connection properties
	 */
	public PaymentAgentClient(RemoteAgentProperties properties) {
		lazyCard = new LazyAgentCard(properties.agents().get("payment-agent").url());
	}

	/**
	 * Sends {@code task} to the Payment Agent and returns the text response.
	 * @param task the task description to send
	 * @return the payment agent's response, or an error message if unavailable
	 */
	public String send(String task) {
		Assert.hasText(task, "task must not be blank");

		return lazyCard.get().map(card -> {
			Message message = A2A.toUserMessage(task);
			return A2ATransport.send(card, message);
		}).orElse("결제 에이전트에 연결할 수 없습니다.");
	}

}
