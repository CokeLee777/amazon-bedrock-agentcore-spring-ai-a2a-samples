# Host-Agent AgentCore Memory 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** host-agent에 Amazon Bedrock AgentCore Memory를 연동해 `/invocations` 호출마다 short-term(대화 이력) 및 long-term(전략 기반 지식) 컨텍스트를 LLM에 주입하고, 매 턴의 USER/ASSISTANT 이벤트를 저장한다.

**Architecture:** `memory` 패키지에 `ConversationMemoryService`·`LongTermMemoryService` 인터페이스와 Bedrock/NoOp 구현체를 두고, `invocation` 패키지의 `DefaultInvocationService`가 mode에 따라 이를 조합해 ChatClient를 호출한다. 빈 등록은 `NoOpMemoryConfiguration`(mode=none)과 `BedrockMemoryConfiguration`(mode≠none) 두 `@Configuration`에 집중한다.

**Tech Stack:** Java 25, Spring Boot 3.5.0, Spring AI 1.1.3, AWS SDK v2 (`bedrockagentcore`, `bedrockagentcorecontrol`), Lombok, JSpecify `@NullMarked`, JUnit 5 + Mockito

---

## 참고 문서

- 상위 설계: `docs/INVOCATIONS_AGENTCORE_MEMORY_DESIGN.md`
- 구현 설계: `docs/INVOCATIONS_MEMORY_IMPLEMENTATION_DESIGN.md`

## 코드 컨벤션 (반드시 준수)

- **탭 들여쓰기**. Spring Java Format(`io.spring.javaformat`)이 빌드 시 검사한다.
  빌드 전 IDE의 Spring Java Format 플러그인으로 포맷 적용하거나 `./gradlew :agents:host-agent:checkFormat` 결과를 확인한다.
- **모든 public 타입·메서드**에 Javadoc(`/** */`) 필수.
- 각 새 패키지에 `@NullMarked` `package-info.java` 추가.
- 기존 코드 스타일 참조: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/`

## 빌드 / 테스트 커맨드

```bash
# 컴파일 확인
./gradlew :agents:host-agent:compileJava

# 단위 테스트 실행
./gradlew :agents:host-agent:test

# 특정 테스트 클래스 실행
./gradlew :agents:host-agent:test --tests "io.github.cokelee777.agent.host.memory.bedrock.AgentCoreEventToMessageConverterTest"

# 포맷 검사
./gradlew :agents:host-agent:checkFormat
```

---

## 파일 맵

### 신규 생성

| 파일 경로 | 역할 |
|-----------|------|
| `…/host/invocation/InvocationRequest.java` | POST /invocations request DTO (prompt, actorId?, sessionId?) |
| `…/host/invocation/InvocationResponse.java` | 응답 DTO (content, sessionId, actorId — 항상 not null) |
| `…/host/invocation/InvocationService.java` | 오케스트레이션 인터페이스 |
| `…/host/invocation/DefaultInvocationService.java` | 메모리 로드·저장 + ChatClient 호출 구현 |
| `…/host/invocation/package-info.java` | @NullMarked |
| `…/host/memory/MemoryMode.java` | enum: NONE, SHORT_TERM, LONG_TERM, BOTH |
| `…/host/memory/ConversationMemoryService.java` | short-term 인터페이스 |
| `…/host/memory/LongTermMemoryService.java` | long-term 인터페이스 |
| `…/host/memory/NoOpConversationMemoryService.java` | NONE 모드용 no-op |
| `…/host/memory/NoOpLongTermMemoryService.java` | NONE·SHORT_TERM 모드용 no-op |
| `…/host/memory/package-info.java` | @NullMarked |
| `…/host/memory/bedrock/BedrockMemoryProperties.java` | @ConfigurationProperties prefix=`aws.bedrock.agentcore.memory` |
| `…/host/memory/bedrock/AgentCoreEventToMessageConverter.java` | List\<Event\> → List\<Message\> |
| `…/host/memory/bedrock/BedrockConversationMemoryService.java` | listEvents + createEvent (short-term) |
| `…/host/memory/bedrock/BedrockLongTermMemoryService.java` | retrieveMemoryRecords (long-term) |
| `…/host/memory/bedrock/package-info.java` | @NullMarked |
| `…/host/config/NoOpMemoryConfiguration.java` | mode=none 조건부 빈 등록 |
| `…/host/config/BedrockMemoryConfiguration.java` | mode≠none 조건부 빈 등록 |
| `…/host/config/package-info.java` | @NullMarked |

### 수정

| 파일 경로 | 변경 내용 |
|-----------|-----------|
| `…/host/InvocationsController.java` | InvocationService 주입, 응답 타입 InvocationResponse로 변경, 내부 InvocationRequest 제거 |
| `…/host/HostAgentApplication.java` | 변경 없음. `BedrockMemoryProperties`는 `NoOpMemoryConfiguration`/`BedrockMemoryConfiguration`이 각각 등록하므로 추가 불필요 |
| `application.yml` | `aws.bedrock.agentcore.memory.*` 설정 블록 추가 |
| `application-local.yml` | `mode: none` 추가 (로컬에서 AWS 자격증명 없이 실행) |

### 테스트 신규 생성

| 파일 경로 | 테스트 대상 |
|-----------|------------|
| `…/host/memory/bedrock/AgentCoreEventToMessageConverterTest.java` | Event → Message 변환 |
| `…/host/memory/bedrock/BedrockConversationMemoryServiceTest.java` | listEvents·createEvent 호출 파라미터 |
| `…/host/memory/bedrock/BedrockLongTermMemoryServiceTest.java` | retrieveMemoryRecords 호출 및 empty strategyId 처리 |
| `…/host/invocation/DefaultInvocationServiceTest.java` | mode별 분기, ChatClient 실패 시 no-save, sessionId 생성 |

---

## Task 1: DTO 및 MemoryMode

**Files:**
- Create: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/invocation/InvocationRequest.java`
- Create: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/invocation/InvocationResponse.java`
- Create: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/invocation/package-info.java`
- Create: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/MemoryMode.java`
- Create: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/package-info.java`

- [ ] **Step 1: package-info 파일 생성**

`invocation/package-info.java`:
```java
/**
 * Invocation orchestration for {@code POST /invocations}.
 */
@NullMarked
package io.github.cokelee777.agent.host.invocation;

import org.jspecify.annotations.NullMarked;
```

