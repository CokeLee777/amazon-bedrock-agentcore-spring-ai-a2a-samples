package io.github.cokelee777.a2a.server.autoconfigure;

import io.a2a.server.config.A2AConfigProvider;
import io.a2a.server.config.DefaultValuesConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * Spring Environment based A2A configuration provider. It first checks the Spring
 * Environment for the property. If not found, it falls back to
 * DefaultValuesConfigProvider.
 * <p>
 * This allows overriding default A2A server properties using standard Spring Environment
 * properties.
 *
 */
@Slf4j
@RequiredArgsConstructor
public class SpringA2AConfigProvider implements A2AConfigProvider {

	private final Environment env;

	private final DefaultValuesConfigProvider defaultValuesConfigProvider;

	@Override
	public String getValue(String name) {
		Assert.notNull(name, "name must not be null");
		if (env.containsProperty(name)) {
			String value = env.getRequiredProperty(name);
			log.info("Using custom A2A config: {}={}", name, value);
			return value;
		}
		// Fallback to defaults
		return defaultValuesConfigProvider.getValue(name);
	}

	@Override
	public Optional<String> getOptionalValue(String name) {
		Assert.notNull(name, "name must not be null");
		if (env.containsProperty(name)) {
			String value = env.getRequiredProperty(name);
			log.info("Using custom A2A optional config: {}={}", name, value);
			return Optional.of(value);
		}
		return defaultValuesConfigProvider.getOptionalValue(name);
	}

}
