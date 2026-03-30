package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;

/**
 * Bedrock AgentCore extension of {@link ChatMemoryRepository} with long-term memory
 * (MemoryRecords API): semantic retrieve, list, and delete.
 *
 * <p>
 * Short-term conversation events are mapped by the base {@link ChatMemoryRepository}
 * operations. The configured {@code memoryId} applies to all calls.
 * </p>
 *
 * @author Chaemin Lee
 * @see BedrockAgentCoreChatMemoryRepository
 */
public interface AdvancedBedrockAgentCoreChatMemoryRepository extends ChatMemoryRepository {

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
