package io.github.cokelee777.agentcore.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the A2A server thread pool used by
 * {@link io.a2a.server.requesthandlers.DefaultRequestHandler} when executing
 * {@link io.a2a.server.agentexecution.AgentExecutor} work.
 *
 * <p>
 * Binds from {@code a2a.server.*} in {@code application.yml}, environment variables, or
 * other {@link org.springframework.boot.context.properties.ConfigurationProperties}
 * sources. Used by
 * {@link io.github.cokelee777.agentcore.autoconfigure.A2AServerAutoConfiguration} to size
 * the {@link java.util.concurrent.ExecutorService} bean unless a custom executor is
 * provided.
 * </p>
 *
 * @param executorCorePoolSize core pool size of the thread pool that runs agent tasks
 * @param executorMaxPoolSize maximum pool size of the thread pool
 * @param executorQueueCapacity capacity of the
 * {@link java.util.concurrent.LinkedBlockingQueue} holding pending agent tasks
 */
@ConfigurationProperties(prefix = "a2a.server")
public record A2AServerProperties(int executorCorePoolSize, int executorMaxPoolSize, int executorQueueCapacity) {

	/**
	 * No-arg constructor supplying default pool sizes when properties are omitted.
	 * Defaults: core 10, max 50, queue capacity 100.
	 */
	public A2AServerProperties() {
		this(10, 50, 100);
	}

}
