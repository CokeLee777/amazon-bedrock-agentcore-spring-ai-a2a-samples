package io.github.cokelee777.a2a.server.autoconfigure;

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
 * @param enabled whether the A2A server is enabled; defaults to {@code true}
 */
@ConfigurationProperties(prefix = A2AServerProperties.CONFIG_PREFIX)
public record A2AServerProperties(boolean enabled) {

	public static final String CONFIG_PREFIX = "spring.ai.a2a.server";

}
