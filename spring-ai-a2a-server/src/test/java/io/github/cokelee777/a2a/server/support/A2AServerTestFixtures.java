package io.github.cokelee777.a2a.server.support;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;

import java.util.List;
import java.util.Map;

/**
 * Shared A2A spec objects for {@code spring-ai-a2a-server} tests.
 */
public final class A2AServerTestFixtures {

	private A2AServerTestFixtures() {
	}

	/**
	 * Minimal {@link AgentCard} for JSON serialization in controller tests.
	 * @return a non-null agent card
	 */
	public static AgentCard minimalAgentCard() {
		return new AgentCard("fixture-agent", "Fixture", "http://localhost/", null, "1.0.0", null,
				new AgentCapabilities(false, false, false, List.of()), List.of("text"), List.of("text"), List.of(),
				false, null, null, null, List.of(new AgentInterface("JSONRPC", "http://localhost/")), "JSONRPC",
				"0.1.0", null);
	}

	/**
	 * User message suitable for {@link MessageSendParams}.
	 * @param messageId message id
	 * @param text user text
	 * @return a non-null message
	 */
	public static Message userMessage(String messageId, String text) {
		return new Message(Message.Role.USER, List.of(new TextPart(text)), messageId, null, null, List.of(), Map.of(),
				List.of());
	}

	/**
	 * Task in the given state.
	 * @param id task id
	 * @param contextId context id
	 * @param state lifecycle state
	 * @return a non-null task
	 */
	public static Task taskInState(String id, String contextId, TaskState state) {
		return new Task(id, contextId, new TaskStatus(state, null, null), null, null, null);
	}

}