`memory/package-info.java`:
```java
/**
 * Memory abstractions for short-term and long-term conversation memory.
 */
@NullMarked
package io.github.cokelee777.agent.host.memory;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 2: InvocationRequest 레코드 생성**

```java
package io.github.cokelee777.agent.host.invocation;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Request payload for {@code POST /invocations}.
 *
 * <p>
 * {@code actorId} and {@code sessionId} are optional — omit them on the first message and
 * the service generates them, returning the values in {@link InvocationResponse}.
 * </p>
 *
 * @param prompt    the user message; must not be blank
 * @param actorId   the actor identifier; {@code null} on first message
 * @param sessionId the session identifier; {@code null} on first message
 */
public record InvocationRequest(@NotBlank(message = "prompt must not be blank") String prompt,
		@Nullable String actorId, @Nullable String sessionId) {

}
```

- [ ] **Step 3: InvocationResponse 레코드 생성**

```java
package io.github.cokelee777.agent.host.invocation;

/**
 * Response payload for {@code POST /invocations}.
 *
 * <p>
 * {@code sessionId} and {@code actorId} are always non-null. Clients must persist these
 * values and include them in every subsequent request to continue the conversation.
 * </p>
 *
 * @param content   the assistant response text
 * @param sessionId the session identifier used for this invocation
 * @param actorId   the actor identifier used for this invocation
 */
public record InvocationResponse(String content, String sessionId, String actorId) {

}
```

- [ ] **Step 4: MemoryMode enum 생성**

```java
package io.github.cokelee777.agent.host.memory;

/**
 * Memory usage mode for {@code POST /invocations}.
 *
 * <p>
 * Controls which memory stores are read from and written to on each invocation.
 * </p>
 */
public enum MemoryMode {

	/**
	 * Memory disabled. No AgentCore Memory API calls are made. Suitable for local
	 * development without AWS credentials.
	 */
	NONE,

	/**
	 * Short-term only. Conversation history is loaded and saved; long-term search is
	 * skipped.
	 */
	SHORT_TERM,

	/**
	 * Long-term only. Relevant records are retrieved via semantic search; history is not
	 * loaded. Trade-off: the LLM has no current-session context. Use only when cross-session
	 * knowledge recall is the primary goal.
	 */
	LONG_TERM,

	/**
	 * Both short-term and long-term. Recommended for production use.
	 */
	BOTH

}
```

- [ ] **Step 5: 컴파일 확인**

```bash
./gradlew :agents:host-agent:compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 2: 메모리 인터페이스 + No-op 구현 + NoOpMemoryConfiguration

**Files:**
- Create: `…/memory/ConversationMemoryService.java`
- Create: `…/memory/LongTermMemoryService.java`
- Create: `…/memory/NoOpConversationMemoryService.java`
- Create: `…/memory/NoOpLongTermMemoryService.java`
- Create: `…/config/NoOpMemoryConfiguration.java`
- Create: `…/config/package-info.java`

- [ ] **Step 1: ConversationMemoryService 인터페이스 생성**

```java
package io.github.cokelee777.agent.host.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Short-term conversation memory service.
 *
 * <p>
 * Loads and persists per-session conversation history so that the LLM can reference
 * previous turns within the same session.
 * </p>
 */
public interface ConversationMemoryService {

	/**
	 * Returns the conversation history for the given actor and session.
	 * Returns an empty list when there is no history (first message).
	 * @param actorId   the actor identifier
	 * @param sessionId the session identifier
	 * @return ordered list of {@link Message} objects (oldest first)
	 */
	List<Message> loadHistory(String actorId, String sessionId);

	/**
	 * Appends a user turn to the short-term memory.
	 * @param actorId   the actor identifier
	 * @param sessionId the session identifier
	 * @param userText  the user message text
	 */
	void appendUserTurn(String actorId, String sessionId, String userText);

	/**
	 * Appends an assistant turn to the short-term memory.
	 * @param actorId       the actor identifier
	 * @param sessionId     the session identifier
	 * @param assistantText the assistant response text
	 */
	void appendAssistantTurn(String actorId, String sessionId, String assistantText);

}
```

- [ ] **Step 2: LongTermMemoryService 인터페이스 생성**

```java
package io.github.cokelee777.agent.host.memory;

import java.util.List;

/**
 * Long-term memory service.
 *
 * <p>
 * Retrieves relevant knowledge for the given actor via semantic search. Long-term
 * records are populated asynchronously by a memory strategy when short-term events
 * accumulate.
 * </p>
 */
public interface LongTermMemoryService {

	/**
	 * Returns relevant long-term memory records for the actor matching the search query.
	 * Returns an empty list when no strategy is configured or mode does not use long-term.
	 * @param actorId     the actor identifier
	 * @param searchQuery the query derived from the current user prompt
	 * @return list of relevant text snippets
	 */
	List<String> retrieveRelevant(String actorId, String searchQuery);

}
```

- [ ] **Step 3: NoOpConversationMemoryService 생성**

```java
package io.github.cokelee777.agent.host.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.Collections;
import java.util.List;

/**
 * No-operation implementation of {@link ConversationMemoryService}.
 *
 * <p>
 * Used when {@code aws.bedrock.agentcore.memory.mode=none}. All operations are
 * no-ops so the application starts without AWS credentials.
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
```

- [ ] **Step 4: NoOpLongTermMemoryService 생성**

```java
package io.github.cokelee777.agent.host.memory;

import java.util.Collections;
import java.util.List;

/**
 * No-operation implementation of {@link LongTermMemoryService}.
 *
 * <p>
 * Used when {@code aws.bedrock.agentcore.memory.mode} is {@code none} or
 * {@code short_term}. Always returns an empty list.
 * </p>
 */
public class NoOpLongTermMemoryService implements LongTermMemoryService {

	@Override
	public List<String> retrieveRelevant(String actorId, String searchQuery) {
		return Collections.emptyList();
	}

}
```

- [ ] **Step 5: config/package-info.java 생성**

