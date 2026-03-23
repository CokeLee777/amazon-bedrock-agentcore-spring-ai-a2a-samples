package io.github.cokelee777.agent.host;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the A2A Orchestrator service.
 *
 * <p>
 * Runs on port 8080. Receives requests from Amazon Bedrock AgentCore Runtime via
 * {@code POST /invocations} and coordinates downstream order, delivery, and payment
 * agents via LLM tool-calling ({@link RemoteAgentConnections}).
 * </p>
 *
 * <p>
 * The system prompt is applied per request in {@link InvocationController#invoke} so
 * that agent descriptions always reflect the latest loaded {@link io.a2a.spec.AgentCard
 * AgentCards}.
 * </p>
 */
@SpringBootApplication
@EnableConfigurationProperties(RemoteAgentProperties.class)
public class HostAgentApplication {

	/**
	 * Starts the Orchestrator.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(HostAgentApplication.class, args);
	}

	/**
	 * Builds the routing {@link ChatClient} with downstream A2A agents registered as
	 * tools.
	 * @param builder the Spring AI auto-configured builder
	 * @param connections the downstream agent tool component
	 * @return the configured {@link ChatClient}
	 */
	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, RemoteAgentConnections connections) {
		return builder.clone().defaultTools(connections).defaultAdvisors(new SimpleLoggerAdvisor()).build();
	}

}
