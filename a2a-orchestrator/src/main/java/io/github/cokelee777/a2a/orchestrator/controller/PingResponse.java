package io.github.cokelee777.a2a.server.autoconfigure;

import org.springframework.util.Assert;

/**
 * Response body for the {@code GET /ping} health check endpoint.
 *
 * @param status the current health status of the agent (e.g., {@code "healthy"})
 */
public record PingResponse(String status) {

	/**
	 * Creates a {@code PingResponse} and validates that {@code status} is not
	 * {@code null}.
	 */
	public PingResponse {
		Assert.notNull(status, "status must not be null");
	}
}
