package io.github.cokelee777.a2a.agent.common.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for spring-ai-a2a-agent-common shared components.
 *
 * <p>
 * Registers {@link PingController}, {@link RemoteAgentProperties} binding for
 * {@code a2a.remote.*}, and {@link RemoteAgentCardRegistry} when this module is on the
 * classpath.
 * </p>
 */
@AutoConfiguration
@Import(PingController.class)
@EnableConfigurationProperties(RemoteAgentProperties.class)
public class AgentCommonAutoConfiguration {

	/**
	 * Registry of lazy cards for all agents listed under {@code a2a.remote.agents}
	 * (possibly empty when unset).
	 * @param properties bound {@code a2a.remote} configuration
	 * @return the shared registry
	 */
	@Bean
	public RemoteAgentCardRegistry remoteAgentCardRegistry(RemoteAgentProperties properties) {
		return new RemoteAgentCardRegistry(properties);
	}

}
