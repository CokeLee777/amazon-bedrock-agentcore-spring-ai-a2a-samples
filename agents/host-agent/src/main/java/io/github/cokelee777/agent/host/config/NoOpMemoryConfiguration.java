package io.github.cokelee777.agent.host.config;

import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.NoOpConversationMemoryService;
import io.github.cokelee777.agent.host.memory.NoOpLongTermMemoryService;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers no-operation memory beans when
 * {@code aws.bedrock.agent-core.memory.mode=none}.
 *
 * <p>
 * Allows the application to start without AWS credentials (e.g., local development). Also
 * registers {@link BedrockMemoryProperties} so that {@code DefaultInvocationService} can
 * read the mode even in NONE mode without requiring AWS infrastructure beans.
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "aws.bedrock.agent-core.memory.mode", havingValue = "none")
@EnableConfigurationProperties(BedrockMemoryProperties.class)
public class NoOpMemoryConfiguration {

	/**
	 * No-op conversation memory service bean.
	 * @return a {@link NoOpConversationMemoryService} instance
	 */
	@Bean
	public ConversationMemoryService conversationMemoryService() {
		return new NoOpConversationMemoryService();
	}

	/**
	 * No-op long-term memory service bean.
	 * @return a {@link NoOpLongTermMemoryService} instance
	 */
	@Bean
	public LongTermMemoryService longTermMemoryService() {
		return new NoOpLongTermMemoryService();
	}

}
