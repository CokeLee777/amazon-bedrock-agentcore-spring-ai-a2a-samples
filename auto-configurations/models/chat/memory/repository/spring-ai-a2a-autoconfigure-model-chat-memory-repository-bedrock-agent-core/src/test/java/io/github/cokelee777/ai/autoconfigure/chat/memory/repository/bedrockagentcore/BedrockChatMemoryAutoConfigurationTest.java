package io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore;

import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;

import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BedrockChatMemoryAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(BedrockChatMemoryAutoConfiguration.class, ChatMemoryAutoConfiguration.class));

	@Test
	void withMemoryId_registersBedrockRepository() {
		contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory-id=mem-123",
					"spring.ai.bedrock.aws.region=us-east-1")
			.withBean(BedrockAgentCoreClient.class, () -> mock(BedrockAgentCoreClient.class))
			.run(ctx -> assertThat(ctx).hasSingleBean(BedrockChatMemoryRepository.class));
	}

	@Test
	void withoutMemoryId_registersInMemoryRepository() {
		contextRunner.run(ctx -> {
			assertThat(ctx).doesNotHaveBean(BedrockChatMemoryRepository.class);
			assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
			assertThat(ctx.getBean(ChatMemoryRepository.class)).isInstanceOf(InMemoryChatMemoryRepository.class);
		});
	}

	@Test
	void withMemoryId_bedrockRepositoryTakesPrecedenceOverInMemory() {
		contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory-id=mem-123",
					"spring.ai.bedrock.aws.region=us-east-1")
			.withBean(BedrockAgentCoreClient.class, () -> mock(BedrockAgentCoreClient.class))
			.run(ctx -> {
				assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
				assertThat(ctx.getBean(ChatMemoryRepository.class)).isInstanceOf(BedrockChatMemoryRepository.class);
			});
	}

}
