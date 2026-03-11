package io.github.cokelee777.agentcore.autoconfigure;

import io.a2a.client.http.A2AHttpClient;
import io.a2a.client.http.JdkA2AHttpClient;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStore;
import io.github.cokelee777.agentcore.autoconfigure.controller.A2AJsonRpcController;
import io.github.cokelee777.agentcore.autoconfigure.properties.A2AServerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Auto-configuration activated when an {@link AgentExecutor} bean is present.
 *
 * <p>
 * Wires up the full A2A server infrastructure: task store, queue manager, push
 * notification support, thread pool, and the {@link DefaultRequestHandler}. Intended
 * exclusively for A2A server modules (e.g., order, delivery, payment agents), not
 * orchestrators.
 * </p>
 */
@AutoConfiguration
@ConditionalOnBean(AgentExecutor.class)
@EnableConfigurationProperties(A2AServerProperties.class)
public class A2AServerAutoConfiguration {

	/**
	 * Create a new {@link A2AServerAutoConfiguration}.
	 */
	public A2AServerAutoConfiguration() {
	}

	/**
	 * Assembles the JSON-RPC controller by wiring a {@link DefaultRequestHandler} from
	 * the provided infrastructure beans.
	 * @param agentExecutor the agent executor that processes incoming tasks
	 * @param taskStore the task store for persisting task state
	 * @param queueManager the queue manager for per-task event queues
	 * @param pushNotificationConfigStore the store for push-notification subscriptions
	 * @param pushNotificationSender the sender for push notifications
	 * @param agentExecutorService the thread pool used to run agent tasks
	 * @return a fully configured {@link A2AJsonRpcController}
	 */
	@Bean
	@ConditionalOnMissingBean
	public A2AJsonRpcController a2aJsonRpcController(AgentExecutor agentExecutor, TaskStore taskStore,
			QueueManager queueManager, PushNotificationConfigStore pushNotificationConfigStore,
			PushNotificationSender pushNotificationSender, ExecutorService agentExecutorService) {

		DefaultRequestHandler requestHandler = DefaultRequestHandler.create(agentExecutor, taskStore, queueManager,
				pushNotificationConfigStore, pushNotificationSender, agentExecutorService);

		return new A2AJsonRpcController(requestHandler);
	}

	/**
	 * Provides an in-memory task store when no custom store bean is present.
	 * @return a new {@link InMemoryTaskStore}
	 */
	@Bean
	@ConditionalOnMissingBean
	public InMemoryTaskStore taskStore() {
		return new InMemoryTaskStore();
	}

	/**
	 * Provides the queue manager that manages per-task event queues.
	 * @param taskStore the task store used by the queue manager for task lookups
	 * @return a new {@link InMemoryQueueManager}
	 */
	@Bean
	@ConditionalOnMissingBean
	public QueueManager queueManager(InMemoryTaskStore taskStore) {
		return new InMemoryQueueManager(taskStore);
	}

	/**
	 * Provides an in-memory push-notification config store when none is present.
	 * @return a new {@link InMemoryPushNotificationConfigStore}
	 */
	@Bean
	@ConditionalOnMissingBean
	public PushNotificationConfigStore pushNotificationConfigStore() {
		return new InMemoryPushNotificationConfigStore();
	}

	/**
	 * Provides the push-notification sender backed by the given config store.
	 * @param pushNotificationConfigStore the config store for notification subscriptions
	 * @param a2AHttpClient the HTTP client used to deliver push notifications
	 * @return a new {@link BasePushNotificationSender}
	 */
	@Bean
	@ConditionalOnMissingBean
	public BasePushNotificationSender pushNotificationSender(PushNotificationConfigStore pushNotificationConfigStore,
			A2AHttpClient a2AHttpClient) {
		return new BasePushNotificationSender(pushNotificationConfigStore, a2AHttpClient);
	}

	/**
	 * Provides the HTTP client used to deliver push notifications when no custom bean is
	 * present.
	 * @return a new {@link JdkA2AHttpClient}
	 */
	@Bean
	@ConditionalOnMissingBean
	public A2AHttpClient a2AHttpClient() {
		return new JdkA2AHttpClient();
	}

	/**
	 * Provides the thread pool used to execute {@link AgentExecutor} tasks.
	 * @param props A2A server configuration properties
	 * @return a {@link ThreadPoolExecutor} sized according to {@code props}
	 */
	@Bean
	@ConditionalOnMissingBean
	public ExecutorService agentExecutorService(A2AServerProperties props) {
		return new ThreadPoolExecutor(props.executorCorePoolSize(), props.executorMaxPoolSize(), 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(props.executorQueueCapacity()), new ThreadPoolExecutor.CallerRunsPolicy());
	}

}