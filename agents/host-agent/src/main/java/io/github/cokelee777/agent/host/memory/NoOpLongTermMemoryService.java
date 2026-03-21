package io.github.cokelee777.agent.host.memory;

import java.util.Collections;
import java.util.List;

/**
 * No-operation implementation of {@link LongTermMemoryService}.
 *
 * <p>
 * Used when {@code aws.bedrock.agent-core.memory.mode} is {@code none} or
 * {@code short_term}. Always returns an empty list.
 * </p>
 */
public class NoOpLongTermMemoryService implements LongTermMemoryService {

	@Override
	public List<String> retrieveRelevant(String actorId, String searchQuery) {
		return Collections.emptyList();
	}

}
