package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryConfig;

/**
 * Configuration properties for
 * {@link BedrockAgentCoreChatMemoryRepositoryAutoConfiguration}.
 *
 * @author Chaemin Lee
 */
@ConfigurationProperties(BedrockAgentCoreChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class BedrockAgentCoreChatMemoryRepositoryProperties {

	/** Property prefix (nested {@code memory.*} under bedrock agent-core). */
	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.bedrock.agent-core.memory";

	/** Bedrock AgentCore Memory Store id (must exist in AWS). */
	private @Nullable String memoryId;

	/** Actor id for this application in AgentCore Memory. */
	private String actorId = BedrockAgentCoreChatMemoryConfig.DEFAULT_ACTOR_ID;

	/** Max results for list / retrieve operations (1–100). */
	private int maxResults = BedrockAgentCoreChatMemoryConfig.DEFAULT_MAX_RESULTS;

	public @Nullable String getMemoryId() {
		return this.memoryId;
	}

	public void setMemoryId(@Nullable String memoryId) {
		this.memoryId = memoryId;
	}

	public String getActorId() {
		return this.actorId;
	}

	public void setActorId(String actorId) {
		this.actorId = actorId;
	}

	public int getMaxResults() {
		return this.maxResults;
	}

	public void setMaxResults(int maxResults) {
		this.maxResults = maxResults;
	}

}
