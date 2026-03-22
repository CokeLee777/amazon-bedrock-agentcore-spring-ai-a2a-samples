package io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Amazon Bedrock AgentCore Chat Memory Repository.
 *
 * <p>
 * Bound from the {@code spring.ai.chat.memory.repository.bedrock.agent-core} prefix.
 * Override with environment variables (e.g., {@code BEDROCK_MEMORY_ID}).
 * </p>
 *
 * @param memoryId the Memory resource ID or ARN; required when this autoconfigure is
 * active
 * @param maxTurns max conversation turns to load from short-term memory; defaults to 10
 */
@ConfigurationProperties(prefix = BedrockChatMemoryRepositoryProperties.CONFIG_PREFIX)
public record BedrockChatMemoryRepositoryProperties(@Nullable String memoryId, @DefaultValue("10") int maxTurns) {

	/** Configuration properties prefix. */
	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.bedrock.agent-core";

}
