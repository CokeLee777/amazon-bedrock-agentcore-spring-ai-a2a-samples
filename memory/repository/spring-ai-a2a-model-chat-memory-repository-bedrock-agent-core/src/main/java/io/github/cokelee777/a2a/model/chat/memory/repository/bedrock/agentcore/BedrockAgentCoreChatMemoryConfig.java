package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

/**
 * Configuration for {@link BedrockAgentCoreChatMemoryRepository}.
 *
 * @see BedrockAgentCoreChatMemoryRepository
 */
public final class BedrockAgentCoreChatMemoryConfig {

	/** Default actor ID when none is configured explicitly. */
	public static final String DEFAULT_ACTOR_ID = "spring-ai";

	private final BedrockAgentCoreClient bedrockAgentCoreClient;

	private final String memoryId;

	private final String actorId;

	private BedrockAgentCoreChatMemoryConfig(Builder builder) {
		Assert.notNull(builder.bedrockAgentCoreClient, "bedrockAgentCoreClient must not be null");
		Assert.hasText(builder.memoryId, "memoryId must not be empty");
		Assert.hasText(builder.actorId, "actorId must not be empty");

		this.bedrockAgentCoreClient = builder.bedrockAgentCoreClient;
		this.memoryId = builder.memoryId;
		this.actorId = builder.actorId;
	}

	/**
	 * Creates a new builder.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	public BedrockAgentCoreClient getBedrockAgentCoreClient() {
		return this.bedrockAgentCoreClient;
	}

	public String getMemoryId() {
		return this.memoryId;
	}

	public String getActorId() {
		return this.actorId;
	}

	/** Builder for {@link BedrockAgentCoreChatMemoryConfig}. */
	public static final class Builder {

		private @Nullable BedrockAgentCoreClient bedrockAgentCoreClient;

		private @Nullable String memoryId;

		private String actorId = DEFAULT_ACTOR_ID;

		private Builder() {
		}

		public Builder bedrockAgentCoreClient(BedrockAgentCoreClient bedrockAgentCoreClient) {
			this.bedrockAgentCoreClient = bedrockAgentCoreClient;
			return this;
		}

		public Builder memoryId(String memoryId) {
			this.memoryId = memoryId;
			return this;
		}

		public Builder actorId(String actorId) {
			this.actorId = actorId;
			return this;
		}

		public BedrockAgentCoreChatMemoryConfig build() {
			return new BedrockAgentCoreChatMemoryConfig(this);
		}

	}

}
