package io.github.cokelee777.a2a.orchestrator.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures in-memory, session-scoped conversation memory for the LLM.
 *
 * <p>
 * Uses {@link InMemoryChatMemoryRepository} as the backing store and
 * {@link MessageWindowChatMemory} to cap the context window. Memory is not persisted
 * across restarts.
 * </p>
 */
@Configuration
public class ChatMemoryConfig {

	/**
	 * Provides a simple in-memory store for chat message history.
	 * @return a new {@link InMemoryChatMemoryRepository}
	 */
	@Bean
	public ChatMemoryRepository chatMemoryRepository() {
		return new InMemoryChatMemoryRepository();
	}

	/**
	 * Creates a sliding-window chat memory capped at {@code chat.memory.max-messages}
	 * messages (default 20).
	 * @param chatMemoryRepository the backing repository
	 * @param maxMessages maximum number of messages to retain per session
	 * @return the configured {@link ChatMemory}
	 */
	@Bean
	public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository,
			@Value("${chat.memory.max-messages:20}") int maxMessages) {
		return MessageWindowChatMemory.builder()
			.chatMemoryRepository(chatMemoryRepository)
			.maxMessages(maxMessages)
			.build();
	}

}
