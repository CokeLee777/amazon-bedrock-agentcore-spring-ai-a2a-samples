# Memory Module Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `host-agent`의 Bedrock AgentCore 메모리 코드를 두 개의 독립 Gradle 모듈로 분리하고, Spring AI `ChatMemoryRepository` 인터페이스를 채택한다.

**Architecture:** 구현체 모듈(`spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core`)과 autoconfigure 모듈(`spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core`)을 분리한다. `host-agent`는 커스텀 메모리 추상화를 제거하고 `ChatMemoryRepository`를 직접 주입받는다. `memory-id` 미설정 시 Spring AI의 `InMemoryChatMemoryRepository`가 자동 폴백된다.

**Tech Stack:** Spring AI 1.1.3 (`ChatMemoryRepository`, `ChatMemoryAutoConfiguration`), AWS SDK `BedrockAgentCoreClient`, Spring Boot `@AutoConfiguration`, Gradle Kotlin DSL, JUnit 5 + Mockito + AssertJ, Spring Boot `ApplicationContextRunner`

**Spec:** `docs/superpowers/specs/2026-03-22-memory-module-restructure-design.md`

---

## File Map

### 새로 생성
```
memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/
  build.gradle.kts
  src/main/java/io/github/cokelee777/ai/chat/memory/repository/bedrockagentcore/
    AgentCoreEventToMessageConverter.java        ← host-agent에서 이동, 패키지 변경
    BedrockChatMemoryRepositoryProperties.java
    BedrockChatMemoryRepository.java
  src/test/java/io/github/cokelee777/ai/chat/memory/repository/bedrockagentcore/
    AgentCoreEventToMessageConverterTest.java    ← host-agent에서 이동, 패키지 변경
    BedrockChatMemoryRepositoryTest.java

auto-configurations/models/chat/memory/repository/
  spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core/
    build.gradle.kts
    src/main/java/io/github/cokelee777/ai/autoconfigure/chat/memory/repository/bedrockagentcore/
      BedrockChatMemoryAutoConfiguration.java
    src/main/resources/META-INF/spring/
      org.springframework.boot.autoconfigure.AutoConfiguration.imports
    src/test/java/io/github/cokelee777/ai/autoconfigure/chat/memory/repository/bedrockagentcore/
      BedrockChatMemoryAutoConfigurationTest.java
```

### 수정
```
settings.gradle.kts                                        ← 두 모듈 include 추가
agents/host-agent/build.gradle.kts                         ← bedrock SDK 제거, autoconfigure 모듈 추가
agents/host-agent/src/main/java/.../DefaultInvocationService.java  ← 전면 재작성
agents/host-agent/src/main/java/.../InvocationResponse.java        ← @Nullable 제거, Javadoc 수정
agents/host-agent/src/main/resources/application.yml               ← 메모리 설정 키 변경
agents/host-agent/src/test/java/.../DefaultInvocationServiceTest.java  ← 전면 재작성
CLAUDE.md                                                  ← 메모리 관련 섹션 업데이트
```

### 삭제 (host-agent)
```
memory/ShortTermMemoryService.java
memory/LongTermMemoryService.java
memory/NoOpShortTermMemoryService.java
memory/NoOpLongTermMemoryService.java
memory/MemoryMode.java
memory/ConversationSession.java
memory/package-info.java
memory/bedrock/BedrockShortTermMemoryService.java
memory/bedrock/BedrockLongTermMemoryService.java
memory/bedrock/AgentCoreEventToMessageConverter.java       ← 새 모듈로 이동
memory/bedrock/BedrockMemoryProperties.java
memory/bedrock/package-info.java
config/BedrockMemoryConfiguration.java
config/NoOpMemoryConfiguration.java
config/MemoryEnabledCondition.java
config/MemoryDisabledCondition.java
config/LongTermMemoryCondition.java
config/LongTermNotSupportedCondition.java
config/package-info.java
src/test/.../BedrockShortTermMemoryServiceTest.java
src/test/.../BedrockLongTermMemoryServiceTest.java
src/test/.../AgentCoreEventToMessageConverterTest.java     ← 새 모듈로 이동
```

---

## Task 1: Gradle 모듈 스캐폴딩

**Files:**
- Modify: `settings.gradle.kts`
- Create: `memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/build.gradle.kts`
- Create: `auto-configurations/models/chat/memory/repository/spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core/build.gradle.kts`

- [ ] **Step 1: settings.gradle.kts에 두 모듈 추가**

```kotlin
// settings.gradle.kts 기존 include 목록 하단에 추가
include("spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
    .projectDir = file("memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")

include("spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
    .projectDir = file("auto-configurations/models/chat/memory/repository/spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
```

- [ ] **Step 2: 구현체 모듈 build.gradle.kts 생성**

디렉토리 생성 후 파일 작성:
```
memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/build.gradle.kts
```

```kotlin
dependencies {
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("software.amazon.awssdk:bedrockagentcore")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
```