```java
/**
 * Spring {@code @Configuration} classes for memory bean registration.
 */
@NullMarked
package io.github.cokelee777.agent.host.config;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 6: NoOpMemoryConfiguration 생성**

> **설계 결정**: `NoOpMemoryConfiguration`에 `@EnableConfigurationProperties(BedrockMemoryProperties.class)`를 추가한다.
> `DefaultInvocationService`는 mode에 관계없이 `BedrockMemoryProperties`를 주입받으므로, NONE 모드에서도
> 해당 Properties 빈이 존재해야 한다. `NoOpMemoryConfiguration`(mode=none)과 `BedrockMemoryConfiguration`(mode≠none)은
> 서로 배타적으로 활성화되므로 이중 등록이 발생하지 않는다. `HostAgentApplication`의 `@EnableConfigurationProperties`에는
> `BedrockMemoryProperties`를 추가하지 않는다.

```java
package io.github.cokelee777.agent.host.config;

import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.NoOpConversationMemoryService;
import io.github.cokelee777.agent.host.memory.NoOpLongTermMemoryService;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers no-operation memory beans when {@code aws.bedrock.agentcore.memory.mode=none}.
 *
 * <p>
 * Allows the application to start without AWS credentials (e.g., local development).
 * Also registers {@link BedrockMemoryProperties} so that {@code DefaultInvocationService}
 * can read the mode even in NONE mode without requiring AWS infrastructure beans.
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "aws.bedrock.agentcore.memory.mode", havingValue = "none")
@EnableConfigurationProperties(BedrockMemoryProperties.class)
public class NoOpMemoryConfiguration {

	/**
	 * No-op conversation memory service bean.
	 * @return a {@link NoOpConversationMemoryService} instance
	 */
	@Bean
	public ConversationMemoryService conversationMemoryService() {
		return new NoOpConversationMemoryService();
	}

	/**
	 * No-op long-term memory service bean.
	 * @return a {@link NoOpLongTermMemoryService} instance
	 */
	@Bean
	public LongTermMemoryService longTermMemoryService() {
		return new NoOpLongTermMemoryService();
	}

}
```

> **중간 상태 주의**: Task 2 완료 시점에는 `application-local.yml`에 `mode: none`이 아직 없으므로
> Spring 컨텍스트를 실제 구동하면 `ConversationMemoryService` 빈이 없어 실패한다.
> 이 상태는 Task 7 완료 후 해소된다. `compileJava`만으로 검증하고 컨텍스트 구동은 Task 7 이후에 한다.

- [ ] **Step 7: 컴파일 확인**

```bash
./gradlew :agents:host-agent:compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 3: AgentCoreEventToMessageConverter

**Files:**
- Create: `…/memory/bedrock/AgentCoreEventToMessageConverter.java`
- Create: `…/memory/bedrock/package-info.java`
- Test: `…/test/…/memory/bedrock/AgentCoreEventToMessageConverterTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/io/github/cokelee777/agent/host/memory/bedrock/AgentCoreEventToMessageConverterTest.java`:

```java
package io.github.cokelee777.agent.host.memory.bedrock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCoreEventToMessageConverterTest {

	private AgentCoreEventToMessageConverter converter;

	@BeforeEach
	void setUp() {
		converter = new AgentCoreEventToMessageConverter();
	}

	@Test
	void emptyList_returnsEmpty() {
		assertThat(converter.toMessages(List.of())).isEmpty();
	}

	@Test
	void userEvent_returnsUserMessage() {
		Event event = userEvent("hello", Instant.now());
		List<Message> messages = converter.toMessages(List.of(event));
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("hello");
	}

	@Test
	void assistantEvent_returnsAssistantMessage() {
		Event event = assistantEvent("hi there", Instant.now());
		List<Message> messages = converter.toMessages(List.of(event));
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("hi there");
	}

	@Test
	void multipleEvents_sortedByTimestamp() {
		Instant t1 = Instant.ofEpochSecond(1000);
		Instant t2 = Instant.ofEpochSecond(2000);
		// 역순으로 추가해도 시간순 정렬되어야 한다
		Event assistant = assistantEvent("reply", t2);
		Event user = userEvent("question", t1);
		List<Message> messages = converter.toMessages(List.of(assistant, user));
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void unknownPayload_isSkipped() {
		// payload가 없는 이벤트는 무시된다
		Event event = Event.builder().eventTimestamp(Instant.now()).build();
		assertThat(converter.toMessages(List.of(event))).isEmpty();
	}

	// --- helpers ---

	private Event userEvent(String text, Instant timestamp) {
		return buildEvent(text, Role.USER, timestamp);
	}

	private Event assistantEvent(String text, Instant timestamp) {
		return buildEvent(text, Role.ASSISTANT, timestamp);
	}

	private Event buildEvent(String text, Role role, Instant timestamp) {
		Conversational conversational = Conversational.builder()
			.content(Content.fromText(text))
			.role(role)
			.build();
		PayloadType payload = PayloadType.fromConversational(conversational);
		return Event.builder().eventTimestamp(timestamp).payload(List.of(payload)).build();
	}

}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.AgentCoreEventToMessageConverterTest"
```
Expected: FAIL (AgentCoreEventToMessageConverter class not found)

- [ ] **Step 3: bedrock/package-info.java 생성**

```java
/**
 * Amazon Bedrock AgentCore Memory implementations.
 */
@NullMarked
package io.github.cokelee777.agent.host.memory.bedrock;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 4: AgentCoreEventToMessageConverter 구현**

```java
package io.github.cokelee777.agent.host.memory.bedrock;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Converts AgentCore {@link Event} objects to Spring AI {@link Message} objects.
 *
 * <p>
 * Only {@code Conversational} payload events with {@code USER} or {@code ASSISTANT}
 * roles are converted. Events with unknown or missing payloads are silently skipped.
 * The returned list is sorted by {@code eventTimestamp} ascending.
 * </p>
 *
 * <p>
 * This class is not annotated with {@code @Component}. It is registered as a
 * {@code @Bean} inside {@link io.github.cokelee777.agent.host.config.BedrockMemoryConfiguration}
 * to avoid double-registration with component scan.
 * </p>
 */
public class AgentCoreEventToMessageConverter {

