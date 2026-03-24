package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.remote.RemoteAgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the orchestrator {@link ChatClient} used on the invocation
 * path.
 *
 * <p>
 * Registers downstream A2A agents as default tools and attaches advisors. Autowired
 * {@link ChatClient.Builder} comes from Spring AI auto-configuration.
 * </p>
 */
@Configuration
public class InvocationConfiguration {

	/**
	 * Builds the routing {@link ChatClient} with downstream A2A agents registered as
	 * tools.
	 * @param builder the Spring AI autoconfigured builder
	 * @param remoteAgentTools the downstream agent {@link Tool} component
	 * @return the configured {@link ChatClient}
	 */
	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, RemoteAgentTools remoteAgentTools) {
		return builder.clone().defaultTools(remoteAgentTools).defaultAdvisors(new SimpleLoggerAdvisor()).build();
	}

}
