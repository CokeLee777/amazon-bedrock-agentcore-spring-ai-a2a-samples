package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.SearchCriteria;

import java.util.Collections;
import java.util.List;

/**
 * Amazon Bedrock AgentCore implementation of {@link LongTermMemoryService}.
 *
 * <p>
 * Retrieves relevant memory records via {@code retrieveMemoryRecords} using the namespace
 * pattern {@code /strategies/{strategyId}/actors/{actorId}}.
 * </p>
 *
 * <p>
 * <strong>Note on namespace format:</strong> the
 * {@code /strategies/{strategyId}/actors/{actorId}} pattern is not formally documented in
 * the SDK Javadoc. Verify the actual namespace value from a {@code listMemoryRecords}
 * response before deploying.
 * </p>
 */
@Slf4j
public class BedrockLongTermMemoryService implements LongTermMemoryService {

	private final BedrockAgentCoreClient client;

	private final BedrockMemoryProperties properties;

	/**
	 * Creates the service, asserting that {@code memoryId} and {@code strategyId} are
	 * provided.
	 * @param client the Bedrock client
	 * @param properties the memory properties
	 */
	public BedrockLongTermMemoryService(BedrockAgentCoreClient client, BedrockMemoryProperties properties) {
		Assert.hasText(properties.memoryId(),
				"aws.bedrock.agent-core.memory.memory-id must be set when mode is not 'none'");
		Assert.hasText(properties.strategyId(),
				"aws.bedrock.agent-core.memory.strategy-id must be set when mode includes long-term");

		this.client = client;
		this.properties = properties;
	}

	@Override
	public List<String> retrieveRelevant(String actorId, String searchQuery) {
		String strategyId = properties.strategyId();
		try {
			String namespace = "/strategies/" + strategyId + "/actors/" + actorId;
			SearchCriteria criteria = SearchCriteria.builder()
				.memoryStrategyId(strategyId)
				.searchQuery(searchQuery)
				.build();
			RetrieveMemoryRecordsRequest request = RetrieveMemoryRecordsRequest.builder()
				.memoryId(properties.memoryId())
				.namespace(namespace)
				.searchCriteria(criteria)
				.maxResults(properties.longTermMaxResults())
				.build();
			RetrieveMemoryRecordsResponse response = client.retrieveMemoryRecords(request);
			if (response.memoryRecordSummaries() == null) {
				return Collections.emptyList();
			}
			return response.memoryRecordSummaries()
				.stream()
				.filter(s -> s.content() != null)
				.map(s -> s.content().text())
				.filter(t -> t != null && !t.isBlank())
				.toList();
		}
		catch (Exception ex) {
			log.error("Failed to retrieve long-term memories for actor={}", actorId, ex);
			throw ex;
		}
	}

}
