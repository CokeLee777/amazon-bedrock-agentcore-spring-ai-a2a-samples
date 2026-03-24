package io.github.cokelee777.agent.host.remote;

import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry;
import io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteAgentToolsTest {

	private RemoteAgentTools remoteAgentTools;

	@BeforeEach
	void setUp() {
		remoteAgentTools = new RemoteAgentTools(new RemoteAgentCardRegistry(
				new RemoteAgentProperties(Map.of("dummy", new RemoteAgentProperties.Agent("http://127.0.0.1:9")))));
	}

	@Test
	void sendMessagesParallel_null_rejectsWithIllegalArgument() {
		assertThatThrownBy(() -> remoteAgentTools.sendMessagesParallel(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("requests");
	}

	@Test
	void sendMessagesParallel_empty_returnsEmptyAggregatedOutput() {
		assertThat(remoteAgentTools.sendMessagesParallel(List.of())).isEmpty();
	}

}
