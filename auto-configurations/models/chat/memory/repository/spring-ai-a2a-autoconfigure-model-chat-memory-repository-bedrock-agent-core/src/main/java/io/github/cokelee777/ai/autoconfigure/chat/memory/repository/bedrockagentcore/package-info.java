/**
 * Spring Boot auto-configuration for the Bedrock AgentCore Chat Memory Repository.
 *
 * <p>
 * Registers
 * {@link io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepository}
 * when {@code spring.ai.chat.memory.repository.bedrock.agent-core.memory-id} is set. When
 * the property is absent, Spring AI's {@code InMemoryChatMemoryRepository} is used as a
 * fallback via {@code ChatMemoryAutoConfiguration}.
 * </p>
 */
@NullMarked
package io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore;

import org.jspecify.annotations.NullMarked;
