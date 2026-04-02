package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.autoconfigure;

import io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BedrockAgentCoreChatMemoryRepositoryAutoConfiguration}.
 *
 */
class BedrockAgentCoreChatMemoryRepositoryAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(BedrockAgentCoreChatMemoryRepositoryAutoConfiguration.class))
		.withUserConfiguration(MockAwsConfig.class)
		.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id=test-store");

	@Test
	void autoConfiguresBedrockRepository() {
		this.contextRunner.run(ctx -> {
			assertThat(ctx).hasSingleBean(BedrockAgentCoreChatMemoryRepository.class);
			assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
		});
	}

	@Test
	void backsOffWhenChatMemoryRepositoryAlreadyDefined() {
		this.contextRunner.withUserConfiguration(UserDefinedRepositoryConfig.class).run(ctx -> {
			assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
			assertThat(ctx).doesNotHaveBean(BedrockAgentCoreChatMemoryRepository.class);
		});
	}

	@Test
	void defaultActorIdIsSpringAi() {
		this.contextRunner.run(ctx -> {
			BedrockAgentCoreChatMemoryRepositoryProperties props = ctx
				.getBean(BedrockAgentCoreChatMemoryRepositoryProperties.class);
			assertThat(props.getActorId()).isEqualTo("spring-ai");
		});
	}

	@Test
	void customActorIdIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory.actor-id=my-agent")
			.run(ctx -> {
				BedrockAgentCoreChatMemoryRepositoryProperties props = ctx
					.getBean(BedrockAgentCoreChatMemoryRepositoryProperties.class);
				assertThat(props.getActorId()).isEqualTo("my-agent");
			});
	}

	@Configuration
	static class MockAwsConfig {

		@Bean
		AwsCredentialsProvider credentialsProvider() {
			return Mockito.mock(AwsCredentialsProvider.class);
		}

		@Bean
		AwsRegionProvider regionProvider() {
			AwsRegionProvider mock = Mockito.mock(AwsRegionProvider.class);
			Mockito.when(mock.getRegion()).thenReturn(Region.US_EAST_1);
			return mock;
		}

	}

	@Configuration
	static class UserDefinedRepositoryConfig {

		@Bean
		ChatMemoryRepository customRepository() {
			return Mockito.mock(ChatMemoryRepository.class);
		}

	}

}