- [ ] **Step 3: autoconfigure 모듈 build.gradle.kts 생성**

디렉토리 생성 후 파일 작성:
```
auto-configurations/models/chat/memory/repository/spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core/build.gradle.kts
```

```kotlin
dependencies {
    implementation(project(":spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core"))
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("software.amazon.awssdk:bedrockagentcore")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew :spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core:compileJava \
          :spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core:compileJava
```
Expected: 소스 없으므로 BUILD SUCCESS (또는 "no source files")

---

## Task 2: AgentCoreEventToMessageConverter 이동

**Files:**
- Create: `memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/src/main/java/io/github/cokelee777/ai/chat/memory/repository/bedrockagentcore/AgentCoreEventToMessageConverter.java`
- Create: `memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/src/test/java/io/github/cokelee777/ai/chat/memory/repository/bedrockagentcore/AgentCoreEventToMessageConverterTest.java`

- [ ] **Step 1: 구현체 이동 (패키지 변경)**

```java
// memory/repository/.../bedrockagentcore/AgentCoreEventToMessageConverter.java
package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Converts AgentCore {@link Event} objects to Spring AI {@link Message} objects.
 *
 * <p>
 * Only {@code Conversational} payload events with {@code USER} or {@code ASSISTANT} roles
 * are converted. Events with unknown or missing payloads are silently skipped. The
 * returned list is sorted by {@code eventTimestamp} ascending.
 * </p>
 *
 * <p>
 * This class is not annotated with {@code @Component}. It is registered as a
 * {@code @Bean} inside {@link io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore.BedrockChatMemoryAutoConfiguration}.
 * </p>
 */
public class AgentCoreEventToMessageConverter {

	/**
	 * Converts a list of {@link Event} objects to Spring AI {@link Message} objects,
	 * sorted by {@code eventTimestamp} ascending.
	 * @param events the raw events from AgentCore Memory
	 * @return sorted list of messages; empty list if input is empty or all events are
	 * skipped
	 */
	public List<Message> toMessages(List<Event> events) {
		Assert.notNull(events, "events must not be null");

		return events.stream()
			.sorted(Comparator.comparing(Event::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
			.mapMulti((Event event, Consumer<Message> downstream) -> {
				Message message = toMessage(event);
				if (message != null) {
					downstream.accept(message);
				}
			})
			.toList();
	}

	/**
	 * Converts a single {@link Event} when it carries a supported conversational payload.
	 * @param event the AgentCore event
	 * @return a {@link Message}, or {@code null} when the event should be skipped
	 */
	private @Nullable Message toMessage(Event event) {
		if (event.payload() == null || event.payload().isEmpty()) {
			return null;
		}
		PayloadType payload = event.payload().getFirst();
		if (payload.conversational() == null) {
			return null;
		}
		Conversational conversational = payload.conversational();
		String text = conversational.content() != null ? conversational.content().text() : "";
		Role role = conversational.role();
		if (Role.USER.equals(role)) {
			return new UserMessage(Objects.requireNonNullElse(text, ""));
		}
		if (Role.ASSISTANT.equals(role)) {
			return new AssistantMessage(Objects.requireNonNullElse(text, ""));
		}
		return null;
	}

}
```

- [ ] **Step 2: 테스트 이동 (패키지 변경)**

```java
// memory/repository/.../bedrockagentcore/AgentCoreEventToMessageConverterTest.java
package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

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
		Event assistant = assistantEvent("reply", t2);
		Event user = userEvent("question", t1);
		List<Message> messages = converter.toMessages(List.of(assistant, user));
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void unknownPayload_isSkipped() {
		Event event = Event.builder().eventTimestamp(Instant.now()).build();
		assertThat(converter.toMessages(List.of(event))).isEmpty();
	}

	private Event userEvent(String text, Instant timestamp) {
		return buildEvent(text, Role.USER, timestamp);
	}

	private Event assistantEvent(String text, Instant timestamp) {
		return buildEvent(text, Role.ASSISTANT, timestamp);
	}

	private Event buildEvent(String text, Role role, Instant timestamp) {
		Conversational conversational = Conversational.builder().content(Content.fromText(text)).role(role).build();
		PayloadType payload = PayloadType.fromConversational(conversational);
		return Event.builder().eventTimestamp(timestamp).payload(List.of(payload)).build();
	}

}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core:test \
  --tests "io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.AgentCoreEventToMessageConverterTest"
```
Expected: 5 tests PASS

---

## Task 3: BedrockChatMemoryRepositoryProperties

**Files:**
- Create: `memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/src/main/java/io/github/cokelee777/ai/chat/memory/repository/bedrockagentcore/BedrockChatMemoryRepositoryProperties.java`

- [ ] **Step 1: Properties 레코드 생성**

