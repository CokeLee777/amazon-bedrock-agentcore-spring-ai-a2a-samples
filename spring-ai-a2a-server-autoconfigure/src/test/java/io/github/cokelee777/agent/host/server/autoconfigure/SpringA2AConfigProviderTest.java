package io.github.cokelee777.agent.host.server.autoconfigure;

import io.a2a.server.config.DefaultValuesConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SpringA2AConfigProvider}.
 */
@ExtendWith(MockitoExtension.class)
class SpringA2AConfigProviderTest {

	private MockEnvironment environment;

	@Mock
	private DefaultValuesConfigProvider defaultValues;

	private SpringA2AConfigProvider provider;

	@BeforeEach
	void setUp() {
		environment = new MockEnvironment();
		provider = new SpringA2AConfigProvider(environment, defaultValues);
	}

	@Test
	void getValueReturnsFromEnvironmentWhenPresent() {
		environment.setProperty("a2a.blocking.agent.timeout.seconds", "60");

		assertThat(provider.getValue("a2a.blocking.agent.timeout.seconds")).isEqualTo("60");
	}

	@Test
	void getValueFallsBackToDefaultWhenNotInEnvironment() {
		when(defaultValues.getValue("a2a.blocking.agent.timeout.seconds")).thenReturn("30");

		assertThat(provider.getValue("a2a.blocking.agent.timeout.seconds")).isEqualTo("30");
	}

	@Test
	void getOptionalValueReturnsFromEnvironmentWhenPresent() {
		environment.setProperty("custom.property", "custom-value");

		assertThat(provider.getOptionalValue("custom.property")).hasValue("custom-value");
	}

	@Test
	void getOptionalValueFallsBackToDefaultWhenNotInEnvironment() {
		when(defaultValues.getOptionalValue("a2a.executor.core-pool-size")).thenReturn(Optional.of("5"));

		assertThat(provider.getOptionalValue("a2a.executor.core-pool-size")).hasValue("5");
	}

	@Test
	void getOptionalValueReturnsEmptyForUnknownProperty() {
		when(defaultValues.getOptionalValue("unknown.property")).thenReturn(Optional.empty());

		assertThat(provider.getOptionalValue("unknown.property")).isEmpty();
	}

	@Test
	void environmentTakesPrecedenceOverDefaults() {
		environment.setProperty("a2a.executor.max-pool-size", "100");

		assertThat(provider.getValue("a2a.executor.max-pool-size")).isEqualTo("100");
	}

}