	/**
	 * Converts a list of {@link Event} objects to Spring AI {@link Message} objects.
	 * @param events the raw events from AgentCore Memory
	 * @return sorted list of messages; empty list if input is empty or all events are skipped
	 */
	public List<Message> toMessages(List<Event> events) {
		return events.stream()
			.sorted(Comparator.comparing(Event::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
			.map(this::toMessage)
			.filter(Objects::nonNull)
			.toList();
	}

	private Message toMessage(Event event) {
		if (event.payload() == null || event.payload().isEmpty()) {
			return null;
		}
		PayloadType payload = event.payload().get(0);
		if (payload.conversational() == null) {
			return null;
		}
		String text = payload.conversational().content() != null ? payload.conversational().content().text() : "";
		Role role = payload.conversational().role();
		if (Role.USER.equals(role)) {
			return new UserMessage(text != null ? text : "");
		}
		if (Role.ASSISTANT.equals(role)) {
			return new AssistantMessage(text != null ? text : "");
		}
		return null;
	}

}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.AgentCoreEventToMessageConverterTest"
```
Expected: PASS (5 tests)

---

## Task 4: BedrockMemoryProperties + BedrockConversationMemoryService

**Files:**
- Create: `…/memory/bedrock/BedrockMemoryProperties.java`
- Create: `…/memory/bedrock/BedrockConversationMemoryService.java`
- Test: `…/test/…/memory/bedrock/BedrockConversationMemoryServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package io.github.cokelee777.agent.host.memory.bedrock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BedrockConversationMemoryServiceTest {

	@Mock
	private BedrockAgentCoreClient client;

	private BedrockMemoryProperties properties;

	private AgentCoreEventToMessageConverter converter;

	private BedrockConversationMemoryService service;

	@BeforeEach
	void setUp() {
		properties = new BedrockMemoryProperties("mem-1", io.github.cokelee777.agent.host.memory.MemoryMode.BOTH, 5,
				"strategy-1", 4);
		converter = new AgentCoreEventToMessageConverter();
		service = new BedrockConversationMemoryService(client, properties, converter);
	}

	@Test
	void loadHistory_callsListEventsWithCorrectParams() {
		ListEventsResponse response = ListEventsResponse.builder().events(List.of()).build();
		when(client.listEvents(any(ListEventsRequest.class))).thenReturn(response);

		service.loadHistory("actor-1", "session-1");

		ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(client).listEvents(captor.capture());
		ListEventsRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo("mem-1");
		assertThat(req.actorId()).isEqualTo("actor-1");
		assertThat(req.sessionId()).isEqualTo("session-1");
		assertThat(req.includePayloads()).isTrue();
		// shortTermMaxTurns=5 → maxResults=10 (5턴 × 2이벤트/턴)
		assertThat(req.maxResults()).isEqualTo(10);
	}

	@Test
	void loadHistory_convertsEventsToMessages() {
		Event userEvent = buildEvent("hi", Role.USER, Instant.ofEpochSecond(1000));
		Event assistantEvent = buildEvent("hello", Role.ASSISTANT, Instant.ofEpochSecond(2000));
		ListEventsResponse response = ListEventsResponse.builder()
			.events(List.of(assistantEvent, userEvent))
			.build();
		when(client.listEvents(any(ListEventsRequest.class))).thenReturn(response);

		List<Message> messages = service.loadHistory("actor-1", "session-1");

		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void appendUserTurn_callsCreateEventWithUserRole() {
		when(client.createEvent(any(CreateEventRequest.class))).thenReturn(CreateEventResponse.builder().build());

		service.appendUserTurn("actor-1", "session-1", "hello");

		ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(client).createEvent(captor.capture());
		CreateEventRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo("mem-1");
		assertThat(req.actorId()).isEqualTo("actor-1");
		assertThat(req.sessionId()).isEqualTo("session-1");
		assertThat(req.payload()).hasSize(1);
		assertThat(req.payload().get(0).conversational().role()).isEqualTo(Role.USER);
		assertThat(req.payload().get(0).conversational().content().text()).isEqualTo("hello");
	}

	@Test
	void appendAssistantTurn_callsCreateEventWithAssistantRole() {
		when(client.createEvent(any(CreateEventRequest.class))).thenReturn(CreateEventResponse.builder().build());

		service.appendAssistantTurn("actor-1", "session-1", "got it");

		ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(client).createEvent(captor.capture());
		CreateEventRequest req = captor.getValue();
		assertThat(req.payload().get(0).conversational().role()).isEqualTo(Role.ASSISTANT);
	}

	private Event buildEvent(String text, Role role, Instant timestamp) {
		Conversational conv = Conversational.builder().content(Content.fromText(text)).role(role).build();
		return Event.builder().eventTimestamp(timestamp).payload(List.of(PayloadType.fromConversational(conv))).build();
	}

}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.BedrockConversationMemoryServiceTest"
```
Expected: FAIL (BedrockMemoryProperties, BedrockConversationMemoryService not found)

- [ ] **Step 3: BedrockMemoryProperties 생성**

```java
package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.MemoryMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Amazon Bedrock AgentCore Memory.
 *
 * <p>
 * Bound from the {@code aws.bedrock.agentcore.memory} YAML prefix. Override with
 * environment variables (e.g., {@code BEDROCK_MEMORY_ID}).
 * </p>
 *
 * @param memoryId          the Memory resource ID or ARN
 * @param mode              the memory usage mode; defaults to {@link MemoryMode#BOTH}
 * @param shortTermMaxTurns max conversation turns to load from short-term; defaults to 10
 * @param strategyId        the Memory strategy ID for long-term retrieval
 * @param longTermMaxResults max records to return from long-term search; defaults to 4
 */
@ConfigurationProperties(prefix = "aws.bedrock.agentcore.memory")
public record BedrockMemoryProperties(
		@DefaultValue("placeholder-memory-id") String memoryId,
		@DefaultValue("BOTH") MemoryMode mode,
		@DefaultValue("10") int shortTermMaxTurns,
		@DefaultValue("placeholder-strategy-id") String strategyId,
		@DefaultValue("4") int longTermMaxResults) {

}
```

- [ ] **Step 4: BedrockConversationMemoryService 구현**

```java
package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Amazon Bedrock AgentCore implementation of {@link ConversationMemoryService}.
 *
 * <p>
 * Uses {@code listEvents} to load short-term history and {@code createEvent} to
 * append USER and ASSISTANT turns. Each turn stores two events: one for the user
 * message and one for the assistant response.
 * </p>
 *
 * <p>
 * {@code maxResults} passed to {@code listEvents} is {@code shortTermMaxTurns * 2}
 * because one turn equals one USER event plus one ASSISTANT event.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class BedrockConversationMemoryService implements ConversationMemoryService {

	private final BedrockAgentCoreClient client;

	private final BedrockMemoryProperties properties;

	private final AgentCoreEventToMessageConverter converter;

	@Override
	public List<Message> loadHistory(String actorId, String sessionId) {
		try {
			ListEventsRequest request = ListEventsRequest.builder()
				.memoryId(properties.memoryId())
				.actorId(actorId)
				.sessionId(sessionId)
				.includePayloads(true)
				.maxResults(properties.shortTermMaxTurns() * 2)
				.build();
			ListEventsResponse response = client.listEvents(request);
			List<Event> events = response.events() != null ? response.events() : Collections.emptyList();
			return converter.toMessages(events);
		}
		catch (Exception ex) {
			log.error("Failed to load history for actor={} session={}", actorId, sessionId, ex);
			throw ex;
		}
	}

	@Override
	public void appendUserTurn(String actorId, String sessionId, String userText) {
		createEvent(actorId, sessionId, userText, Role.USER);
	}

	@Override
	public void appendAssistantTurn(String actorId, String sessionId, String assistantText) {
		createEvent(actorId, sessionId, assistantText, Role.ASSISTANT);
	}

	private void createEvent(String actorId, String sessionId, String text, Role role) {
		try {
			Conversational conversational = Conversational.builder()
				.content(Content.fromText(text))
				.role(role)
				.build();
			PayloadType payload = PayloadType.fromConversational(conversational);
			CreateEventRequest request = CreateEventRequest.builder()
				.memoryId(properties.memoryId())
				.actorId(actorId)
				.sessionId(sessionId)
				.eventTimestamp(Instant.now())
				.payload(payload)
				.build();
			client.createEvent(request);
		}
		catch (Exception ex) {
			log.error("Failed to append {} turn for actor={} session={}", role, actorId, sessionId, ex);
			throw ex;
		}
	}

}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.BedrockConversationMemoryServiceTest"
```
Expected: PASS (4 tests)

---

## Task 5: BedrockLongTermMemoryService + BedrockMemoryConfiguration

**Files:**
- Create: `…/memory/bedrock/BedrockLongTermMemoryService.java`
- Create: `…/config/BedrockMemoryConfiguration.java`
- Test: `…/test/…/memory/bedrock/BedrockLongTermMemoryServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.MemoryMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BedrockLongTermMemoryServiceTest {

	@Mock
	private BedrockAgentCoreClient client;

	@Test
	void emptyStrategyId_returnsEmptyWithoutApiCall() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem-1", MemoryMode.BOTH, 10, "", 4);
		BedrockLongTermMemoryService service = new BedrockLongTermMemoryService(client, props);

		List<String> result = service.retrieveRelevant("actor-1", "query");

		assertThat(result).isEmpty();
		verifyNoInteractions(client);
	}

	@Test
	void retrieveRelevant_callsApiWithCorrectNamespaceAndCriteria() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem-1", MemoryMode.BOTH, 10, "strat-1", 4);
		BedrockLongTermMemoryService service = new BedrockLongTermMemoryService(client, props);

		MemoryRecordSummary summary = MemoryRecordSummary.builder()
			.content(MemoryRecordContent.fromText("past order info"))
			.build();
		RetrieveMemoryRecordsResponse response = RetrieveMemoryRecordsResponse.builder()
			.memoryRecordSummaries(List.of(summary))
			.build();
		when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class))).thenReturn(response);

