/**
 * Amazon Bedrock AgentCore implementation of Spring AI {@code ChatMemoryRepository}.
 *
 * <p>
 * Provides
 * {@link io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepository},
 * which persists conversation turns via the Bedrock AgentCore Memory API using
 * {@code listEvents} / {@code createEvent}. The {@code conversationId} is a composite key
 * of the form {@code "actorId:sessionId"}.
 * </p>
 */
@NullMarked
package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

import org.jspecify.annotations.NullMarked;
