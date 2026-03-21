package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.MemoryMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Amazon Bedrock AgentCore Memory.
 *
 * <p>
 * Bound from the {@code aws.bedrock.agent-core.memory} YAML prefix. Override with
 * environment variables (e.g., {@code BEDROCK_MEMORY_ID}).
 * </p>
 *
 * @param mode the memory usage mode; required, defaults to {@link MemoryMode#NONE}
 * @param memoryId the Memory resource ID or ARN; required when mode is not {@code none}
 * @param strategyId the Memory strategy ID for long-term retrieval; required when mode
 * includes long-term
 * @param shortTermMaxTurns max conversation turns to load from short-term; defaults to 10
 * @param longTermMaxResults max records to return from long-term search; defaults to 4
 */
@Validated
@ConfigurationProperties(prefix = BedrockMemoryProperties.CONFIG_PREFIX)
public record BedrockMemoryProperties(@NotNull MemoryMode mode, @Nullable String memoryId, @Nullable String strategyId,
		@Min(1) int shortTermMaxTurns, @Min(1) int longTermMaxResults) {

	public static final String CONFIG_PREFIX = "aws.bedrock.agent-core.memory";

}
