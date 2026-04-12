package io.github.cokelee777.a2a.agent.common;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.Message;
import io.a2a.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link A2ATransport#send}.
 *
 * <p>
 * The success path (task completion with text artifacts) requires a live A2A server and
 * is covered by integration tests. These unit tests focus on error-handling behavior.
 * </p>
 * <p>
 * Note: {@code sendStream} connection-refused tests are omitted because the A2A SDK's
 * {@code JdkA2AHttpClient} does not propagate TCP-level failures (e.g. connection
 * refused) to the SSE error handler — the failure is silently dropped inside
 * {@code asyncRequest}, so the future only resolves after the 60-second timeout. This is
 * an SDK limitation; the happy-path streaming behavior is covered by integration tests.
 * </p>
 */
class A2ATransportTest {

	private static final String AGENT_NAME = "Test Agent";

	/**
	 * When the downstream agent URL is unreachable (connection refused), {@code send}
	 * should return an error message string instead of throwing.
	 */
	@Test
	void send_whenConnectionRefused_returnsErrorMessage() {
		AgentCard agentCard = unreachableAgentCard();
		Message message = A2A.toUserMessage("hello");

		String result = A2ATransport.send(agentCard, message);

		assertThat(result).startsWith("Error communicating with agent '" + AGENT_NAME + "'");
	}

	/**
	 * Verifies that the error message embeds the original exception message so callers
	 * can diagnose failures.
	 */
	@Test
	void send_whenConnectionRefused_errorMessageContainsCause() {
		AgentCard agentCard = unreachableAgentCard();
		Message message = A2A.toUserMessage("hello");

		String result = A2ATransport.send(agentCard, message);

		// Error format: "Error communicating with agent '<name>': <cause>"
		assertThat(result).contains(": ");
		assertThat(result).isNotEqualTo("Error communicating with agent '" + AGENT_NAME + "': ");
	}

	private static AgentCard unreachableAgentCard() {
		// Port 1 on localhost is a privileged port that is never bound in practice,
		// so the connection is refused immediately without waiting for a timeout.
		String url = "http://localhost:1";
		return new AgentCard.Builder().name(AGENT_NAME)
			.description("A test agent that is intentionally unreachable")
			.url(url)
			.additionalInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), url)))
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(false).pushNotifications(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of())
			.build();
	}

}
