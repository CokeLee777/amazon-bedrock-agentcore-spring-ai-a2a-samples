package io.github.cokelee777.a2a.server.autoconfigure;

import io.a2a.server.config.A2AConfigProvider;
import io.a2a.server.config.DefaultValuesConfigProvider;
import org.springframework.core.env.Environment;

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
public class SpringA2AConfigProvider implements A2AConfigProvider {

	private final Environment env;

	private final DefaultValuesConfigProvider defaultValues;

	SpringA2AConfigProvider(Environment env, DefaultValuesConfigProvider defaultValues) {
		this.env = env;
		this.defaultValues = defaultValues;
	}

	@Override
	public String getValue(String name) {
		if (this.env.containsProperty(name)) {
			return this.env.getProperty(name);
		}
		// Fallback to defaults
		return this.defaultValues.getValue(name);
	}

	@Override
	public Optional<String> getOptionalValue(String name) {
		if (this.env.containsProperty(name)) {
			return Optional.of(this.env.getProperty(name));
		}
		return this.defaultValues.getOptionalValue(name);
	}

}
