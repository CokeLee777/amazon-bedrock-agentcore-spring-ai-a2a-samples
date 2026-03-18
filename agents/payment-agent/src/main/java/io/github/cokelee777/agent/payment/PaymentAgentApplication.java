package io.github.cokelee777.agent.payment;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.TransportProtocol;
import io.github.cokelee777.agent.host.server.executor.ChatClientExecutorHandler;
import io.github.cokelee777.agent.host.server.executor.DefaultAgentExecutor;
import io.github.cokelee777.agent.common.util.TextExtractor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Spring Boot application entry point for the Payment A2A agent.
 *
 * <p>
 * Handles the {@code payment_status} skill for payment and refund status queries.
 * Declares {@link AgentCard} and {@link AgentExecutor} beans directly, following the
 * reference weather-agent style.
 * </p>
 */
@SpringBootApplication
public class PaymentAgentApplication {

	private static final String SYSTEM_PROMPT = "당신은 결제 조회 전문 에이전트입니다. 주문번호로 결제 및 환불 상태를 확인합니다.";

	/**
	 * Starts the Payment Agent.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(PaymentAgentApplication.class, args);
	}

	/**
	 * Builds the agent card advertising the payment agent's skills.
	 * @param agentUrl base URL of this agent; injected from {@code a2a.agent-url}
	 * @return the fully constructed {@link AgentCard}
	 */
	@Bean
	public AgentCard agentCard(@Value("${a2a.agent-url}") String agentUrl) {
		return new AgentCard.Builder().name("Payment Agent")
			.description("주문번호로 결제 및 환불 상태를 확인하는 에이전트")
			.url(agentUrl)
			.additionalInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(false).pushNotifications(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of(new AgentSkill.Builder().id("payment_status")
				.name("결제 상태 조회")
				.description("주문번호로 결제 및 환불 상태를 반환합니다")
				.tags(List.of("payment", "refund"))
				.build()))
			.build();
	}

	/**
	 * Creates the {@link AgentExecutor} using {@link DefaultAgentExecutor} with
	 * {@link PaymentTools} as the registered tool.
	 * @param builder the Spring AI auto-configured {@link ChatClient.Builder}
	 * @param tools the payment tool methods
	 * @return a configured {@link AgentExecutor}
	 */
	@Bean
	public AgentExecutor agentExecutor(ChatClient.Builder builder, PaymentTools tools) {
		ChatClient chatClient = builder.clone().defaultSystem(SYSTEM_PROMPT).defaultTools(tools).build();
		ChatClientExecutorHandler handler = (chat, ctx) -> {
			String msg = TextExtractor.extractTextFromMessage(ctx.getMessage());
			return chat.prompt().user(msg).call().content();
		};
		return new DefaultAgentExecutor(chatClient, handler);
	}

}
