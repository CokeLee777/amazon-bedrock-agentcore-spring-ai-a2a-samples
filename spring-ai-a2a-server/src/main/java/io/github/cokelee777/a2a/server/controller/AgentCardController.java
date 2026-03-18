package io.github.cokelee777.a2a.server.controller;

import io.a2a.spec.AgentCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for A2A agent card metadata.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AgentCardController {

	private final AgentCard agentCard;

	/**
	 * Returns agent card metadata.
	 */
	@GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public AgentCard getAgentCard() {
		log.debug("Serving agent card: {}", agentCard.name());
		return agentCard;
	}

}
