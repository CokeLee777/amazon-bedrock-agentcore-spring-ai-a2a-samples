package io.github.cokelee777.a2a.orchestrator.config;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Provides the {@link AgentCard} bean that describes this orchestrator to other A2A
 * agents and to the AgentCore Runtime discovery endpoint.
 *
 * <p>
 * The card's {@code url} and JSON-RPC interface URL are taken verbatim from
 * {@code a2a.orchestrator-url} with no normalization—configure the exact base URL callers
 * must use (including path or trailing slash if your deployment requires it).
 * </p>
 */
@Configuration
public class AgentCardConfig {

	/**
	 * Builds the agent card advertising the orchestrator's skills.
	 * @param agentUrl base URL of this orchestrator as seen by AgentCore Runtime and
	 * other A2A callers; injected from {@code a2a.orchestrator-url} and passed through
	 * unchanged into {@link AgentCard.Builder#url()} and {@code additionalInterfaces}
	 * @return the fully constructed {@link AgentCard}
	 */
	@Bean
	public AgentCard agentCard(@Value("${a2a.orchestrator-url}") String agentUrl) {
		return new AgentCard.Builder().name("A2A Orchestrator")
			.description("주문/배송 관련 다중 에이전트를 조율하는 오케스트레이터")
			.url(agentUrl)
			.additionalInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(false).pushNotifications(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of(
					new AgentSkill.Builder().id("order_query")
						.name("주문 조회")
						.description("회원의 주문 목록을 조회합니다")
						.tags(List.of("order", "list"))
						.build(),
					new AgentSkill.Builder().id("order_cancellability")
						.name("주문 취소 가능 여부 확인")
						.description("배송/결제 상태를 병렬 확인하여 취소 가능 여부를 판단합니다")
						.tags(List.of("order", "cancellability"))
						.build(),
					new AgentSkill.Builder().id("delivery_tracking")
						.name("배송 조회")
						.description("운송장번호로 배송 상태를 추적합니다")
						.tags(List.of("delivery", "tracking"))
						.build()))
			.build();
	}

}
