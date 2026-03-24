package io.github.cokelee777.a2a.agent.common.autoconfigure;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteAgentCardRegistryTest {

	private static RemoteAgentProperties props(String key, String url) {
		return new RemoteAgentProperties(Map.of(key, new RemoteAgentProperties.Agent(url)));
	}

	@Test
	void getAgentDescriptions_emptyRegistry_returnsEmptyString() {
		assertThat(new RemoteAgentCardRegistry(new RemoteAgentProperties(Map.of())).getAgentDescriptions()).isEmpty();
	}

	@Test
	void findLazyCardByAgentName_unknownName_throwsIllegalArgument() {
		RemoteAgentCardRegistry registry = new RemoteAgentCardRegistry(props("a", "http://127.0.0.1:9"));

		assertThatThrownBy(() -> registry.findLazyCardByAgentName("missing"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("missing");
	}

	@Test
	void findLazyCardByAgentName_configuredName_returnsLazyCard() {
		RemoteAgentCardRegistry registry = new RemoteAgentCardRegistry(props("a", "http://127.0.0.1:9"));

		assertThat(registry.findLazyCardByAgentName("a")).isNotNull();
	}

}
