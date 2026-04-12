package io.github.cokelee777.agent.host.invocation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Registers the blocking and streaming {@link ChatClient} beans for the invocation path.
 *
 * <p>
 * Tools ({@link io.github.cokelee777.agent.host.remote.RemoteAgentTools}) are NOT
 * registered as defaults here; they are created per-request by
 * {@link DefaultInvocationService} so that a streaming-aware instance (with an
 * {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}) can be
 * supplied for SSE calls and a plain instance for blocking calls.
 * </p>
 */
@Configuration
public class InvocationConfiguration {

	/** Blocking ChatClient for {@code POST /invocations} (non-SSE). */
	@Bean
	@Qualifier("chatClient")
	public ChatClient chatClient(ChatClient.Builder builder) {
		return builder.clone().defaultAdvisors(new SimpleLoggerAdvisor()).build();
	}

	/**
	 * Streaming ChatClient for {@code POST /invocations} with
	 * {@code Accept: text/event-stream}.
	 */
	@Bean
	@Qualifier("streamingChatClient")
	public ChatClient streamingChatClient(ChatClient.Builder builder) {
		return builder.clone().defaultAdvisors(new SimpleLoggerAdvisor()).build();
	}

	/**
	 * Provide executor for sse using virtual threads.
	 */
	@Bean
	public Executor sseEmitterTaskExecutor() {
		ThreadFactory factory = Thread.ofVirtual().name("sse-emitter-task", 1).factory();
		return Executors.newThreadPerTaskExecutor(factory);
	}

}