```java
// memory/repository/.../bedrockagentcore/BedrockChatMemoryRepositoryProperties.java
package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Amazon Bedrock AgentCore Chat Memory Repository.
 *
 * <p>
 * Bound from the {@code spring.ai.chat.memory.repository.bedrock.agent-core} prefix.
 * Override with environment variables (e.g., {@code BEDROCK_MEMORY_ID}).
 * </p>
 *
 * @param memoryId the Memory resource ID or ARN; required when this autoconfigure is
 * active
 * @param maxTurns max conversation turns to load from short-term memory; defaults to 10
 */
@Validated
@ConfigurationProperties(prefix = BedrockChatMemoryRepositoryProperties.CONFIG_PREFIX)
public record BedrockChatMemoryRepositoryProperties(@Nullable String memoryId,
		@DefaultValue("10") @Min(1) int maxTurns) {

	/** Configuration properties prefix. */
	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.bedrock.agent-core";

}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew :spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core:compileJava
```
Expected: BUILD SUCCESS

---

## Task 4: BedrockChatMemoryRepository

**Files:**
- Create: `memory/repository/.../bedrockagentcore/BedrockChatMemoryRepository.java`
- Create: `memory/repository/.../bedrockagentcore/BedrockChatMemoryRepositoryTest.java`

- [ ] **Step 1: 테스트 작성**

```java
// memory/repository/.../bedrockagentcore/BedrockChatMemoryRepositoryTest.java
package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

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
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockChatMemoryRepositoryTest {

	private static final String MEMORY_ID = "mem-test";

	private static final String ACTOR_ID = "actor-1";

	private static final String SESSION_ID = "sess-1";

	private static final String CONVERSATION_ID = ACTOR_ID + ":" + SESSION_ID;

	@Mock
	private BedrockAgentCoreClient client;

	private BedrockChatMemoryRepository repository;

	@BeforeEach
	void setUp() {
		BedrockChatMemoryRepositoryProperties properties = new BedrockChatMemoryRepositoryProperties(MEMORY_ID, 10);
		AgentCoreEventToMessageConverter converter = new AgentCoreEventToMessageConverter();
		repository = new BedrockChatMemoryRepository(client, properties, converter);
	}

	@Test
	void findByConversationId_parsesCompositeKeyAndCallsListEvents() {
		when(client.listEvents(any(ListEventsRequest.class)))
			.thenReturn(ListEventsResponse.builder().events(List.of()).build());

		repository.findByConversationId(CONVERSATION_ID);

		ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(client).listEvents(captor.capture());
		ListEventsRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo(MEMORY_ID);
		assertThat(req.actorId()).isEqualTo(ACTOR_ID);
		assertThat(req.sessionId()).isEqualTo(SESSION_ID);
	}

	@Test
	void findByConversationId_maxResultsIsMaxTurnsTimesTwo() {
		when(client.listEvents(any(ListEventsRequest.class)))
			.thenReturn(ListEventsResponse.builder().events(List.of()).build());

		repository.findByConversationId(CONVERSATION_ID);

		ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(client).listEvents(captor.capture());
		assertThat(captor.getValue().maxResults()).isEqualTo(20); // 10 * 2
	}

	@Test
	void findByConversationId_returnsSortedMessages() {
		Instant t1 = Instant.ofEpochSecond(1000);
		Instant t2 = Instant.ofEpochSecond(2000);
		Event userEvent = buildEvent("hi", Role.USER, t1);
		Event assistantEvent = buildEvent("hello", Role.ASSISTANT, t2);
		// 역순으로 반환해도 오름차순 정렬되어야 한다
		when(client.listEvents(any(ListEventsRequest.class)))
			.thenReturn(ListEventsResponse.builder().events(List.of(assistantEvent, userEvent)).build());

		List<Message> messages = repository.findByConversationId(CONVERSATION_ID);

		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void saveAll_callsCreateEventForEachMessage() {
		List<Message> messages = List.of(new UserMessage("hello"), new AssistantMessage("hi"));

		repository.saveAll(CONVERSATION_ID, messages);

		verify(client, times(2)).createEvent(any(CreateEventRequest.class));
	}

	@Test
	void saveAll_userMessage_setsRoleUser() {
		repository.saveAll(CONVERSATION_ID, List.of(new UserMessage("test")));

		ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(client).createEvent(captor.capture());
		CreateEventRequest req = captor.getValue();
		assertThat(req.memoryId()).isEqualTo(MEMORY_ID);
		assertThat(req.actorId()).isEqualTo(ACTOR_ID);
		assertThat(req.sessionId()).isEqualTo(SESSION_ID);
		assertThat(req.payload().getFirst().conversational().role()).isEqualTo(Role.USER);
	}

	@Test
	void saveAll_emptyList_noApiCalls() {
		repository.saveAll(CONVERSATION_ID, List.of());

		verify(client, never()).createEvent(any());
	}

	@Test
	void deleteByConversationId_noApiCalls() {
		repository.deleteByConversationId(CONVERSATION_ID);

		verify(client, never()).createEvent(any());
		verify(client, never()).listEvents(any());
	}

	@Test
	void findConversationIds_returnsEmpty() {
		assertThat(repository.findConversationIds()).isEmpty();
	}

	private Event buildEvent(String text, Role role, Instant timestamp) {
		Conversational conv = Conversational.builder().content(Content.fromText(text)).role(role).build();
		return Event.builder().eventTimestamp(timestamp).payload(List.of(PayloadType.fromConversational(conv))).build();
	}

}
```

