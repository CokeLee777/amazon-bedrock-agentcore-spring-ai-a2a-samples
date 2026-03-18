package io.github.cokelee777.agent.host.server.autoconfigure;

import io.a2a.server.config.A2AConfigProvider;
import io.a2a.server.config.DefaultValuesConfigProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * Spring Environment based A2A configuration provider. It first checks the Spring
 * Environment for the property. If not found, it falls back to
 * DefaultValuesConfigProvider.
 *
 * This allows overriding default A2A server properties using standard Spring Environment
 * properties.
 *
 */
@RequiredArgsConstructor
public class SpringA2AConfigProvider implements A2AConfigProvider {

	private final Environment env;

	private final DefaultValuesConfigProvider defaultValuesConfigProvider;

	@Override
	public String getValue(String name) {
		Assert.hasText(name, "name must not be null");
		if (env.containsProperty(name)) {
			return env.getRequiredProperty(name);
		}
		// Fallback to defaults
		return defaultValuesConfigProvider.getValue(name);
	}

	@Override
	public Optional<String> getOptionalValue(String name) {
		Assert.hasText(name, "name must not be null");
		if (env.containsProperty(name)) {
			return Optional.of(env.getRequiredProperty(name));
		}
		return defaultValuesConfigProvider.getOptionalValue(name);
	}

}