		List<String> result = service.retrieveRelevant("actor-1", "my order");

		ArgumentCaptor<RetrieveMemoryRecordsRequest> captor = ArgumentCaptor
			.forClass(RetrieveMemoryRecordsRequest.class);
		verify(client).retrieveMemoryRecords(captor.capture());
		RetrieveMemoryRecordsRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo("mem-1");
		assertThat(req.namespace()).isEqualTo("/strategies/strat-1/actors/actor-1");
		assertThat(req.searchCriteria().memoryStrategyId()).isEqualTo("strat-1");
		assertThat(req.searchCriteria().searchQuery()).isEqualTo("my order");
		assertThat(req.maxResults()).isEqualTo(4);
		assertThat(result).containsExactly("past order info");
	}

	@Test
	void placeholderStrategyId_returnsEmptyWithoutApiCall() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem-1", MemoryMode.BOTH, 10,
				"placeholder-strategy-id", 4);
		BedrockLongTermMemoryService service = new BedrockLongTermMemoryService(client, props);

		List<String> result = service.retrieveRelevant("actor-1", "query");

		assertThat(result).isEmpty();
		verifyNoInteractions(client);
	}

}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.BedrockLongTermMemoryServiceTest"
```
Expected: FAIL

- [ ] **Step 3: BedrockLongTermMemoryService 구현**

```java
package io.github.cokelee777.agent.host.memory.bedrock;

import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

import java.util.Collections;
import java.util.List;

/**
 * Amazon Bedrock AgentCore implementation of {@link LongTermMemoryService}.
 *
 * <p>
 * Retrieves relevant memory records via {@code retrieveMemoryRecords} using the
 * namespace pattern {@code /strategies/{strategyId}/actors/{actorId}}.
 * </p>
 *
 * <p>
 * If {@code strategyId} is blank or equals the placeholder value, the API is not
 * called and an empty list is returned.
 * </p>
 *
 * <p>
 * <strong>Note on namespace format:</strong> the {@code /strategies/{strategyId}/actors/{actorId}}
 * pattern is not formally documented in the SDK Javadoc. Verify the actual namespace
 * value from a {@code listMemoryRecords} response before deploying.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class BedrockLongTermMemoryService implements LongTermMemoryService {

	private static final String PLACEHOLDER_STRATEGY_ID = "placeholder-strategy-id";

	private final BedrockAgentCoreClient client;

	private final BedrockMemoryProperties properties;

	@Override
	public List<String> retrieveRelevant(String actorId, String searchQuery) {
		String strategyId = properties.strategyId();
		if (strategyId == null || strategyId.isBlank() || PLACEHOLDER_STRATEGY_ID.equals(strategyId)) {
			log.debug("Long-term retrieval skipped: strategyId is blank or placeholder");
			return Collections.emptyList();
		}
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
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.BedrockLongTermMemoryServiceTest"
```
Expected: PASS (3 tests)

- [ ] **Step 5: BedrockMemoryConfiguration 생성**

```java
package io.github.cokelee777.agent.host.config;

import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.NoOpLongTermMemoryService;
import io.github.cokelee777.agent.host.memory.bedrock.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

/**
 * Registers Amazon Bedrock AgentCore Memory beans when
 * {@code aws.bedrock.agentcore.memory.mode} is not {@code none}.
 *
 * <p>
 * When mode is {@code short_term}, a {@link NoOpLongTermMemoryService} is registered.
 * When mode is {@code long_term} or {@code both}, a {@link BedrockLongTermMemoryService}
 * is registered.
 * </p>
 */
