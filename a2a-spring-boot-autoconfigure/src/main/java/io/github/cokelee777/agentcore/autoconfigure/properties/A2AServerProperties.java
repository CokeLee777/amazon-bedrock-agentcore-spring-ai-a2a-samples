package io.github.cokelee777.agentcore.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the A2A server infrastructure.
 *
 * <p>
 * Bind values via {@code a2a.server.*} in {@code application.yml}.
 * </p>
 *
 * consumption
 *
 * @param executorCorePoolSize core thread pool size for the agent executor
 * @param executorMaxPoolSize maximum thread pool size for the agent executor
 * @param executorQueueCapacity task queue capacity for the agent executor
 */
@ConfigurationProperties(prefix = "a2a.server")
public record A2AServerProperties(int executorCorePoolSize, int executorMaxPoolSize, int executorQueueCapacity) {

	/** Default constructor with sensible production defaults. */
	public A2AServerProperties() {
		this(10, 50, 100);
	}

}
