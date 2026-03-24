package io.github.cokelee777.agent.host;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the A2A Orchestrator service.
 *
 * <p>
 * Runs on port 8080. Receives requests from Amazon Bedrock AgentCore Runtime via
 * {@code POST /invocations} (see
 * {@link io.github.cokelee777.agent.host.invocation.InvocationController}) and
 * coordinates downstream order, delivery, and payment agents via LLM tool-calling (see
 * {@link io.github.cokelee777.agent.host.remote.RemoteAgentTools}).
 * </p>
 *
 * <p>
 * The system prompt is applied per request in
 * {@link io.github.cokelee777.agent.host.invocation.InvocationController#invoke} so that
 * agent descriptions always reflect the latest loaded {@link io.a2a.spec.AgentCard
 * AgentCards} (via
 * {@link io.github.cokelee777.a2a.agent.common.autoconfigure.RemoteAgentCardRegistry}).
 * </p>
 */
@SpringBootApplication
public class HostAgentApplication {

	/**
	 * Starts the Orchestrator.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(HostAgentApplication.class, args);
	}

}