@Configuration
@ConditionalOnExpression("!'none'.equalsIgnoreCase('${aws.bedrock.agentcore.memory.mode:both}')")
@EnableConfigurationProperties(BedrockMemoryProperties.class)
public class BedrockMemoryConfiguration {

	/**
	 * Creates the {@link BedrockAgentCoreClient} using the configured AWS region.
	 * @param properties the memory properties providing the region indirectly via
	 * {@code spring.ai.bedrock.aws.region} or {@code BEDROCK_REGION}
	 * @param region the AWS region value
	 * @return the Bedrock AgentCore data-plane client
	 */
	@Bean
	public BedrockAgentCoreClient bedrockAgentCoreClient(
			@org.springframework.beans.factory.annotation.Value("${spring.ai.bedrock.aws.region:${BEDROCK_REGION:ap-northeast-2}}") String region) {
		return BedrockAgentCoreClient.builder().region(Region.of(region)).build();
	}

	/**
	 * Creates the event-to-message converter.
	 * @return the converter
	 */
	@Bean
	public AgentCoreEventToMessageConverter agentCoreEventToMessageConverter() {
		return new AgentCoreEventToMessageConverter();
	}

	/**
	 * Creates the Bedrock short-term conversation memory service.
	 * @param client     the Bedrock client
	 * @param properties the memory properties
	 * @param converter  the event-to-message converter
	 * @return the service
	 */
	@Bean
	public BedrockConversationMemoryService conversationMemoryService(BedrockAgentCoreClient client,
			BedrockMemoryProperties properties, AgentCoreEventToMessageConverter converter) {
		return new BedrockConversationMemoryService(client, properties, converter);
	}

	/**
	 * Creates a no-op long-term memory service when mode is {@code short_term}.
	 * @return a no-op implementation
	 */
	@Bean
	@ConditionalOnProperty(name = "aws.bedrock.agentcore.memory.mode", havingValue = "short_term")
	public LongTermMemoryService noOpLongTermMemoryService() {
		return new NoOpLongTermMemoryService();
	}

	/**
	 * Creates the Bedrock long-term memory service when mode is {@code long_term} or
	 * {@code both}.
	 * @param client     the Bedrock client
	 * @param properties the memory properties
	 * @return the service
	 */
	@Bean
	@ConditionalOnExpression(
			"'${aws.bedrock.agentcore.memory.mode:both}'.equalsIgnoreCase('long_term') or '${aws.bedrock.agentcore.memory.mode:both}'.equalsIgnoreCase('both')")
	public BedrockLongTermMemoryService bedrockLongTermMemoryService(BedrockAgentCoreClient client,
			BedrockMemoryProperties properties) {
		return new BedrockLongTermMemoryService(client, properties);
	}

}
```

- [ ] **Step 6: 컴파일 확인**

```bash
./gradlew :agents:host-agent:compileJava
```
Expected: BUILD SUCCESSFUL

---

## Task 6: DefaultInvocationService

**Files:**
- Create: `…/invocation/InvocationService.java`
- Create: `…/invocation/DefaultInvocationService.java`
- Test: `…/test/…/invocation/DefaultInvocationServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.RemoteAgentConnections;
import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.MemoryMode;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultInvocationServiceTest {

	@Mock
	private ConversationMemoryService conversationMemoryService;

	@Mock
	private LongTermMemoryService longTermMemoryService;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec callSpec;

	@Mock
	private RemoteAgentConnections connections;

	@Test
	void modeNone_noMemoryCalls() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem", MemoryMode.NONE, 10, "strat", 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service.invoke(new InvocationRequest("hello", null, null));

		assertThat(response.content()).isEqualTo("reply");
		verifyNoInteractions(conversationMemoryService);
		verifyNoInteractions(longTermMemoryService);
	}

	@Test
	void modeShortTerm_loadsHistoryAndSavesTurnsAfterChatClient() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem", MemoryMode.SHORT_TERM, 10, "strat", 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		when(conversationMemoryService.loadHistory(anyString(), anyString()))
			.thenReturn(List.of(new UserMessage("prev")));
		setupChatClientChain("ok");
		when(connections.getAgentDescriptions()).thenReturn("");

		service.invoke(new InvocationRequest("hi", "actor-1", "session-1"));

		// 저장은 ChatClient 이후에 순서대로 호출된다
		InOrder order = inOrder(chatClient, conversationMemoryService);
		order.verify(chatClient).prompt();
		order.verify(conversationMemoryService).appendUserTurn("actor-1", "session-1", "hi");
		order.verify(conversationMemoryService).appendAssistantTurn("actor-1", "session-1", "ok");
		verifyNoInteractions(longTermMemoryService);
	}

	@Test
	void modeLongTerm_retrievesRelevantAndSavesTurns() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem", MemoryMode.LONG_TERM, 10, "strat", 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		when(longTermMemoryService.retrieveRelevant(anyString(), anyString()))
			.thenReturn(List.of("past info"));
		setupChatClientChain("response");
		when(connections.getAgentDescriptions()).thenReturn("");

		service.invoke(new InvocationRequest("query", "actor-1", "session-1"));

		verify(longTermMemoryService).retrieveRelevant("actor-1", "query");
		verify(conversationMemoryService, never()).loadHistory(anyString(), anyString());
		verify(conversationMemoryService).appendUserTurn(anyString(), anyString(), eq("query"));
	}

	@Test
	void chatClientFailure_noMemorySaved() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem", MemoryMode.BOTH, 10, "strat", 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		when(conversationMemoryService.loadHistory(anyString(), anyString())).thenReturn(List.of());
		when(longTermMemoryService.retrieveRelevant(anyString(), anyString())).thenReturn(List.of());
		when(connections.getAgentDescriptions()).thenReturn("");
		// ChatClient 호출 시 예외 발생
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(any())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenThrow(new RuntimeException("LLM error"));

		assertThatThrownBy(() -> service.invoke(new InvocationRequest("hi", "actor-1", "session-1")))
			.isInstanceOf(RuntimeException.class);

		verify(conversationMemoryService, never()).appendUserTurn(anyString(), anyString(), anyString());
		verify(conversationMemoryService, never()).appendAssistantTurn(anyString(), anyString(), anyString());
	}

	@Test
	void noSessionId_generatesNewSessionIdInResponse() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem", MemoryMode.NONE, 10, "strat", 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		setupChatClientChain("hi");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service.invoke(new InvocationRequest("hello", null, null));

		assertThat(response.sessionId()).isNotBlank();
		assertThat(response.actorId()).isNotBlank();
	}

	@Test
	void providedSessionId_returnsSameSessionIdInResponse() {
		BedrockMemoryProperties props = new BedrockMemoryProperties("mem", MemoryMode.NONE, 10, "strat", 4);
		DefaultInvocationService service = new DefaultInvocationService(conversationMemoryService,
				longTermMemoryService, chatClient, connections, props);
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service.invoke(new InvocationRequest("hi", "actor-1", "sess-42"));

		assertThat(response.sessionId()).isEqualTo("sess-42");
		assertThat(response.actorId()).isEqualTo("actor-1");
	}

	// ChatClient mock chain helper
	@SuppressWarnings("unchecked")
	private void setupChatClientChain(String content) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(any())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callSpec);
		when(callSpec.content()).thenReturn(content);
	}

}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.DefaultInvocationServiceTest"
```
Expected: FAIL (DefaultInvocationService not found)

- [ ] **Step 3: InvocationService 인터페이스 생성**

```java
package io.github.cokelee777.agent.host.invocation;