- [ ] **Step 2: 스텁 클래스 생성 (컴파일용)**

```java
// memory/repository/.../bedrockagentcore/BedrockChatMemoryRepository.java
package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

import java.util.List;

/** Amazon Bedrock AgentCore implementation of {@link ChatMemoryRepository}. */
public class BedrockChatMemoryRepository implements ChatMemoryRepository {

	public BedrockChatMemoryRepository(BedrockAgentCoreClient client,
			BedrockChatMemoryRepositoryProperties properties, AgentCoreEventToMessageConverter converter) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public List<String> findConversationIds() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		throw new UnsupportedOperationException();
	}

}
```

- [ ] **Step 3: 테스트 실행 (실패 확인)**

```bash
./gradlew :spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core:test \
  --tests "*.BedrockChatMemoryRepositoryTest"
```
Expected: FAIL (UnsupportedOperationException)

- [ ] **Step 4: 구현체 작성**

```java
// memory/repository/.../bedrockagentcore/BedrockChatMemoryRepository.java
package io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Amazon Bedrock AgentCore implementation of {@link ChatMemoryRepository}.
 *
 * <p>
 * Uses {@code listEvents} to load conversation history and {@code createEvent} to append
 * new messages (append-only, same semantics as
 * {@code JdbcChatMemoryRepository.saveAll}).
 * </p>
 *
 * <p>
 * {@code conversationId} format: {@code "actorId:sessionId"}.
 * </p>
 *
 * <p>
 * Pagination is not handled: only the most recent {@code maxTurns * 2} events are loaded.
 * {@code deleteByConversationId} is a no-op because Bedrock AgentCore does not expose an
 * event-deletion API.
 * </p>
 */
@Slf4j
public class BedrockChatMemoryRepository implements ChatMemoryRepository {

	private static final String DELIMITER = ":";

	private final BedrockAgentCoreClient client;

	private final BedrockChatMemoryRepositoryProperties properties;

	private final AgentCoreEventToMessageConverter converter;

	/**
	 * Creates a new repository, asserting that {@code memoryId} is present.
	 * @param client the Bedrock AgentCore data-plane client
	 * @param properties the memory repository properties
	 * @param converter converts AgentCore events to Spring AI messages
	 */
	public BedrockChatMemoryRepository(BedrockAgentCoreClient client,
			BedrockChatMemoryRepositoryProperties properties, AgentCoreEventToMessageConverter converter) {
		Assert.hasText(properties.memoryId(),
				BedrockChatMemoryRepositoryProperties.CONFIG_PREFIX + ".memory-id must be set");
		this.client = client;
		this.properties = properties;
		this.converter = converter;
	}

	/**
	 * Not supported by Bedrock AgentCore API.
	 * @return empty list
	 */
	@Override
	public List<String> findConversationIds() {
		return List.of();
	}

	/**
	 * Loads conversation history for the given {@code conversationId}.
	 * @param conversationId in the format {@code "actorId:sessionId"}
	 * @return messages sorted by eventTimestamp ascending (oldest first)
	 */
	@Override
	public List<Message> findByConversationId(String conversationId) {
		String[] parts = parseConversationId(conversationId);
		try {
			ListEventsRequest request = ListEventsRequest.builder()
				.memoryId(properties.memoryId())
				.actorId(parts[0])
				.sessionId(parts[1])
				.includePayloads(true)
				.maxResults(properties.maxTurns() * 2)
				.build();
			ListEventsResponse response = client.listEvents(request);
			List<Event> events = Objects.requireNonNullElse(response.events(), Collections.emptyList());
			return converter.toMessages(events);
		}
		catch (Exception ex) {
			log.error("Failed to load history for conversationId={}", conversationId, ex);
			throw ex;
		}
	}

	/**
	 * Appends each message as a new event in Bedrock AgentCore Memory (append-only,
	 * consistent with {@code JdbcChatMemoryRepository} semantics).
	 * @param conversationId in the format {@code "actorId:sessionId"}
	 * @param messages the new messages to append; ignored if empty
	 */
	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		if (messages.isEmpty()) {
			return;
		}
		String[] parts = parseConversationId(conversationId);
		for (Message message : messages) {
			try {
				Role role = (message instanceof UserMessage) ? Role.USER : Role.ASSISTANT;
				Conversational conversational = Conversational.builder()
					.content(Content.fromText(message.getText()))
					.role(role)
					.build();
				CreateEventRequest request = CreateEventRequest.builder()
					.memoryId(properties.memoryId())
					.actorId(parts[0])
					.sessionId(parts[1])
					.eventTimestamp(Instant.now())
					.payload(PayloadType.fromConversational(conversational))
					.build();
				client.createEvent(request);
			}
			catch (Exception ex) {
				log.error("Failed to save message for conversationId={}", conversationId, ex);
				throw ex;
			}
		}
	}

	/**
	 * No-op: Bedrock AgentCore does not support event deletion.
	 * @param conversationId the conversation identifier (logged only)
	 */
	@Override
	public void deleteByConversationId(String conversationId) {
		log.warn("deleteByConversationId is not supported by Bedrock AgentCore Memory API. conversationId={}",
				conversationId);
	}

	private String[] parseConversationId(String conversationId) {
		String[] parts = conversationId.split(DELIMITER, 2);
		Assert.isTrue(parts.length == 2,
				"conversationId must be in format 'actorId:sessionId', got: " + conversationId);
		return parts;
	}

}
```

