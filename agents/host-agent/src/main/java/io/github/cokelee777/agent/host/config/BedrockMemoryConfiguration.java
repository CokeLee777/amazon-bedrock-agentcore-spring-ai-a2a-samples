package io.github.cokelee777.agent.host.config;

import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.NoOpLongTermMemoryService;
import io.github.cokelee777.agent.host.memory.bedrock.AgentCoreEventToMessageConverter;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockConversationMemoryService;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockLongTermMemoryService;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

/**
 * Registers Amazon Bedrock AgentCore Memory beans when
 * {@code aws.bedrock.agent-core.memory.mode} is not {@code none}.
 *
 * <p>
 * When mode is {@code short_term}, a {@link NoOpLongTermMemoryService} is registered.
 * When mode is {@code long_term} or {@code both}, a {@link BedrockLongTermMemoryService}
 * is registered.
 * </p>
 */
@Configuration
@ConditionalOnExpression("!'none'.equalsIgnoreCase('${aws.bedrock.agent-core.memory.mode}')")
@EnableConfigurationProperties(BedrockMemoryProperties.class)
public class BedrockMemoryConfiguration {

	/**
	 * Creates the {@link BedrockAgentCoreClient} using the configured AWS region.
	 * @param region the AWS region value
	 * @return the Bedrock AgentCore data-plane client
	 */
	@Bean
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
	 * Creates the Bedrock short-term conversation memory service.
	 * @param client the Bedrock client
	 * @param properties the memory properties
	 * @param converter the event-to-message converter
	 * @return the service
	 */
	@Bean
	public BedrockConversationMemoryService conversationMemoryService(BedrockAgentCoreClient client,
			BedrockMemoryProperties properties, AgentCoreEventToMessageConverter converter) {
		return new BedrockConversationMemoryService(client, properties, converter);
	}

	/**
	 * Creates a no-op long-term memory service when mode is {@code short_term}.
	 * @return a no-op implementation
	 */
	@Bean
	@ConditionalOnProperty(name = "aws.bedrock.agent-core.memory.mode", havingValue = "short_term")
	public LongTermMemoryService noOpLongTermMemoryService() {
		return new NoOpLongTermMemoryService();
	}

	/**
	 * Creates the Bedrock long-term memory service when mode is {@code long_term} or
	 * {@code both}.
	 * @param client the Bedrock client
	 * @param properties the memory properties
	 * @return the service
	 */
	@Bean
	@ConditionalOnExpression("'${aws.bedrock.agent-core.memory.mode}'.equalsIgnoreCase('long_term') or '${aws.bedrock.agent-core.memory.mode}'.equalsIgnoreCase('both')")
	public BedrockLongTermMemoryService bedrockLongTermMemoryService(BedrockAgentCoreClient client,
			BedrockMemoryProperties properties) {
		return new BedrockLongTermMemoryService(client, properties);
	}

}
