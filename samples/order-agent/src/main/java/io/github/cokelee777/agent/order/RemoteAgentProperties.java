package io.github.cokelee777.agent.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * Configuration properties for remote downstream A2A agents called by the Order Agent.
 *
 * <p>
 * Bound from the {@code remote.agents} YAML map. Each key is the logical agent name
 * (e.g., {@code delivery-agent}) and the value holds the connection details.
 * </p>
 *
 * @param agents map of logical agent name to connection details
 */
@ConfigurationProperties(prefix = "remote")
public record RemoteAgentProperties(Map<String, Agent> agents) {

	public RemoteAgentProperties {
		Assert.notNull(agents, "agents must not be null");
	}

	/**
	 * Connection details for a single downstream agent.
	 *
	 * @param url the base URL of the agent (e.g., {@code http://localhost:9002})
	 */
	public record Agent(String url) {

		public Agent {
			Assert.notNull(url, "url must not be null");
		}
	}
}