- [ ] **Step 5: 테스트 실행 (통과 확인)**

```bash
./gradlew :spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core:test
```
Expected: 모든 테스트 PASS

---

## Task 5: BedrockChatMemoryAutoConfiguration

**Files:**
- Create: `auto-configurations/.../bedrockagentcore/BedrockChatMemoryAutoConfiguration.java`
- Create: `auto-configurations/.../resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `auto-configurations/.../bedrockagentcore/BedrockChatMemoryAutoConfigurationTest.java`

- [ ] **Step 1: AutoConfiguration 클래스 생성**

```java
// auto-configurations/.../bedrockagentcore/BedrockChatMemoryAutoConfiguration.java
package io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore;

import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.AgentCoreEventToMessageConverter;
import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepository;
import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepositoryProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;

/**
 * Auto-configuration for Amazon Bedrock AgentCore {@link BedrockChatMemoryRepository}.
 *
 * <p>
 * Activates when:
 * <ol>
 * <li>{@link BedrockChatMemoryRepository} is on the classpath.</li>
 * <li>{@code spring.ai.chat.memory.repository.bedrock.agent-core.memory-id} is
 * set.</li>
 * </ol>
 * When not active, Spring AI's {@link ChatMemoryAutoConfiguration} registers
 * {@code InMemoryChatMemoryRepository} as the fallback via {@code @ConditionalOnMissingBean}.
 * </p>
 */
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass(BedrockChatMemoryRepository.class)
@ConditionalOnProperty(prefix = BedrockChatMemoryRepositoryProperties.CONFIG_PREFIX, name = "memory-id")
@EnableConfigurationProperties(BedrockChatMemoryRepositoryProperties.class)
public class BedrockChatMemoryAutoConfiguration {

	/**
	 * Creates the Bedrock AgentCore data-plane client using the configured AWS region.
	 * @param region the AWS region value from {@code spring.ai.bedrock.aws.region}
	 * @return the client
	 */
	@Bean
	public BedrockAgentCoreClient bedrockAgentCoreClient(
			@Value("${spring.ai.bedrock.aws.region}") String region) {
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
	 * Creates the {@link BedrockChatMemoryRepository}.
	 * @param client the Bedrock client
	 * @param properties the memory repository properties
	 * @param converter the event-to-message converter
	 * @return the repository
	 */
	@Bean
	public BedrockChatMemoryRepository chatMemoryRepository(BedrockAgentCoreClient client,
			BedrockChatMemoryRepositoryProperties properties, AgentCoreEventToMessageConverter converter) {
		return new BedrockChatMemoryRepository(client, properties, converter);
	}

}
```

- [ ] **Step 2: AutoConfiguration imports 파일 생성**

```
# auto-configurations/.../resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore.BedrockChatMemoryAutoConfiguration
```

- [ ] **Step 3: 테스트 작성**

```java
// auto-configurations/.../bedrockagentcore/BedrockChatMemoryAutoConfigurationTest.java
package io.github.cokelee777.ai.autoconfigure.chat.memory.repository.bedrockagentcore;

import io.github.cokelee777.ai.chat.memory.repository.bedrockagentcore.BedrockChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BedrockChatMemoryAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(BedrockChatMemoryAutoConfiguration.class, ChatMemoryAutoConfiguration.class));

	@Test
	void withMemoryId_registersBedrockRepository() {
		contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory-id=mem-123",
					"spring.ai.bedrock.aws.region=us-east-1")
			.withBean(BedrockAgentCoreClient.class, () -> mock(BedrockAgentCoreClient.class))
			.run(ctx -> assertThat(ctx).hasSingleBean(BedrockChatMemoryRepository.class));
	}

	@Test
	void withoutMemoryId_registersInMemoryRepository() {
		contextRunner.run(ctx -> {
			assertThat(ctx).doesNotHaveBean(BedrockChatMemoryRepository.class);
			assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
			assertThat(ctx.getBean(ChatMemoryRepository.class)).isInstanceOf(InMemoryChatMemoryRepository.class);
		});
	}

	@Test
	void withMemoryId_bedrockRepositoryTakesPrecedenceOverInMemory() {
		contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory-id=mem-123",
					"spring.ai.bedrock.aws.region=us-east-1")
			.withBean(BedrockAgentCoreClient.class, () -> mock(BedrockAgentCoreClient.class))
			.run(ctx -> {
				assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
				assertThat(ctx.getBean(ChatMemoryRepository.class)).isInstanceOf(BedrockChatMemoryRepository.class);
			});
	}

}
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew :spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core:test
```
Expected: 3 tests PASS

---

## Task 6: DefaultInvocationService 리팩터링

**Files:**
- Modify: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/invocation/DefaultInvocationService.java`
- Modify: `agents/host-agent/src/test/java/io/github/cokelee777/agent/host/invocation/DefaultInvocationServiceTest.java`

