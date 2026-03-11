package io.github.cokelee777.agentcore.common.transport;

import io.a2a.client.http.A2ACardResolver;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link A2aTransport}.
 *
 * <p>
 * Uses Mockito spies to intercept {@link A2aTransport#executeMessage} so that tests never
 * require a real HTTP connection. The package-private
 * {@link A2aTransport#A2aTransport(A2ACardResolver)} constructor is used to inject a mock
 * {@link A2ACardResolver}.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class A2aTransportTest {

	@Mock
	private A2ACardResolver mockCardResolver;

	@Mock
	private AgentCard mockAgentCard;

	private final Message testMessage = new Message.Builder().role(Message.Role.USER)
		.parts(new io.a2a.spec.TextPart("hello"))
		.build();

	@Test
	void send_executeMessageReturnsText_returnsText() throws Exception {
		when(mockCardResolver.getAgentCard()).thenReturn(mockAgentCard);
		A2aTransport spy = spy(new A2aTransport(mockCardResolver));
		doReturn(Optional.of("response")).when(spy).executeMessage(any(), any());

		Optional<String> result = spy.send(testMessage, 5);

		assertThat(result).contains("response");
	}

	@Test
	void send_executeMessageReturnsEmpty_returnsEmpty() throws Exception {
		when(mockCardResolver.getAgentCard()).thenReturn(mockAgentCard);
		A2aTransport spy = spy(new A2aTransport(mockCardResolver));
		doReturn(Optional.empty()).when(spy).executeMessage(any(), any());

		Optional<String> result = spy.send(testMessage, 5);

		assertThat(result).isEmpty();
	}

	@Test
	void send_executeMessageThrows_returnsEmpty() throws Exception {
		when(mockCardResolver.getAgentCard()).thenReturn(mockAgentCard);
		A2aTransport spy = spy(new A2aTransport(mockCardResolver));
		doThrow(new RuntimeException("downstream failure")).when(spy).executeMessage(any(), any());

		Optional<String> result = spy.send(testMessage, 5);

		assertThat(result).isEmpty();
	}

	@Test
	void send_timeout_returnsEmpty() throws Exception {
		// timeoutSeconds=0 이면 Future.get() 이 async task 시작 전에 TimeoutException 을 던질 수
		// 있으므로
		// 두 스텁 모두 호출되지 않을 수 있다. lenient() 로 strict stubbing 검증을 면제한다.
		lenient().when(mockCardResolver.getAgentCard()).thenReturn(mockAgentCard);
		A2aTransport spy = spy(new A2aTransport(mockCardResolver));
		lenient().doAnswer(invocation -> {
			Thread.sleep(500);
			return Optional.of("late response");
		}).when(spy).executeMessage(any(), any());

		Optional<String> result = spy.send(testMessage, 0);

		assertThat(result).isEmpty();
	}

	@Test
	void send_cardResolverThrows_returnsEmpty() {
		when(mockCardResolver.getAgentCard()).thenThrow(new RuntimeException("network error"));
		A2aTransport transport = new A2aTransport(mockCardResolver);

		Optional<String> result = transport.send(testMessage, 5);

		assertThat(result).isEmpty();
	}

	@Test
	void send_calledTwice_cardResolverInvokedOnce() throws Exception {
		when(mockCardResolver.getAgentCard()).thenReturn(mockAgentCard);
		A2aTransport spy = spy(new A2aTransport(mockCardResolver));
		doReturn(Optional.of("ok")).when(spy).executeMessage(any(), any());

		spy.send(testMessage, 5);
		spy.send(testMessage, 5);

		verify(mockCardResolver, times(1)).getAgentCard();
	}

}
