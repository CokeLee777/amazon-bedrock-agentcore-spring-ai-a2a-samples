package io.github.cokelee777.agent.payment;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AgentCore Runtime health checks.
 *
 * <p>
 * The AgentCore Runtime polls {@code GET /ping} to verify that the agent is operational
 * before routing traffic to it.
 * </p>
 */
@RestController
public class PingController {

	/**
	 * Returns a healthy status to confirm the agent is reachable.
	 * @return {@code 200 OK} with {@code {"status":"healthy"}}
	 */
	@GetMapping(path = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PingResponse> ping() {
		return ResponseEntity.ok(new PingResponse("Healthy"));
	}

	/**
	 * Response body for the {@code GET /ping} health check endpoint.
	 *
	 * @param status health status string (e.g., {@code "Healthy"})
	 */
	public record PingResponse(String status) {
	}

}