- [ ] **Step 1: DefaultInvocationServiceTest 전면 재작성**

```java
// agents/host-agent/src/test/java/io/github/cokelee777/agent/host/invocation/DefaultInvocationServiceTest.java
package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.RemoteAgentConnections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultInvocationServiceTest {

	@Mock
	private ChatMemoryRepository chatMemoryRepository;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec callSpec;

	@Mock
	private RemoteAgentConnections connections;

	@Test
	void invoke_loadsHistoryAndSavesNewMessagesAfterLlmCall() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of(new UserMessage("prev")));
		setupChatClientChain("ok");
		when(connections.getAgentDescriptions()).thenReturn("");

		service().invoke(new InvocationRequest("hi", "actor-1", "session-1"));

		InOrder order = inOrder(chatClient, chatMemoryRepository);
		order.verify(chatClient).prompt();
		ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
		order.verify(chatMemoryRepository).saveAll(eq("actor-1:session-1"), captor.capture());
		List<?> saved = captor.getValue();
		assertThat(saved).hasSize(2);
		assertThat(saved.get(0)).isInstanceOf(UserMessage.class);
		assertThat(saved.get(1)).isInstanceOf(AssistantMessage.class);
	}

	@Test
	void invoke_savesOnlyTwoNewMessages() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		service().invoke(new InvocationRequest("hello", "a", "s"));

		ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
		verify(chatMemoryRepository).saveAll(anyString(), captor.capture());
		assertThat(captor.getValue()).hasSize(2);
	}

	@Test
	void invoke_nullActorId_generatesUuid() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("hi");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service().invoke(new InvocationRequest("hello", null, null));

		assertThat(response.actorId()).isNotBlank();
		assertThat(response.sessionId()).isNotBlank();
	}

	@Test
	void invoke_providedIds_returnsSameIds() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("reply");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service().invoke(new InvocationRequest("hi", "actor-1", "sess-42"));

		assertThat(response.actorId()).isEqualTo("actor-1");
		assertThat(response.sessionId()).isEqualTo("sess-42");
	}

	@Test
	void invoke_alwaysReturnsNonNullIds() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		setupChatClientChain("hi");
		when(connections.getAgentDescriptions()).thenReturn("");

		InvocationResponse response = service().invoke(new InvocationRequest("hello", null, null));

		assertThat(response.sessionId()).isNotNull();
		assertThat(response.actorId()).isNotNull();
	}

	@Test
	void invoke_llmFailure_noMemorySaved() {
		when(chatMemoryRepository.findByConversationId(anyString())).thenReturn(List.of());
		when(connections.getAgentDescriptions()).thenReturn("");
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(anyList())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenThrow(new RuntimeException("LLM error"));

		assertThatThrownBy(() -> service().invoke(new InvocationRequest("hi", "actor-1", "session-1")))
			.isInstanceOf(RuntimeException.class);

		verify(chatMemoryRepository, never()).saveAll(anyString(), any());
	}

	private DefaultInvocationService service() {
		return new DefaultInvocationService(chatClient, connections, chatMemoryRepository);
	}

	private void setupChatClientChain(String content) {
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.messages(anyList())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callSpec);
		when(callSpec.content()).thenReturn(content);
	}

}
```

- [ ] **Step 2: DefaultInvocationService 재작성**