/**
 * Orchestrates a single {@code POST /invocations} request.
 *
 * <p>
 * Resolves or generates {@code actorId} and {@code sessionId}, loads memory context,
 * invokes the LLM, and persists the turn.
 * </p>
 */
public interface InvocationService {

	/**
	 * Processes one invocation. Generates {@code actorId}/{@code sessionId} if absent.
	 * @param request the invocation request
	 * @return the response including LLM output and resolved identifiers
	 */
	InvocationResponse invoke(InvocationRequest request);

}
```

- [ ] **Step 4: DefaultInvocationService 구현**

> **참고**: `ROUTING_SYSTEM_PROMPT` 상수는 기존 `InvocationsController`에 있던 것을 이 클래스로 **이동**하는 것이다.
> Task 7에서 `InvocationsController`를 교체하면 컨트롤러의 복사본은 제거된다.
> 두 상수가 동일한지 반드시 확인하고 진행한다.

```java
package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.RemoteAgentConnections;
import io.github.cokelee777.agent.host.memory.ConversationMemoryService;
import io.github.cokelee777.agent.host.memory.LongTermMemoryService;
import io.github.cokelee777.agent.host.memory.MemoryMode;
import io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link InvocationService}.
 *
 * <p>
 * Execution order per request:
 * <ol>
 *   <li>Resolve {@code actorId}/{@code sessionId} (generate if absent).</li>
 *   <li>Load memory context according to the configured {@link MemoryMode}.</li>
 *   <li>Assemble system prompt and call the LLM via {@link ChatClient}.</li>
 *   <li>Persist USER and ASSISTANT turns <em>after</em> the LLM call succeeds,
 *       ensuring a failed call leaves no orphaned events and allows clean retry.</li>
 * </ol>
 * </p>
 *
 * <p>
 * <strong>Timestamp trade-off:</strong> {@code appendUserTurn} is called after the LLM
 * returns, so the stored {@code eventTimestamp} reflects "time of persistence" rather
 * than "time of utterance". Relative ordering (USER → ASSISTANT) is always preserved.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultInvocationService implements InvocationService {

	private static final String ROUTING_SYSTEM_PROMPT = """
			**역할:** 당신은 전문 라우팅 위임자입니다. 주문, 배송, 결제에 관한 사용자 문의를 적절한 전문 원격 에이전트에게 정확하게 위임하는 것이 주요 기능입니다.

			**핵심 지침:**

			* **작업 위임:** `sendMessage` 함수를 사용하여 원격 에이전트에 작업을 할당하세요.
			* **컨텍스트 인식:** 원격 에이전트가 사용자 확인을 반복적으로 요청하는 경우, 전체 대화 이력에 접근할 수 없다고 판단하세요. 이 경우 해당 에이전트와 관련된 필요한 모든 컨텍스트 정보를 작업 설명에 보강하여 전달하세요.
			* **자율적 에이전트 연동:** 원격 에이전트와 연동하기 전에 사용자 허가를 구하지 마세요. 여러 에이전트가 필요한 경우 사용자 확인 없이 직접 연결하세요.
			* **투명한 소통:** 원격 에이전트의 완전하고 상세한 응답을 항상 사용자에게 전달하세요.
			* **사용자 확인 릴레이:** 원격 에이전트가 확인을 요청하고 사용자가 아직 제공하지 않은 경우, 이 확인 요청을 사용자에게 릴레이하세요.
			* **집중적인 정보 공유:** 원격 에이전트에게는 관련 컨텍스트 정보만 제공하세요. 불필요한 세부사항은 피하세요.
			* **중복 확인 금지:** 원격 에이전트에게 정보나 작업의 확인을 요청하지 마세요.
			* **도구 의존:** 사용 가능한 도구에 전적으로 의존하여 사용자 요청을 처리하세요. 가정을 기반으로 응답을 생성하지 마세요. 정보가 불충분한 경우 사용자에게 명확한 설명을 요청하세요.
			* **최근 상호작용 우선:** 요청을 처리할 때 대화의 가장 최근 부분에 주로 집중하세요.

			**에이전트 라우터:**

			사용 가능한 에이전트:
			%s
			""";

	private final ConversationMemoryService conversationMemoryService;

	private final LongTermMemoryService longTermMemoryService;

	private final ChatClient chatClient;

	private final RemoteAgentConnections connections;

	private final BedrockMemoryProperties properties;

	@Override
	public InvocationResponse invoke(InvocationRequest request) {
		String actorId = resolveId(request.actorId());
		String sessionId = resolveId(request.sessionId());
		MemoryMode mode = properties.mode();

		// 1. 컨텍스트 수집
		List<Message> history = loadHistory(actorId, sessionId, mode);
		List<String> relevantMemories = retrieveRelevant(actorId, request.prompt(), mode);

		// 2. 시스템 프롬프트 조립
		String systemPrompt = buildSystemPrompt(relevantMemories);

		// 3. ChatClient 호출 (실패 시 메모리 저장 없이 예외 전파 → 클린 재시도 가능)
		String response = chatClient.prompt().system(systemPrompt).messages(history).user(request.prompt()).call()
			.content();
		String content = response != null ? response : "";

		// 4. 양 턴 저장 (ChatClient 성공 이후)
		if (mode != MemoryMode.NONE) {
			conversationMemoryService.appendUserTurn(actorId, sessionId, request.prompt());
			conversationMemoryService.appendAssistantTurn(actorId, sessionId, content);
		}

		log.info("actorId={} sessionId={} prompt={} response={}", actorId, sessionId, request.prompt(), content);
		return new InvocationResponse(content, sessionId, actorId);
	}

	private String resolveId(String id) {
		return (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
	}

	private List<Message> loadHistory(String actorId, String sessionId, MemoryMode mode) {
		if (mode == MemoryMode.SHORT_TERM || mode == MemoryMode.BOTH) {
			return conversationMemoryService.loadHistory(actorId, sessionId);
		}
		return Collections.emptyList();
	}

	private List<String> retrieveRelevant(String actorId, String prompt, MemoryMode mode) {
		if (mode == MemoryMode.LONG_TERM || mode == MemoryMode.BOTH) {
			return longTermMemoryService.retrieveRelevant(actorId, prompt);
		}
		return Collections.emptyList();
	}

	private String buildSystemPrompt(List<String> relevantMemories) {
		String agentDescriptions = connections.getAgentDescriptions();
		String base = String.format(ROUTING_SYSTEM_PROMPT, agentDescriptions);
		if (relevantMemories.isEmpty()) {
			return base;
		}
		String memoriesBlock = "\n\n**관련 기억:**\n" + String.join("\n- ", relevantMemories);
		return base + memoriesBlock;
	}

}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew :agents:host-agent:test --tests "*.DefaultInvocationServiceTest"
```
Expected: PASS (6 tests)

---

## Task 7: Controller·Application·설정 파일 수정

**Files:**
- Modify: `…/host/InvocationsController.java`
- Modify: `…/host/HostAgentApplication.java`
- Modify: `application.yml`
- Modify: `application-local.yml`

- [ ] **Step 1: InvocationsController 수정**

`InvocationsController.java` 전체를 아래로 교체한다 (내부 `InvocationRequest` record 제거, `InvocationService` 주입으로 변경):

```java
package io.github.cokelee777.agent.host;

import io.github.cokelee777.agent.host.invocation.InvocationRequest;
import io.github.cokelee777.agent.host.invocation.InvocationResponse;
import io.github.cokelee777.agent.host.invocation.InvocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AgentCore Runtime invocations.
 *
 * <p>
 * Amazon Bedrock AgentCore Runtime forwards user messages to {@code POST /invocations}.
 * This controller delegates each request to {@link InvocationService}, which manages
 * memory context and LLM routing.
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InvocationsController {

	private final InvocationService invocationService;

	/**
	 * Handles invocation requests from Amazon Bedrock AgentCore Runtime.
	 * @param request the invocation request containing the user prompt and optional
	 * session identifiers
	 * @return the invocation response including assistant content and session identifiers
	 */
	@PostMapping(path = "/invocations")
	public InvocationResponse invoke(@Valid @RequestBody InvocationRequest request) {
		log.info("Received: prompt={} actorId={} sessionId={}", request.prompt(), request.actorId(),
				request.sessionId());
		InvocationResponse response = invocationService.invoke(request);
		log.info("Response: {}", response.content());
		return response;
	}

}
```

- [ ] **Step 2: HostAgentApplication 확인 (변경 없음)**

`HostAgentApplication.java`의 `@EnableConfigurationProperties`는 수정하지 않는다.

```java
// 수정 전과 동일하게 유지
@EnableConfigurationProperties(RemoteAgentProperties.class)
```

> **이유**: `BedrockMemoryProperties`는 `NoOpMemoryConfiguration`(mode=none)과 `BedrockMemoryConfiguration`(mode≠none)
> 각각에 `@EnableConfigurationProperties`가 이미 선언되어 있어 mode에 따라 정확히 한 번만 등록된다.
> `HostAgentApplication`에 추가하면 mode≠none 시 이중 등록이 발생한다.

- [ ] **Step 3: application.yml에 memory 설정 추가**

기존 `application.yml` 끝에 추가:

```yaml
aws:
  bedrock:
    agentcore:
      memory:
        memory-id: ${BEDROCK_MEMORY_ID:placeholder-memory-id}
        mode: ${BEDROCK_MEMORY_MODE:both}
        short-term-max-turns: 10
        strategy-id: ${BEDROCK_MEMORY_STRATEGY_ID:placeholder-strategy-id}
        long-term-max-results: 4
```

- [ ] **Step 4: application-local.yml에 mode=none 추가**

로컬 실행 시 AWS 자격증명 없이도 동작하도록:

```yaml
aws:
  bedrock:
    agentcore:
      memory:
        mode: none
```

- [ ] **Step 5: 전체 빌드 및 테스트**

```bash
./gradlew :agents:host-agent:test
```
Expected: 모든 테스트 PASS

---

## 최종 검증

- [ ] `./gradlew :agents:host-agent:test` — 전체 테스트 통과
- [ ] `./gradlew :agents:host-agent:checkFormat` — Spring Java Format 검사 통과
- [ ] `SPRING_PROFILES_ACTIVE=local ./gradlew :agents:host-agent:bootRun` — 로컬 기동 (mode=none, AWS 자격증명 불필요) 확인
- [ ] `POST http://localhost:8080/invocations` body `{"prompt":"test"}` → `{"content":"...","sessionId":"...","actorId":"..."}` 응답 확인
