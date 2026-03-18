package io.github.cokelee777.a2a.server.autoconfigure;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.config.A2AConfigProvider;
import io.a2a.server.config.DefaultValuesConfigProvider;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStateProvider;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.AgentCard;
import io.github.cokelee777.a2a.server.controller.AgentCardController;
import io.github.cokelee777.a2a.server.controller.MessageController;
import io.github.cokelee777.a2a.server.controller.TaskController;
import io.github.cokelee777.a2a.server.executor.DefaultAgentExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring Boot auto-configuration for A2A Server.
 *
 * <p>
 * Automatically enables A2A protocol support when Spring AI ChatClient is on the
 * classpath. Provides A2A controllers, agent card metadata, and task API support.
 *
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@ConditionalOnProperty(prefix = A2AServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@EnableConfigurationProperties(A2AServerProperties.class)
public class A2AServerAutoConfiguration {

	/**
	 * Log AgentCard at startup. Applications MUST provide AgentCard bean.
	 */
	@Autowired
	public void logAgentCard(AgentCard agentCard) {
		log.info("Using AgentCard: {} (version: {})", agentCard.name(), agentCard.version());
	}

	/**
	 * Logs that a bean is being auto-configured. Use at the start of each {@code @Bean}
	 * method that registers a default implementation.
	 * @param component short name of the component (e.g. "InMemoryTaskStore")
	 * @param purpose one-line purpose (e.g. "task management")
	 */
	private static void logAutoConfig(String component, String purpose) {
		log.info("Auto-configuring {} for {}", component, purpose);
	}

	/**
	 * REST controller for agent card metadata (/.well-known/agent-card.json).
	 */
	@Bean
	@ConditionalOnMissingBean
	public AgentCardController agentCardController(AgentCard agentCard) {
		logAutoConfig("AgentCardController", "agent card endpoint");
		return new AgentCardController(agentCard);
	}

	/**
	 * REST controller for A2A message handling.
	 */
	@Bean
	@ConditionalOnMissingBean
	public MessageController messageController(RequestHandler requestHandler) {
		logAutoConfig("MessageController", "A2A message handling");
		return new MessageController(requestHandler);
	}

	/**
	 * REST controller for A2A task API.
	 */
	@Bean
	@ConditionalOnMissingBean
	public TaskController taskController(RequestHandler requestHandler) {
		logAutoConfig("TaskController", "A2A task API");
		return new TaskController(requestHandler);
	}

	/**
	 * Provide default TaskStore (InMemoryTaskStore).
	 */
	@Bean
	@ConditionalOnMissingBean
	public TaskStore taskStore() {
		logAutoConfig("InMemoryTaskStore", "task management");
		// TODO: Should implements Custom Memory Task Store
		return new InMemoryTaskStore();
	}

	/**
	 * Provide default QueueManager (InMemoryQueueManager).
	 */
	@Bean
	@ConditionalOnMissingBean
	public QueueManager queueManager(TaskStore taskStore) {
		logAutoConfig("InMemoryQueueManager", "event queue management");
		// TODO: Should implements Custom QueueManager
		return new InMemoryQueueManager((TaskStateProvider) taskStore);
	}

	/**
	 * Provide default PushNotificationConfigStore (InMemoryPushNotificationConfigStore).
	 */
	@Bean
	@ConditionalOnMissingBean
	public PushNotificationConfigStore pushNotificationConfigStore() {
		logAutoConfig("InMemoryPushNotificationConfigStore", "push notification config store");
		// TODO: Should implements Custom PushNotificationConfigStore
		return new InMemoryPushNotificationConfigStore();
	}

	/**
	 * Provide default PushNotificationSender (BasePushNotificationSender).
	 */
	@Bean
	@ConditionalOnMissingBean
	public PushNotificationSender pushNotificationSender(PushNotificationConfigStore pushNotificationConfigStore) {
		logAutoConfig("BasePushNotificationSender", "push notification sender");
		// TODO: Should implements Custom PushNotificationSender
		return new BasePushNotificationSender(pushNotificationConfigStore);
	}

	/**
	 * Provide default values for A2A SDK configuration keys.
	 */
	@Bean
	@ConditionalOnMissingBean
	public DefaultValuesConfigProvider defaultValuesConfigProvider() {
		logAutoConfig("DefaultValuesConfigProvider", "A2A default values");
		return new DefaultValuesConfigProvider();
	}

	/**
	 * Configuration provider for A2A settings. If a property is not found in the Spring
	 * Environment, it falls back to default values provided by
	 * DefaultValuesConfigProvider.
	 */
	@Bean
	@ConditionalOnMissingBean
	public SpringA2AConfigProvider configProvider(Environment environment,
			DefaultValuesConfigProvider defaultValuesConfigProvider) {
		logAutoConfig("SpringA2AConfigProvider", "configuration");
		return new SpringA2AConfigProvider(environment, defaultValuesConfigProvider);
	}

	/**
	 * Provide internal executor for async agent operations.
	 */
	@Bean
	@ConditionalOnMissingBean(name = "a2aInternalExecutor")
	public Executor a2aInternalExecutor(SpringA2AConfigProvider configProvider) {
		logAutoConfig("A2A internal executor", "async agent operations");
		int corePoolSize = Integer.parseInt(configProvider.getValue("a2a.executor.core-pool-size"));
		int maxPoolSize = Integer.parseInt(configProvider.getValue("a2a.executor.max-pool-size"));
		long keepAliveSeconds = Long.parseLong(configProvider.getValue("a2a.executor.keep-alive-seconds"));

		log.debug("A2A internal executor: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}", corePoolSize,
				maxPoolSize, keepAliveSeconds);

		AtomicInteger threadCounter = new AtomicInteger(1);
		// Non-daemon threads as per A2A spec

		return new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveSeconds, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(), runnable -> {
					Thread thread = new Thread(runnable);
					thread.setName("a2a-agent-executor-" + threadCounter.getAndIncrement());
					thread.setDaemon(false); // Non-daemon threads as per A2A spec
					return thread;
				});
	}

	/**
	 * Provide RequestHandler wiring all A2A SDK components together.
	 *
	 * <p>
	 * Note: Applications must provide their own {@link AgentExecutor} bean by extending
	 * {@link DefaultAgentExecutor} and implementing the {@code executeAsMessage} method.
	 */
	@Bean
	@ConditionalOnMissingBean
	public RequestHandler requestHandler(AgentExecutor agentExecutor, TaskStore taskStore, QueueManager queueManager,
			PushNotificationConfigStore pushConfigStore, PushNotificationSender pushSender,
			@Qualifier("a2aInternalExecutor") Executor executor) {
		logAutoConfig("DefaultRequestHandler", "A2A request handling");
		return DefaultRequestHandler.create(agentExecutor, taskStore, queueManager, pushConfigStore, pushSender,
				executor);
	}

}
