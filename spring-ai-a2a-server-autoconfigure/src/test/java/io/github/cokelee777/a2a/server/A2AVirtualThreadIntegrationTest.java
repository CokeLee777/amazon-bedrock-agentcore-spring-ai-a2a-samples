package io.github.cokelee777.a2a.server;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCard;
import io.github.cokelee777.a2a.server.executor.DefaultAgentExecutor;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration test verifying that A2A agent execution runs on virtual threads.
 *
 * <p>
 * Sends a real A2A {@code message/send} JSON-RPC request and captures
 * {@link Thread#isVirtual()} from inside the {@link AgentExecutor#execute} call, which is
 * the code path driven by the {@code a2aTaskExecutor} bean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class A2AVirtualThreadIntegrationTest {

	/**
	 * Minimal Spring Boot application for bootstrapping auto-configuration.
	 */
	@SpringBootApplication
	static class TestApplication {

		static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}

	}

	/**
	 * Holds thread metadata captured during agent execution.
	 */
	static class ExecutionCapture {

		final AtomicBoolean isVirtual = new AtomicBoolean(false);

		final AtomicBoolean executed = new AtomicBoolean(false);

		final AtomicReference<String> threadName = new AtomicReference<>();

	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		public ChatModel testChatModel() {
			return mock(ChatModel.class);
		}

		@Bean
		public ChatClient testChatClient(ChatModel testChatModel) {
			return ChatClient.builder(testChatModel).defaultSystem("You are a test agent").build();
		}

		@Bean
		public AgentCard testAgentCard() {
			return new AgentCard("Test Agent", "Virtual thread test agent", "http://localhost/", null, "1.0.0", null,
					new io.a2a.spec.AgentCapabilities(false, false, false, List.of()), List.of("text"), List.of("text"),
					List.of(), false, null, null, null,
					List.of(new io.a2a.spec.AgentInterface("JSONRPC", "http://localhost/")), "JSONRPC", "0.1.0", null);
		}

		@Bean
		public ExecutionCapture executionCapture() {
			return new ExecutionCapture();
		}

		@Bean
		public AgentExecutor testAgentExecutor(ChatClient testChatClient, ExecutionCapture capture) {
			return new DefaultAgentExecutor(testChatClient, (chatClient, requestContext) -> {
				Thread current = Thread.currentThread();
				capture.isVirtual.set(current.isVirtual());
				capture.threadName.set(current.getName());
				capture.executed.set(true);
				return "test response";
			});
		}

	}

	@LocalServerPort
	private int port;

	@Autowired
	private ExecutionCapture capture;

	/**
	 * Sends a real A2A message/send request and verifies the AgentExecutor ran on a
	 * virtual thread from {@code a2aTaskExecutor}.
	 * {@code MessageController.sendMessage()} blocks until the executor thread completes,
	 * so the captured values are guaranteed to be set before the assertions.
	 */
	@Test
	void agentExecutorShouldRunOnVirtualThread() {
		RestTemplate restTemplate = new RestTemplate();
		String url = "http://localhost:" + port + "/";

		String body = """
				{
				  "jsonrpc": "2.0",
				  "id": "vt-test-1",
				  "method": "message/send",
				  "params": {
				    "message": {
				      "kind": "message",
				      "messageId": "msg-vt-1",
				      "role": "user",
				      "parts": [{"kind": "text", "text": "hello"}]
				    }
				  }
				}
				""";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);

		assertThat(capture.executed.get()).as("AgentExecutor should have been called").isTrue();
		assertThat(capture.isVirtual.get()).as("AgentExecutor should run on a virtual thread").isTrue();
		assertThat(capture.threadName.get()).as("AgentExecutor should use a2aTaskExecutor").startsWith("a2a-task-");
	}

}