```java
// agents/host-agent/src/main/java/io/github/cokelee777/agent/host/invocation/DefaultInvocationService.java
package io.github.cokelee777.agent.host.invocation;

import io.github.cokelee777.agent.host.RemoteAgentConnections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link InvocationService}.
 *
 * <p>
 * Execution order per request:
 * <ol>
 * <li>Resolve {@code actorId}/{@code sessionId} (generate UUID if absent).</li>
 * <li>Compose {@code conversationId} as {@code "actorId:sessionId"}.</li>
 * <li>Load history from {@link ChatMemoryRepository}.</li>
 * <li>Call the LLM via {@link ChatClient}.</li>
 * <li>Persist the new USER and ASSISTANT messages <em>after</em> the LLM call succeeds,
 * ensuring a failed call leaves no orphaned events.</li>
 * </ol>
 * </p>
 *
 * <p>
 * <strong>saveAll semantics:</strong> only the two new messages are passed to
 * {@link ChatMemoryRepository#saveAll}, consistent with append-based repositories such as
 * {@code JdbcChatMemoryRepository}.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultInvocationService implements InvocationService {

	private static final String ROUTING_SYSTEM_PROMPT = """
			**역할:** 당신은 전문 라우팅 위임자입니다. 주문, 배송, 결제에 관한 사용자 문의를 적절한 전문 원격 에이전트에게 정확하게 위임하는 것이 주요 기능입니다.

			**핵심 지침:**

			* **응답 형식:** 내부 추론 과정(<thinking> 등)을 응답에 절대 포함하지 마세요. 최종 답변만 출력하세요.
			* **작업 위임:** `sendMessage` 함수를 사용하여 원격 에이전트에 작업을 할당하세요.
			* **컨텍스트 인식:** 원격 에이전트가 사용자 확인을 반복적으로 요청하는 경우, 전체 대화 이력에 접근할 수 없다고 판단하세요. 이 경우 해당 에이전트와 관련된 필요한 모든 컨텍스트 정보를 작업 설명에 보강하여 전달하세요.
			* **자율적 에이전트 연동:** 원격 에이전트와 연동하기 전에 사용자 허가를 구하지 마세요. 여러 에이전트가 필요한 경우 사용자 확인 없이 직접 연결하세요.
			* **투명한 소통:** 원격 에이전트의 완전하고 상세한 응답을 항상 사용자에게 전달하세요.
			* **응답 언어:** 사용자가 사용한 언어로 항상 응답하세요. 한국어 질문에는 반드시 한국어로 답변하세요.
			* **사용자 확인 릴레이:** 원격 에이전트가 확인을 요청하고 사용자가 아직 제공하지 않은 경우, 이 확인 요청을 사용자에게 릴레이하세요.
			* **집중적인 정보 공유:** 원격 에이전트에게는 관련 컨텍스트 정보만 제공하세요. 불필요한 세부사항은 피하세요.
			* **중복 확인 금지:** 원격 에이전트에게 정보나 작업의 확인을 요청하지 마세요.
			* **도구 의존:** 사용 가능한 도구에 전적으로 의존하여 사용자 요청을 처리하세요. 가정을 기반으로 응답을 생성하지 마세요. 정보가 불충분한 경우 사용자에게 명확한 설명을 요청하세요.
			* **최근 상호작용 우선:** 요청을 처리할 때 대화의 가장 최근 부분에 주로 집중하세요.

			**에이전트 라우터:**

			사용 가능한 에이전트:
			%s
			""";

	private final ChatClient chatClient;

	private final RemoteAgentConnections connections;

	private final ChatMemoryRepository chatMemoryRepository;

	@Override
	public InvocationResponse invoke(InvocationRequest request) {
		String actorId = resolveId(request.actorId());
		String sessionId = resolveId(request.sessionId());
		String conversationId = actorId + ":" + sessionId;
		String prompt = request.prompt();

		List<Message> history = chatMemoryRepository.findByConversationId(conversationId);

		String response = chatClient.prompt()
			.system(ROUTING_SYSTEM_PROMPT.formatted(connections.getAgentDescriptions()))
			.messages(history)
			.user(prompt)
			.call()
			.content();
		String content = Objects.requireNonNullElse(response, "");

		chatMemoryRepository.saveAll(conversationId, List.of(new UserMessage(prompt), new AssistantMessage(content)));

		log.info("session={} prompt={} response={}", conversationId, prompt, content);
		return new InvocationResponse(content, sessionId, actorId);
	}

	private String resolveId(@Nullable String id) {
		return Objects.requireNonNullElse(id, UUID.randomUUID().toString());
	}

}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :host-agent:test --tests "*.DefaultInvocationServiceTest"
```
Expected: 6 tests PASS

---

## Task 7: InvocationResponse 업데이트 및 host-agent build.gradle.kts 변경

**Files:**
- Modify: `agents/host-agent/src/main/java/io/github/cokelee777/agent/host/invocation/InvocationResponse.java`
- Modify: `agents/host-agent/build.gradle.kts`

- [ ] **Step 1: InvocationResponse @Nullable 제거 및 Javadoc 수정**

```java
// agents/host-agent/src/main/java/.../invocation/InvocationResponse.java
package io.github.cokelee777.agent.host.invocation;

import org.springframework.util.Assert;

/**
 * Response payload for {@code POST /invocations}.
 *
 * <p>
 * {@code sessionId} and {@code actorId} are always non-null. Clients must persist these
 * values to continue the conversation in subsequent requests.
 * </p>
 *
 * @param content the assistant response text
 * @param sessionId the session identifier used for this invocation
 * @param actorId the actor identifier used for this invocation
 */
public record InvocationResponse(String content, String sessionId, String actorId) {

	public InvocationResponse {
		Assert.notNull(content, "content must not be null");
	}

}
```

