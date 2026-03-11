package io.github.cokelee777.agentcore.orchestrator.tools;

import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import io.github.cokelee777.agentcore.common.metadata.A2aMetadataKeys;
import io.github.cokelee777.agentcore.common.transport.A2aTransport;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for Spring AI {@code @Tool} implementations that delegate to
 * downstream A2A agents.
 *
 * <p>
 * Subclasses supply the target agent URL via the constructor and expose one or more
 * {@code @Tool}-annotated methods that call {@link #sendRequest} with the appropriate
 * skill ID and message text.
 * </p>
 */
public abstract class A2aTool {

	private final A2aTransport transport;

	/**
	 * Timeout in seconds for downstream A2A calls, read from
	 * {@code a2a.client.timeout-seconds}.
	 */
	@Value("${a2a.client.timeout-seconds}")
	private int timeoutSeconds;

	/**
	 * Initialises the underlying {@link A2aTransport} for the given agent URL.
	 * @param agentUrl base URL of the downstream A2A agent
	 */
	protected A2aTool(String agentUrl) {
		this.transport = new A2aTransport(agentUrl);
	}

	/**
	 * Sends a {@code message/send} request to the downstream agent and returns the text
	 * response.
	 * @param skillId the skill ID to place in the message metadata so the agent can route
	 * the request
	 * @param text the natural-language text to include in the message body
	 * @return the agent's response text, or a Korean error message if the call fails
	 */
	protected String sendRequest(String skillId, String text) {
		Message message = new Message.Builder().role(Message.Role.USER)
			.parts(List.of(new TextPart(text)))
			.metadata(Map.of(A2aMetadataKeys.SKILL_ID, skillId))
			.build();
		return transport.send(message, timeoutSeconds).orElse("에이전트 호출 중 오류가 발생했습니다.");
	}

}
