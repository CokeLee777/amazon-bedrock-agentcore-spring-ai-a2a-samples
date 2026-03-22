package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Configuration for {@link BedrockChatMemoryRepository}.
 *
 * <p>
 * Use {@link #builder()} to construct an instance.
 * </p>
 */
public record BedrockChatMemoryRepositoryConfig(String memoryId, int maxTurns) {

	public BedrockChatMemoryRepositoryConfig {
		Assert.notNull(memoryId, "memoryId must not be null");
		Assert.isTrue(maxTurns > 0, "maxTurns must be greater than zero");
	}

	/**
	 * Returns a new {@link Builder}.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the Memory resource ID or ARN.
	 * @return the memory ID
	 */
	public String memoryId() {
		return this.memoryId;
	}

	/**
	 * Returns the maximum number of conversation turns to load.
	 * @return the max turns
	 */
	public int maxTurns() {
		return this.maxTurns;
	}

	/** Builder for {@link BedrockChatMemoryRepositoryConfig}. */
	public static final class Builder {

		private @Nullable String memoryId;

		private int maxTurns;

		private Builder() {
		}

		/**
		 * Sets the Memory resource ID or ARN.
		 * @param memoryId the memory ID; must not be null
		 * @return this builder
		 */
		public Builder memoryId(String memoryId) {
			Assert.notNull(memoryId, "memoryId must not be null");
			this.memoryId = memoryId;
			return this;
		}

		/**
		 * Sets the maximum number of conversation turns to load.
		 * @param maxTurns the max turns; must be greater than zero
		 * @return this builder
		 */
		public Builder maxTurns(int maxTurns) {
			Assert.isTrue(maxTurns > 0, "maxTurns must be greater than zero");
			this.maxTurns = maxTurns;
			return this;
		}

		/**
		 * Builds the {@link BedrockChatMemoryRepositoryConfig}.
		 * @return the config
		 */
		public BedrockChatMemoryRepositoryConfig build() {
			Assert.notNull(this.memoryId, "memoryId must not be null");
			Assert.isTrue(this.maxTurns > 0, "maxTurns must be greater than zero");
			return new BedrockChatMemoryRepositoryConfig(this.memoryId, this.maxTurns);
		}

	}

}
