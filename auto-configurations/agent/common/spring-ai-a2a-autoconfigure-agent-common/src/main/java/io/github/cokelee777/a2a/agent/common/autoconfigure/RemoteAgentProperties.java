package io.github.cokelee777.a2a.agent.common.autoconfigure;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Configuration properties for remote downstream A2A agents.
 *
 * <p>
 * Bound from the {@code a2a.remote.agents} YAML map. Each key is the logical agent name
 * (e.g. {@code order-agent}) and the value holds the connection details.
 * </p>
 *
 * <p>
 * Enabled via {@link AgentCommonAutoConfiguration}.
 * </p>
 *
 * @param agents map of logical agent name to connection details; defaults to an empty map
 * when {@code a2a.remote.agents} is absent
 */
@Validated
@ConfigurationProperties(prefix = RemoteAgentProperties.CONFIG_PREFIX)
public record RemoteAgentProperties(@DefaultValue @NotNull Map<String, Agent> agents) {

	/** Configuration properties prefix. */
	public static final String CONFIG_PREFIX = "a2a.remote";

	/**
	 * Connection details for a single downstream agent.
	 *
	 * @param url the base URL of the agent (e.g. {@code http://localhost:9001})
	 */
	public record Agent(@NotNull String url) {
	}
}
