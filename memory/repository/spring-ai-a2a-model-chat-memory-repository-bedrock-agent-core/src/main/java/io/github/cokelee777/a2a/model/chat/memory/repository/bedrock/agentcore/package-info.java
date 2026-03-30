/**
 * Amazon Bedrock AgentCore implementation of Spring AI {@code ChatMemoryRepository}.
 *
 * <p>
 * Provides
 * {@link io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryRepository},
 * which maps {@code conversationId} to Bedrock {@code sessionId} and uses the configured
 * {@code actorId} for Memory API calls (events and long-term memory records).
 * </p>
 */
@NullMarked
package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import org.jspecify.annotations.NullMarked;
