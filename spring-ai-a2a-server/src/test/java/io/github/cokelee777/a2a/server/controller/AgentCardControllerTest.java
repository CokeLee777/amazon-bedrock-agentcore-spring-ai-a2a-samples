package io.github.cokelee777.a2a.server.controller;

import io.a2a.spec.AgentCard;
import io.github.cokelee777.a2a.server.support.A2AServerTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice tests for {@link AgentCardController}.
 */
@WebMvcTest(controllers = AgentCardController.class)
@Import(AgentCardControllerTest.AgentCardFixtureConfig.class)
class AgentCardControllerTest {

	/**
	 * Supplies a concrete {@link AgentCard} bean for JSON serialization.
	 */
	static class AgentCardFixtureConfig {

		@Bean
		AgentCard agentCard() {
			return A2AServerTestFixtures.minimalAgentCard();
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getAgentCard_returnsJsonWithAgentName() throws Exception {
		this.mockMvc.perform(get("/.well-known/agent-card.json").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("fixture-agent"))
			.andExpect(jsonPath("$.version").value("1.0.0"));
	}

}
