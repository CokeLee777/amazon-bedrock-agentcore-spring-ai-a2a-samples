package io.github.cokelee777.agent.payment;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.TransportProtocol;
import io.github.cokelee777.a2a.agent.common.util.TextExtractor;
import io.github.cokelee777.a2a.server.executor.ChatClientExecutorHandler;
import io.github.cokelee777.a2a.server.executor.DefaultAgentExecutor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * A2A payment agent beans: {@link AgentCard}, {@link ChatClient}, and
 * {@link AgentExecutor}.
 */
@Configuration
public class PaymentAgentConfiguration {

	private static final String SYSTEM_PROMPT = """
			당신은 결제 조회 전문 에이전트입니다.
			전체 결제 목록 조회 요청에는 별도의 입력 없이 getPaymentList 툴을 즉시 호출하여 전체 목록을 반환합니다.
			특정 주문번호 조회 요청에는 getPaymentStatus 툴로 해당 결제/환불 상태를 반환합니다.
			""";

	/**
	 * Builds the agent card advertising the payment agent's skills.
	 * @param agentUrl base URL of this agent; injected from
	 * {@code spring.ai.a2a.server.url}
	 * @return the fully constructed {@link AgentCard}
	 */
	@Bean
	public AgentCard agentCard(@Value("${spring.ai.a2a.server.url}") String agentUrl) {
		return new AgentCard.Builder().name("Payment Agent")
			.description("전체 결제 목록 조회 및 주문번호로 결제/환불 상태를 확인하는 에이전트")
			.url(agentUrl)
			.additionalInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(false).pushNotifications(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of(
					new AgentSkill.Builder().id("list_payments")
						.name("전체 결제 목록 조회")
						.description("모든 주문의 현재 결제/환불 상태 목록을 반환합니다")
						.tags(List.of("payment", "list"))
						.build(),
					new AgentSkill.Builder().id("payment_status")
						.name("결제 상태 조회")
						.description("주문번호로 결제 및 환불 상태를 반환합니다")
						.tags(List.of("payment", "refund"))
						.build()))
			.build();
	}

	/**
	 * Configures the {@link ChatClient} with the payment system prompt and
	 * {@link PaymentTools}.
	 * @param builder the Spring AI auto-configured {@link ChatClient.Builder}
	 * @param tools the payment tool methods
	 * @return a configured {@link ChatClient}
	 */
	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, PaymentTools tools) {
		return builder.clone().defaultSystem(SYSTEM_PROMPT).defaultTools(tools).build();
	}

	/**
	 * Creates the {@link AgentExecutor} using {@link DefaultAgentExecutor} with
	 * {@link PaymentTools} as the registered tool (via {@link #chatClient}).
	 * @param chatClient the payment {@link ChatClient} bean
	 * @return a configured {@link AgentExecutor}
	 */
	@Bean
	public AgentExecutor agentExecutor(ChatClient chatClient) {
		ChatClientExecutorHandler handler = (chat, ctx) -> {
			String msg = TextExtractor.extractTextFromMessage(ctx.getMessage());
			return chat.prompt().user(msg).call().content();
		};
		return new DefaultAgentExecutor(chatClient, handler);
	}

}
