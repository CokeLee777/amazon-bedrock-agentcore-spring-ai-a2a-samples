package io.github.cokelee777.agent.delivery;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.TransportProtocol;
import io.github.cokelee777.a2a.agent.common.util.TextExtractor;
import io.github.cokelee777.a2a.server.executor.StreamingAgentExecutor;
import io.github.cokelee777.a2a.server.executor.StreamingChatClientExecutorHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * A2A delivery agent beans: {@link AgentCard}, {@link ChatClient}, and
 * {@link AgentExecutor}.
 */
@Configuration
public class DeliveryAgentConfiguration {

	private static final String SYSTEM_PROMPT = """
			당신은 배송 조회 전문 에이전트입니다.
			전체 배송 목록 조회 요청에는 별도의 입력 없이 getDeliveryList 툴을 즉시 호출하여 전체 목록을 반환합니다.
			특정 운송장번호 조회 요청에는 trackDelivery 툴로 해당 배송 상태를 반환합니다.
			""";

	/**
	 * Builds the agent card advertising the delivery agent's skills.
	 * @param agentUrl base URL of this agent; injected from
	 * {@code spring.ai.a2a.server.url}
	 * @return the fully constructed {@link AgentCard}
	 */
	@Bean
	public AgentCard agentCard(@Value("${spring.ai.a2a.server.url}") String agentUrl) {
		return new AgentCard.Builder().name("Delivery Agent")
			.description("전체 배송 목록 조회 및 운송장번호로 배송 상태를 추적하는 에이전트")
			.url(agentUrl)
			.additionalInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(true).pushNotifications(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of(
					new AgentSkill.Builder().id("list_deliveries")
						.name("전체 배송 목록 조회")
						.description("모든 운송장의 현재 배송 상태 목록을 반환합니다")
						.tags(List.of("delivery", "list"))
						.build(),
					new AgentSkill.Builder().id("track_delivery")
						.name("배송 조회")
						.description("운송장번호로 현재 배송 상태를 반환합니다")
						.tags(List.of("delivery", "tracking"))
						.build()))
			.build();
	}

	/**
	 * Configures the {@link ChatClient} with the delivery system prompt and
	 * {@link DeliveryTools}.
	 * @param builder the Spring AI auto-configured {@link ChatClient.Builder}
	 * @param tools the delivery tool methods
	 * @return a configured {@link ChatClient}
	 */
	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, DeliveryTools tools) {
		return builder.clone().defaultSystem(SYSTEM_PROMPT).defaultTools(tools).build();
	}

	/** AgentExecutor backed by {@link StreamingAgentExecutor}. */
	@Bean
	public AgentExecutor agentExecutor(ChatClient chatClient) {
		StreamingChatClientExecutorHandler handler = (chat, ctx) -> {
			String msg = TextExtractor.extractTextFromMessage(ctx.getMessage());
			return chat.prompt().user(msg).stream().content();
		};
		return new StreamingAgentExecutor(chatClient, handler);
	}

}
