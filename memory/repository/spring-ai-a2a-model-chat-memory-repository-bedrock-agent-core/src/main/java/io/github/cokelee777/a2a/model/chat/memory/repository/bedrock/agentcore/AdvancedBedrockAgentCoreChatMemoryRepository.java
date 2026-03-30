package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;

/**
 * Bedrock AgentCore-specific extended interface for {@link ChatMemoryRepository} that
 * provides access to <strong>long-term memory</strong> operations.
 *
 * <p>
 * Bedrock AgentCore separates memory into two layers:
 * <ul>
 * <li><strong>Short-term (Events API)</strong>: raw conversation events stored per
 * session. The base {@link ChatMemoryRepository} operations ({@code saveAll},
 * {@code findByConversationId}, {@code deleteByConversationId}) map to this layer.</li>
 * <li><strong>Long-term (MemoryRecords API)</strong>: structured memories extracted from
 * conversations by Bedrock's memory strategies. This interface adds operations for
 * semantic retrieval, listing, and deletion of these records.</li>
 * </ul>
 *
 * <p>
 * The {@code memoryId} is always taken from the underlying
 * {@link BedrockAgentCoreChatMemoryConfig} and does not need to be supplied by the
 * caller.
 *
 * <p>
 * For multi-actor (e.g. per-user) scenarios, use the overloads that take an explicit
 * {@code actorId}. The {@link ChatMemoryRepository} methods delegate to those overloads
 * using {@link BedrockAgentCoreChatMemoryConfig#getActorId()}.
 *
 * @author Chaemin Lee
 * @see BedrockAgentCoreChatMemoryRepository
 * @see BedrockAgentCoreChatMemoryConfig
 */
public interface AdvancedBedrockAgentCoreChatMemoryRepository extends ChatMemoryRepository {

	/**
	 * Lists conversation ids (Bedrock session ids) for the given actor.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @return session ids, never {@code null}
	 */
	List<String> findConversationIds(String actorId);

	/**
	 * Loads all messages for a conversation scoped to the given actor.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @param conversationId the conversation id (Bedrock session id)
	 * @return messages in order, never {@code null}
	 */
	List<Message> findByConversationId(String actorId, String conversationId);

	/**
	 * Replaces all messages for a conversation scoped to the given actor.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @param conversationId the conversation id (Bedrock session id)
	 * @param messages the messages to store
	 */
	void saveAll(String actorId, String conversationId, List<Message> messages);

	/**
	 * Deletes all messages for a conversation scoped to the given actor.
	 * @param actorId the Bedrock AgentCore actor identifier
	 * @param conversationId the conversation id (Bedrock session id)
	 */
	void deleteByConversationId(String actorId, String conversationId);

	/**
	 * Semantically searches long-term memory records under the namespace.
	 * @param namespace namespace prefix for stored records
	 * @param searchQuery natural-language query (up to 10,000 characters)
	 * @return matching summaries ordered by relevance, never {@code null}
	 */
	List<MemoryRecordSummary> retrieveMemoryRecords(String namespace, String searchQuery);

	/**
	 * Lists long-term memory records under the namespace without semantic search.
	 * @param namespace namespace prefix
	 * @return summaries, never {@code null}
	 */
	List<MemoryRecordSummary> listMemoryRecords(String namespace);

	/**
	 * Deletes a long-term memory record by id.
	 * @param memoryRecordId record identifier
	 */
	void deleteMemoryRecord(String memoryRecordId);

}