- [ ] **Step 2: host-agent build.gradle.kts 변경**

```kotlin
// agents/host-agent/build.gradle.kts
dependencies {
    implementation(project(":agent-common"))
    implementation(project(":spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse")
    implementation("org.springframework.ai:spring-ai-client-chat")
}
```

(`bedrockagentcore`, `bedrockagentcorecontrol` 제거. autoconfigure 모듈 추가.)

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew :host-agent:compileJava
```
Expected: BUILD SUCCESS (삭제 대상 파일들의 import 오류는 다음 Task에서 해결)

---

## Task 8: host-agent 파일 삭제 및 application.yml 업데이트

**Files:**
- Delete: 여러 파일 (아래 목록)
- Modify: `agents/host-agent/src/main/resources/application.yml`

- [ ] **Step 1: 삭제 대상 파일 제거**

아래 파일들을 삭제한다:

```
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/ShortTermMemoryService.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/LongTermMemoryService.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/NoOpShortTermMemoryService.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/NoOpLongTermMemoryService.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/MemoryMode.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/ConversationSession.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/package-info.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/bedrock/BedrockShortTermMemoryService.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/bedrock/BedrockLongTermMemoryService.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/bedrock/AgentCoreEventToMessageConverter.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/bedrock/BedrockMemoryProperties.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/memory/bedrock/package-info.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/config/BedrockMemoryConfiguration.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/config/NoOpMemoryConfiguration.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/config/MemoryEnabledCondition.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/config/MemoryDisabledCondition.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/config/LongTermMemoryCondition.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/config/LongTermNotSupportedCondition.java
agents/host-agent/src/main/java/io/github/cokelee777/agent/host/config/package-info.java
agents/host-agent/src/test/java/io/github/cokelee777/agent/host/memory/bedrock/BedrockShortTermMemoryServiceTest.java
agents/host-agent/src/test/java/io/github/cokelee777/agent/host/memory/bedrock/BedrockLongTermMemoryServiceTest.java
agents/host-agent/src/test/java/io/github/cokelee777/agent/host/memory/bedrock/AgentCoreEventToMessageConverterTest.java
```

- [ ] **Step 2: application.yml 메모리 설정 키 변경**

```yaml
# agents/host-agent/src/main/resources/application.yml
server:
  port: 8080

spring:
  ai:
    bedrock:
      aws:
        region: ${BEDROCK_REGION:ap-northeast-2}
      converse:
        chat:
          options:
            model: ${BEDROCK_MODEL_ID:}
    chat:
      memory:
        repository:
          bedrock:
            agent-core:
              memory-id: ${BEDROCK_MEMORY_ID:}
              max-turns: 10

remote:
  agents:
    order-agent:
      url: ${ORDER_AGENT_URL:}
    delivery-agent:
      url: ${DELIVERY_AGENT_URL:}
    payment-agent:
      url: ${PAYMENT_AGENT_URL:}
```

(`aws.bedrock.agent-core.memory.*` 전체 제거, `spring.ai.chat.memory.repository.bedrock.agent-core.*` 추가)

- [ ] **Step 3: 전체 빌드 확인**

```bash
./gradlew build
```
Expected: BUILD SUCCESS, 모든 테스트 PASS

---

## Task 9: CLAUDE.md 업데이트

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: CLAUDE.md 모듈 구조 섹션 업데이트**

`CLAUDE.md`의 모듈 구조 트리에서 아래를 반영한다:

1. `host-agent` 하위에서 제거:
   - `config/BedrockMemoryConfiguration`, `config/NoOpMemoryConfiguration`, `*Condition` 클래스들
   - `memory/bedrock/BedrockConversationMemoryService`, `memory/bedrock/BedrockLongTermMemoryService`
   - `memory/ShortTermMemoryService`, `memory/LongTermMemoryService`, `memory/MemoryMode`

2. 새 모듈 추가:
   ```
   ├── memory/repository/
   │   └── spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/
   │       ├── BedrockChatMemoryRepository     # ChatMemoryRepository 구현체 (append)
   │       ├── BedrockChatMemoryRepositoryProperties  # CONFIG_PREFIX = spring.ai.chat.memory.repository.bedrock.agent-core
   │       └── AgentCoreEventToMessageConverter
   └── auto-configurations/models/chat/memory/repository/
       └── spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core/
           └── BedrockChatMemoryAutoConfiguration  # @AutoConfiguration(before=ChatMemoryAutoConfiguration)
   ```

3. 핵심 설계 결정 사항에서 기존 `BedrockMemoryProperties`, `MemoryMode`, `NoOpMemoryConfiguration` 섹션을 제거하고 새 설계로 교체.

4. `springAiVersion` 1.1.2 → 1.1.3으로 수정.

- [ ] **Step 2: 최종 빌드 확인**

```bash
./gradlew build
```
Expected: BUILD SUCCESS
