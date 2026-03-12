package io.github.cokelee777.a2a.server.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Spring AI A2A Server.
 *
 * <p>
 * These properties allow customization of basic A2A server behavior.
 *
 * <p>
 * <strong>Example configuration:</strong> <pre>
 * spring:
 *   ai:
 *     a2a:
 *       server:
 *         enabled: true
 * </pre>
 *
 */
@Setter
@Getter
@ConfigurationProperties(prefix = A2AServerProperties.CONFIG_PREFIX)
public class A2AServerProperties {

	public static final String CONFIG_PREFIX = "spring.ai.a2a.server";

	/**
	 * Whether the A2A server is enabled.
	 */
	private boolean enabled = true;

}
