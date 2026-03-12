package io.github.cokelee777.agentcore.common.executor;

import io.a2a.spec.Message;

/**
 * Interface for handling skill execution requests in an A2A agent server.
 *
 * <p>
 * Each implementation declares the skill ID it handles and the expected caller role,
 * allowing the agent executor to route requests by skill ID without inspecting message
 * content.
 * </p>
 */
public interface SkillExecutor {

	/**
	 * Returns the skill ID this executor handles.
	 *
	 * <p>
	 * Must match the {@code id} declared in the agent's {@code AgentCard} skills list.
	 * </p>
	 * @return the skill ID string (e.g., {@code "order_list"})
	 */
	String skillId();

	/**
	 * Returns the A2A caller role required to invoke this skill.
	 * @return {@link Message.Role#AGENT} for internal skills, or
	 * {@link Message.Role#USER} for external skills
	 */
	Message.Role requiredRole();

	/**
	 * Executes the skill logic for the given message text.
	 * @param message the message text extracted from the A2A request
	 * @return the result of the skill execution
	 */
	String execute(String message);

}
