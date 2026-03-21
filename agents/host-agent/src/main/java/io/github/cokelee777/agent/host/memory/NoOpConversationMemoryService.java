package io.github.cokelee777.agent.host.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.Collections;
import java.util.List;

/**
 * No-operation implementation of {@link ConversationMemoryService}.
 *
 * <p>
 * Used when {@code aws.bedrock.agent-core.memory.mode=none}. All operations are no-ops so
 * the application starts without AWS credentials.
 * </p>
 */
public class NoOpConversationMemoryService implements ConversationMemoryService {

	@Override
	public List<Message> loadHistory(String actorId, String sessionId) {
		return Collections.emptyList();
	}

	@Override
	public void appendUserTurn(String actorId, String sessionId, String userText) {
	}

	@Override
	public void appendAssistantTurn(String actorId, String sessionId, String assistantText) {
	}

}
