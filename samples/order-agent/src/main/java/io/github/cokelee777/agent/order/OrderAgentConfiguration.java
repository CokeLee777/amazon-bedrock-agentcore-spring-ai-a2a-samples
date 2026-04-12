package io.github.cokelee777.agent.order;

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
 * A2A order agent beans: {@link AgentCard}, {@link ChatClient}, and
 * {@link AgentExecutor}.
 */
@Configuration
public class OrderAgentConfiguration {

	private static final String SYSTEM_PROMPT = """
			당신은 주문 조회 전문 에이전트입니다.
			주문 내역 조회는 별도의 입력 없이 getOrderList 툴을 즉시 호출하여 전체 주문 목록을 반환합니다. 사용자에게 추가 정보를 요청하지 마세요.
			취소 가능 여부 확인 시 주문번호를 받아 배송 에이전트(배송 상태)와 결제 에이전트(결제 상태)를 모두 조회하여 종합적으로 판단합니다.
			실제 취소나 환불 처리는 수행하지 않으며, 조회 및 판단만 제공합니다.
			""";

	/**
	 * Builds the agent card advertising the order agent's skills.
	 * @param agentUrl base URL of this agent; injected from
	 * {@code spring.ai.a2a.server.url}
	 * @return the fully constructed {@link AgentCard}
	 */
	@Bean
	public AgentCard agentCard(@Value("${spring.ai.a2a.server.url}") String agentUrl) {
		return new AgentCard.Builder().name("Order Agent")
			.description("주문 내역 조회 및 취소 가능 여부를 확인하는 에이전트. 배송 에이전트와 결제 에이전트를 내부적으로 호출하여 통합 정보를 제공합니다.")
			.url(agentUrl)
			.additionalInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(true).pushNotifications(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of(
					new AgentSkill.Builder().id("order_list")
						.name("주문 조회")
						.description("회원의 주문 목록을 조회합니다")
						.tags(List.of("order", "list"))
						.build(),
					new AgentSkill.Builder().id("order_cancellability_check")
						.name("주문 취소 가능 여부 확인")
						.description("배송/결제 상태를 확인하여 취소 가능 여부를 판단합니다")
						.tags(List.of("order", "cancellability"))
						.build()))
			.build();
	}

	/**
	 * Configures the {@link ChatClient} with the order system prompt and
	 * {@link OrderTools}.
	 * @param builder the Spring AI auto-configured {@link ChatClient.Builder}
	 * @param tools the order tool methods
	 * @return a configured {@link ChatClient}
	 */
	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, OrderTools tools) {
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
