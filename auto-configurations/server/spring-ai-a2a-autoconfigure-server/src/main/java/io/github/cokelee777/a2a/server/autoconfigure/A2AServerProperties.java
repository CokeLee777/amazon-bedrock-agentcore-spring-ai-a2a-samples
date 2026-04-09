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
 *         url: ${A2A_SERVER_URL:}
 * </pre>
 *
 * @param enabled whether the A2A server is enabled; defaults to {@code true}
 * @param url base URL of this A2A server used for {@link io.a2a.spec.AgentCard}
 * construction; typically set via {@code A2A_SERVER_URL} environment variable in
 * production, or overridden per-profile in local development; the autoconfiguration
 * default is {@code http://localhost:{server.port}}
 */
@ConfigurationProperties(prefix = A2AServerProperties.CONFIG_PREFIX)
public record A2AServerProperties(boolean enabled, String url) {

	public static final String CONFIG_PREFIX = "spring.ai.a2a.server";

}
