package io.github.cokelee777.agent.host.remote;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;

/**
 * Request to delegate work to one downstream agent. Used by
 * {@link RemoteAgentTools#delegateToRemoteAgent} and as list elements in
 * {@link RemoteAgentTools#delegateToRemoteAgentsParallel}.
 *
 * @param agentName must equal the target agent's {@link io.a2a.spec.AgentCard#name()}
 * from its agent card (not the YAML config key)
 * @param task task description and context to send to that agent
 */
public record RemoteAgentDelegationRequest(@ToolParam(description = "작업을 위임할 에이전트의 이름") String agentName,
		@ToolParam(description = "에이전트에게 전달할 포괄적인 작업 설명 및 컨텍스트") String task) {

	public RemoteAgentDelegationRequest {
		Assert.hasText(agentName, "agentName must not be empty");
		Assert.hasText(task, "task must not be empty");
	}
}
