package io.github.cokelee777.a2a.orchestrator.config;

import io.github.cokelee777.a2a.orchestrator.tools.A2aTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the Spring AI {@link ChatClient} used by the orchestrator.
 *
 * <p>
 * Wires the Bedrock {@link ChatModel} with a customer-support system prompt, all
 * discovered {@link A2aTool} beans as registered tool functions, and a
 * {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor} for
 * session-scoped conversation history.
 * </p>
 */
@Configuration
public class BedrockChatModelConfig {

	private static final String SYSTEM_PROMPT = """
			당신은 주문/배송 고객 지원 에이전트입니다.
			- 주문 내역/목록 조회(예: "내 주문 보여줘"): 현재 사용자 ID(memberId)를 getOrderList 도구에 넣어 호출하세요.
			- 주문 취소 가능 여부: checkOrderCancellability 도구에 주문번호(ORD-xxxx)를 넣어 호출하세요.
			- 배송 조회: trackDelivery 도구에 운송장번호(TRACK-xxxx)를 넣어 호출하세요.
			- 결제/환불 상태: getPaymentStatus 도구에 주문번호를 넣어 호출하세요.
			""";

	/**
	 * Creates the {@link ChatClient} with the system prompt, all A2A tools, and chat
	 * memory advisor pre-configured.
	 * @param chatModel the Bedrock Converse {@link ChatModel} auto-configured by Spring
	 * AI
	 * @param chatMemory the session-scoped {@link ChatMemory} bean
	 * @param tools all {@link A2aTool} implementations found in the application context
	 * @param <T> concrete {@link A2aTool} subtype
	 * @return the fully wired {@link ChatClient}
	 */
	@Bean
	public <T extends A2aTool> ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory, List<T> tools) {
		return ChatClient.builder(chatModel)
			.defaultSystem(SYSTEM_PROMPT)
			.defaultTools(tools.toArray())
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor())
			.build();
	}

}
