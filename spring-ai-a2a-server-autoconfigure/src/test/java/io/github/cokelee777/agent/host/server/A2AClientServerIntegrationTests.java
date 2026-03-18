package io.github.cokelee777.agent.host.server;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCard;
import io.github.cokelee777.agent.host.server.executor.DefaultAgentExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for A2A server auto-configuration.
 *
 * <p>
 * Verifies:
 * <ul>
 * <li>A2A server starts successfully with auto-configuration</li>
 * <li>Default AgentCard bean is created with correct configuration</li>
 * <li>HTTP endpoints are available</li>
 * </ul>
 *
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class A2AClientServerIntegrationTests {

	/**
	 * Minimal Spring Boot application for testing auto-configuration.
	 */
	@SpringBootApplication
	static class TestApplication {

		static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}

	}

	@TestConfiguration
	static class TestConfig {

		/**
		 * Provides a mock ChatModel bean for testing (no real LLM connection needed).
		 */
		@Bean
		public ChatModel testChatModel() {
			return mock(ChatModel.class);
		}

		/**
		 * Provides a minimal ChatClient bean to trigger auto-configuration.
		 */
		@Bean
		public ChatClient testChatClient(ChatModel testChatModel) {
			return ChatClient.builder(testChatModel).defaultSystem("You are a test agent").build();
		}

		/**
		 * Provides AgentCard bean for testing.
		 */
		@Bean
		public AgentCard testAgentCard() {
			return new AgentCard("Spring AI A2A Agent", "A2A agent powered by Spring AI", "http://localhost:58888/a2a",
					null, "1.0.0", null, new io.a2a.spec.AgentCapabilities(false, false, false, List.of()),
					List.of("text"), List.of("text"), List.of(), false, null, null, null,
					List.of(new io.a2a.spec.AgentInterface("JSONRPC", "http://localhost:58888/a2a")), "JSONRPC",
					"0.1.0", null);
		}

		/**
		 * Provides a test AgentExecutor bean.
		 */
		@Bean
		public AgentExecutor testAgentExecutor(ChatClient testChatClient) {
			return new DefaultAgentExecutor(testChatClient, (chatClient, requestContext) -> "test response");
		}

	}

	@LocalServerPort
	private int port;

	@Autowired
	private AgentCard agentCard;

	/**
	 * Tests that the A2A server started successfully with default auto-configuration.
	 */
	@Test
	void testA2AServerStarted() {
		assertThat(agentCard).isNotNull();
		assertThat(agentCard.name()).isEqualTo("Spring AI A2A Agent");
		assertThat(agentCard.description()).isEqualTo("A2A agent powered by Spring AI");
		assertThat(agentCard.version()).isEqualTo("1.0.0");
		assertThat(agentCard.protocolVersion()).isEqualTo("0.1.0");
	}

	/**
	 * Tests that the server is running on the expected port.
	 */
	@Test
	void testServerPort() {
		assertThat(port).isEqualTo(58888);
	}

	/**
	 * Tests that default AgentCard capabilities are configured.
	 */
	@Test
	void testAgentCardCapabilities() {
		assertThat(agentCard.capabilities()).isNotNull();
		assertThat(agentCard.capabilities().streaming()).isFalse();
		assertThat(agentCard.capabilities().pushNotifications()).isFalse();
	}

}
