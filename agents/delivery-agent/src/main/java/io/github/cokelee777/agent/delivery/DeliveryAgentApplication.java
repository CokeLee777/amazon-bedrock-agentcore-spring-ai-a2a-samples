package io.github.cokelee777.agent.delivery;

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
 * Spring Boot application entry point for the Delivery A2A agent.
 *
 * <p>
 * Handles the {@code track_delivery} skill. Declares {@link AgentCard} and
 * {@link AgentExecutor} beans directly, following the reference weather-agent style.
 * </p>
 */
@SpringBootApplication
public class DeliveryAgentApplication {

	private static final String SYSTEM_PROMPT = "당신은 배송 조회 전문 에이전트입니다. 운송장번호를 기반으로 배송 상태를 추적합니다.";

	/**
	 * Starts the Delivery Agent.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(DeliveryAgentApplication.class, args);
	}

	/**
	 * Builds the agent card advertising the delivery agent's skills.
	 * @param agentUrl base URL of this agent; injected from {@code a2a.agent-url}
	 * @return the fully constructed {@link AgentCard}
	 */
	@Bean
	public AgentCard agentCard(@Value("${a2a.agent-url}") String agentUrl) {
		return new AgentCard.Builder().name("Delivery Agent")
			.description("운송장번호로 배송 상태를 추적하는 에이전트")
			.url(agentUrl)
			.additionalInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)))
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(false).pushNotifications(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of(new AgentSkill.Builder().id("track_delivery")
				.name("배송 조회")
				.description("운송장번호로 현재 배송 상태를 반환합니다")
				.tags(List.of("delivery", "tracking"))
				.build()))
			.build();
	}

	/**
	 * Creates the {@link AgentExecutor} using {@link DefaultAgentExecutor} with
	 * {@link DeliveryTools} as the registered tool.
	 * @param builder the Spring AI auto-configured {@link ChatClient.Builder}
	 * @param tools the delivery tool methods
	 * @return a configured {@link AgentExecutor}
	 */
	@Bean
	public AgentExecutor agentExecutor(ChatClient.Builder builder, DeliveryTools tools) {
		ChatClient chatClient = builder.clone().defaultSystem(SYSTEM_PROMPT).defaultTools(tools).build();
		ChatClientExecutorHandler handler = (chat, ctx) -> {
			String msg = TextExtractor.extractTextFromMessage(ctx.getMessage());
			return chat.prompt().user(msg).call().content();
		};
		return new DefaultAgentExecutor(chatClient, handler);
	}

}
