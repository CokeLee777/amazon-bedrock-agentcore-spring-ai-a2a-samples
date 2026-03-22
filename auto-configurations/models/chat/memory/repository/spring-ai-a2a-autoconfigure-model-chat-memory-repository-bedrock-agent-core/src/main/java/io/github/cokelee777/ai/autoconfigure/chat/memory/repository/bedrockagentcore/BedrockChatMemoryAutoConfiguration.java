package io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore;

import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.AgentCoreEventToMessageConverter;
import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepository;
import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepositoryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;

/**
 * Auto-configuration for Amazon Bedrock AgentCore {@link BedrockChatMemoryRepository}.
 *
 * <p>
 * Activates when:
 * <ol>
 * <li>{@link BedrockChatMemoryRepository} is on the classpath.</li>
 * <li>{@code spring.ai.chat.memory.repository.bedrock.agent-core.memory-id} is set.</li>
 * </ol>
 * When not active, Spring AI's {@link ChatMemoryAutoConfiguration} registers
 * {@code InMemoryChatMemoryRepository} as the fallback via
 * {@code @ConditionalOnMissingBean}.
 * </p>
 */
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass(BedrockChatMemoryRepository.class)
@ConditionalOnProperty(prefix = BedrockChatMemoryRepositoryProperties.CONFIG_PREFIX, name = "memory-id")
@EnableConfigurationProperties(BedrockChatMemoryRepositoryProperties.class)
public class BedrockChatMemoryAutoConfiguration {

	/**
	 * Creates the Bedrock AgentCore data-plane client using the configured AWS region.
	 * @param region the AWS region value from {@code spring.ai.bedrock.aws.region}
	 * @return the client
	 */
	@Bean
	@ConditionalOnMissingBean
	public BedrockAgentCoreClient bedrockAgentCoreClient(@Value("${spring.ai.bedrock.aws.region}") String region) {
		return BedrockAgentCoreClient.builder().region(Region.of(region)).build();
	}

	/**
	 * Creates the event-to-message converter.
	 * @return the converter
	 */
	@Bean
	public AgentCoreEventToMessageConverter agentCoreEventToMessageConverter() {
		return new AgentCoreEventToMessageConverter();
	}

	/**
	 * Creates the {@link BedrockChatMemoryRepository}.
	 * @param client the Bedrock client
	 * @param properties the memory repository properties
	 * @param converter the event-to-message converter
	 * @return the repository
	 */
	/**
	 * Creates the {@link BedrockChatMemoryRepositoryConfig} from properties.
	 * @param properties the memory repository properties
	 * @return the config
	 */
	@Bean
	public BedrockChatMemoryRepositoryConfig bedrockChatMemoryRepositoryConfig(
			BedrockChatMemoryRepositoryProperties properties) {
		return BedrockChatMemoryRepositoryConfig.builder()
			.memoryId(properties.memoryId())
			.maxTurns(properties.maxTurns())
			.build();
	}

	/**
	 * Creates the {@link BedrockChatMemoryRepository}.
	 * @param client the Bedrock client
	 * @param config the repository configuration
	 * @param converter the event-to-message converter
	 * @return the repository
	 */
	@Bean
	public BedrockChatMemoryRepository chatMemoryRepository(BedrockAgentCoreClient client,
			BedrockChatMemoryRepositoryConfig config, AgentCoreEventToMessageConverter converter) {
		return new BedrockChatMemoryRepository(client, config, converter);
	}

}
