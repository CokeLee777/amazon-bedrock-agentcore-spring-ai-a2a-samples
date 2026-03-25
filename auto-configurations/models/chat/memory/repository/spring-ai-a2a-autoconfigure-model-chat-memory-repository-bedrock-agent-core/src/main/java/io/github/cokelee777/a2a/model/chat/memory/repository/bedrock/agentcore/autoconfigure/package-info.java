/**
 * Spring Boot autoconfiguration for the Bedrock AgentCore Chat Memory Repository.
 *
 * <p>
 * Registers
 * {@link io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.BedrockChatMemoryRepository}
 * when {@code spring.ai.chat.memory.repository.bedrock.agent-core.memory-id} is set. When
 * the property is absent, Spring AI's {@code InMemoryChatMemoryRepository} is used as a
 * fallback via {@code ChatMemoryAutoConfiguration}.
 * </p>
 */
@NullMarked
package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.autoconfigure;

import org.jspecify.annotations.NullMarked;
