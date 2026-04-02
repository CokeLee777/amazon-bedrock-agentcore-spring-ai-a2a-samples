package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.autoconfigure;

import io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryConfig;
import io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionConfiguration;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

/**
 * Auto-configuration for {@link BedrockAgentCoreChatMemoryRepository}.
 *
 * <p>
 * Requires {@code spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id}.
 * AWS credentials and region come from {@link BedrockAwsConnectionConfiguration}
 * ({@code spring.ai.bedrock.aws.*}).
 * </p>
 *
 */
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ BedrockAgentCoreChatMemoryRepository.class, BedrockAgentCoreClient.class })
@ConditionalOnProperty(prefix = BedrockAgentCoreChatMemoryRepositoryProperties.CONFIG_PREFIX, name = "memory-id")
@EnableConfigurationProperties(BedrockAgentCoreChatMemoryRepositoryProperties.class)
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockAgentCoreChatMemoryRepositoryAutoConfiguration {

	/**
	 * Registers the Bedrock AgentCore-backed {@link ChatMemoryRepository}.
	 * @param credentialsProvider AWS credentials
	 * @param regionProvider AWS region
	 * @param props memory properties
	 * @return the repository
	 */
	@Bean
	@ConditionalOnMissingBean(ChatMemoryRepository.class)
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockAgentCoreChatMemoryRepository chatMemoryRepository(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockAgentCoreChatMemoryRepositoryProperties props) {
		Assert.hasText(props.getMemoryId(),
				"spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id must be set");

		BedrockAgentCoreClient client = BedrockAgentCoreClient.builder()
			.credentialsProvider(credentialsProvider)
			.region(regionProvider.getRegion())
			.build();

		BedrockAgentCoreChatMemoryConfig config = BedrockAgentCoreChatMemoryConfig.builder()
			.bedrockAgentCoreClient(client)
			.memoryId(props.getMemoryId())
			.actorId(props.getActorId())
			.build();

		return new BedrockAgentCoreChatMemoryRepository(config);
	}

}
