# Memory Module Restructure Design

**Date:** 2026-03-22
**Status:** Approved

## Overview

`host-agent` 내부에 있던 Bedrock AgentCore 메모리 관련 코드를 독립 Gradle 모듈 두 개로 분리한다.
Spring AI의 `ChatMemoryRepository` 인터페이스를 채택하고, 장기 메모리 패턴을 제거한다.
구현체는 `memory/repository/`, autoconfigure는 `auto-configurations/models/chat/memory/repository/` 하위에 Spring AI 네이밍 컨벤션을 따라 관리한다.
로컬 개발 시에는 Spring AI가 자동 제공하는 `InMemoryChatMemoryRepository`를 그대로 사용한다.

---

## Decisions

| 결정 | 내용 |
|------|------|
| 단기 메모리 인터페이스 | 커스텀 `ShortTermMemoryService` 제거 → Spring AI `ChatMemoryRepository` 채택 |
| 장기 메모리 | `LongTermMemoryService` 및 관련 구현체 제거. 추후 Spring AI `VectorStore` 기반 RAG로 대체 |
| NoOp 대체 | `NoOpShortTermMemoryService` 제거 → Spring AI의 `InMemoryChatMemoryRepository` 직접 사용 |
| 로컬 dev 폴백 | 별도 in-memory 모듈 불필요. Spring AI `ChatMemoryAutoConfiguration`이 `@ConditionalOnMissingBean`으로 자동 등록 |
| `MemoryMode` | `@ConditionalOnMissingBean` 패턴 채택으로 완전 제거 |
| 조건 처리 | 커스텀 `*Condition` 클래스 제거 → `@ConditionalOnProperty` + Spring AI autoconfigure 패턴으로 대체 |
| `actorId` 처리 | `ChatMemoryRepository`는 `conversationId`(String) 하나만 받으므로 `actorId:sessionId` 복합 키로 구성. `InvocationRequest`/`InvocationResponse`의 `actorId`, `sessionId` 필드는 유지 |
| `saveAll` 의미론 | JDBC 등 다른 구현체와 동일하게 **append(INSERT)**. `DefaultInvocationService`는 새 메시지 2개만 전달 |
| InMemory 로컬 한계 | `InMemoryChatMemoryRepository.saveAll` = 전체 교체이므로 로컬 dev에서 멀티턴 히스토리 유지 안됨. 허용된 한계. |
| `actorId`/`sessionId` 반환 | `MemoryMode` 제거로 null 반환 조건도 제거. 항상 resolved 값(UUID 포함) 반환 |
| `deleteByConversationId` | Bedrock API는 이벤트 삭제를 지원하지 않으므로 no-op + 경고 로그로 구현 |
| 모듈 분리 | 구현체와 autoconfigure를 별도 모듈로 분리. Spring AI `memory/repository/` + `auto-configurations/` 디렉토리 구조 준수 |

---

## New Module Structure

```
memory/
└── repository/
    └── spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core/
        ├── BedrockChatMemoryRepository               ← implements ChatMemoryRepository (append)
        ├── BedrockChatMemoryRepositoryProperties     ← @ConfigurationProperties
        └── AgentCoreEventToMessageConverter          ← Event → Message 변환 (host-agent에서 이동)

auto-configurations/
└── models/
    └── chat/
        └── memory/
            └── repository/
                └── spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core/
                    ├── BedrockChatMemoryAutoConfiguration   ← @AutoConfiguration
                    └── META-INF/spring/
                        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
                            ← BedrockChatMemoryAutoConfiguration FQCN 등록 필수
```

autoconfigure 모듈이 구현체 모듈에 의존한다:
```kotlin
// spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core/build.gradle.kts
dependencies {
    implementation(project(":spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core"))
}
```

---

### spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core (구현체)

`BedrockChatMemoryRepository`가 `ChatMemoryRepository`를 구현한다.
Spring AI의 JDBC 구현체와 동일하게 `saveAll` = append(INSERT) 의미론을 따른다.

```java
// BedrockChatMemoryRepository
// conversationId 형식: "actorId:sessionId"
public class BedrockChatMemoryRepository implements ChatMemoryRepository {

    // 생성자에서 Assert.hasText(properties.memoryId()) 검증

    // findByConversationId(conversationId)
    //   → "actorId:sessionId" 파싱 (split ":", limit 2)
    //   → listEvents(memoryId, actorId, sessionId, maxResults = maxTurns * 2)
    //   → AgentCoreEventToMessageConverter로 Event → Message 변환
    //   → eventTimestamp 기준 오름차순 정렬 (oldest first) — BedrockChatMemoryRepository 책임
    //   → 페이지네이션 미처리: maxResults 내에서만 조회 (listEvents nextToken 무시)

    // saveAll(conversationId, messages)
    //   → "actorId:sessionId" 파싱
    //   → messages가 비어있으면 즉시 반환 (API 호출 없음)
    //   → 각 Message에 대해 createEvent() 호출 (append, JDBC와 동일 의미론)

    // deleteByConversationId(conversationId)
    //   → Bedrock API 미지원: no-op + WARN 로그 출력
}

// BedrockChatMemoryRepositoryProperties
@ConfigurationProperties(prefix = BedrockChatMemoryRepositoryProperties.CONFIG_PREFIX)
public record BedrockChatMemoryRepositoryProperties(
    @Nullable String memoryId,                   // @ConditionalOnProperty로 게이팅, 생성자에서 Assert.hasText
    @DefaultValue("10") @Min(1) int maxTurns     // 기존 shortTermMaxTurns
) {
    public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.bedrock.agent-core";
}
```

---

### spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core (autoconfigure)

`memory-id` 프로퍼티가 설정된 경우에만 활성화된다.
미설정 시 Spring AI의 `ChatMemoryAutoConfiguration`이 `@ConditionalOnMissingBean`으로 `InMemoryChatMemoryRepository`를 자동 등록한다.

```java
// BedrockChatMemoryAutoConfiguration
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass(BedrockChatMemoryRepository.class)
@ConditionalOnProperty(prefix = BedrockChatMemoryRepositoryProperties.CONFIG_PREFIX, name = "memory-id")
@EnableConfigurationProperties(BedrockChatMemoryRepositoryProperties.class)
public class BedrockChatMemoryAutoConfiguration {

    // BedrockAgentCoreClient: ${spring.ai.bedrock.aws.region} @Value로 region 읽어서 생성
    //   (기존 BedrockMemoryConfiguration과 동일 패턴)
    @Bean
    public BedrockAgentCoreClient bedrockAgentCoreClient(
            @Value("${spring.ai.bedrock.aws.region}") String region) { ... }

    @Bean
    public AgentCoreEventToMessageConverter agentCoreEventToMessageConverter() { ... }

    @Bean
    public BedrockChatMemoryRepository chatMemoryRepository(
            BedrockAgentCoreClient client,
            BedrockChatMemoryRepositoryProperties properties,
            AgentCoreEventToMessageConverter converter) { ... }
}

// 조건 구조 설명:
// @AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
//   → Bedrock 빈이 먼저 등록되어 ChatMemoryAutoConfiguration의
//     @ConditionalOnMissingBean이 InMemory 등록을 건너뜀
// @ConditionalOnClass(BedrockChatMemoryRepository.class)
//   → 구현체 모듈이 classpath에 있을 때만 활성화.
//     나중에 다른 전략 모듈 추가 시 각자의 구현체 클래스로 조건 분리 가능
// @ConditionalOnProperty(name = "memory-id")
//   → memory-id 미설정 시 클래스 자체가 비활성화 → InMemory 폴백
//     memory-id 설정 시 Bedrock 빈 등록
```

---

## saveAll 의미론 비교

| 구현체 | saveAll 동작 | DefaultInvocationService 전달값 |
|--------|-------------|-------------------------------|
| `InMemoryChatMemoryRepository` | **전체 교체** (HashMap put) | 새 메시지 2개 → 세션 히스토리 유지 안됨 (로컬 dev 한계, 허용) |
| `JdbcChatMemoryRepository` | **INSERT (append)** | 새 메시지 2개 → 누적 |
| `BedrockChatMemoryRepository` | **INSERT (append)** | 새 메시지 2개 → `createEvent()` 2회 호출 |

---

## host-agent Changes

### 제거 대상

| 파일 | 이유 |
|------|------|
| `memory/ShortTermMemoryService` | `ChatMemoryRepository`로 대체 |
| `memory/LongTermMemoryService` | 장기 메모리 패턴 제거 |
| `memory/NoOpShortTermMemoryService` | Spring AI `InMemoryChatMemoryRepository`로 대체 |
| `memory/NoOpLongTermMemoryService` | 장기 메모리 패턴 제거 |
| `memory/MemoryMode` | `@ConditionalOnMissingBean` 패턴으로 불필요 |
| `memory/ConversationSession` | `actorId:sessionId` 복합 키를 `DefaultInvocationService`에서 직접 조합 |
| `memory/bedrock/BedrockLongTermMemoryService` | 장기 메모리 패턴 제거 |
| `memory/bedrock/AgentCoreEventToMessageConverter` | 구현체 모듈로 이동 |
| `memory/bedrock/BedrockMemoryProperties` | `BedrockChatMemoryRepositoryProperties`로 대체 (구현체 모듈) |
| `memory/bedrock/BedrockShortTermMemoryService` | `BedrockChatMemoryRepository`로 대체 (구현체 모듈) |
| `config/BedrockMemoryConfiguration` | `BedrockChatMemoryAutoConfiguration`으로 대체 (autoconfigure 모듈) |
| `config/NoOpMemoryConfiguration` | Spring AI autoconfigure로 대체 |
| `config/MemoryEnabledCondition` | 불필요 |
| `config/MemoryDisabledCondition` | 불필요 |
| `config/LongTermMemoryCondition` | 장기 메모리 패턴 제거 |
| `config/LongTermNotSupportedCondition` | 장기 메모리 패턴 제거 |

### 수정 대상

| 파일 | 내용 |
|------|------|
| `invocation/InvocationResponse` | Javadoc에서 `MemoryMode.NONE` 시 null 반환 언급 제거. `sessionId`, `actorId` 레코드 컴포넌트의 `@Nullable` 어노테이션 제거. 항상 non-null 반환으로 업데이트 |
| `invocation/InvocationRequest` | 변경 없음 (`actorId`, `sessionId` 필드 유지) |

### DefaultInvocationService 변경

- `ShortTermMemoryService`, `LongTermMemoryService`, `MemoryMode` 의존성 제거
- `ChatMemoryRepository` 주입
- `ConversationSession` 제거 → `actorId:sessionId` 복합 키 직접 조합
- `actorId`, `sessionId` 중 하나만 null인 경우에도 null 값만 UUID로 대체 (기존 ConversationSession.Builder와 동일 동작)
- `actorId`/`sessionId` 항상 non-null 반환 (`MemoryMode.NONE` null 반환 로직 제거)

```java
// 히스토리 로드
String resolvedActorId = resolveId(request.actorId());
String resolvedSessionId = resolveId(request.sessionId());
String conversationId = resolvedActorId + ":" + resolvedSessionId;
List<Message> history = chatMemoryRepository.findByConversationId(conversationId);

// LLM 호출
String response = chatClient.prompt()
    .system(buildSystemPrompt())
    .messages(history)
    .user(prompt)
    .call()
    .content();
String content = Objects.requireNonNullElse(response, "");

// 새 메시지 2개만 저장 (append)
chatMemoryRepository.saveAll(conversationId,
    List.of(new UserMessage(prompt), new AssistantMessage(content)));

return new InvocationResponse(content, resolvedSessionId, resolvedActorId);
```

```java
private String resolveId(@Nullable String id) {
    return Objects.requireNonNullElse(id, UUID.randomUUID().toString());
}
```

### host-agent build.gradle.kts 변경

```kotlin
// 변경 전
implementation("software.amazon.awssdk:bedrockagentcore")
implementation("software.amazon.awssdk:bedrockagentcorecontrol")  // 장기 메모리 전용 → 제거

// 변경 후
implementation(project(":spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core"))
// 로컬 dev: memory-id 미설정 시 Spring AI ChatMemoryAutoConfiguration이 InMemory 자동 등록
```

---

## settings.gradle.kts 변경

기존 평면 구조 유지. `memory/` 및 `auto-configurations/` 하위 모듈은 `projectDir`로 경로 지정한다.

```kotlin
// 기존 include 목록에 추가
include("spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
    .projectDir = file("memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")

include("spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
    .projectDir = file("auto-configurations/models/chat/memory/repository/spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
```

---

## Data Flow

```
InvocationsController
    → DefaultInvocationService
        → resolveId(actorId), resolveId(sessionId)            // null → UUID
        → conversationId = actorId + ":" + sessionId
        → ChatMemoryRepository.findByConversationId(id)       // history load
        → ChatClient.prompt().messages(history).user(prompt)  // LLM call
        → ChatMemoryRepository.saveAll(id, [user, assistant]) // append 2 new messages
        → InvocationResponse(content, sessionId, actorId)     // 항상 non-null
```

**로컬 환경** (`memory-id` 미설정):
Spring AI `ChatMemoryAutoConfiguration` → `InMemoryChatMemoryRepository` 자동 등록.
`saveAll` = 전체 교체이므로 멀티턴 히스토리 유지 안됨 (로컬 dev 한계, 허용).
`actorId`/`sessionId`는 항상 non-null로 반환.

**운영 환경** (`memory-id` 설정):
`BedrockChatMemoryAutoConfiguration` → `BedrockChatMemoryRepository` 등록.
`saveAll` = append → 정상 멀티턴 히스토리.

---

## Testing

- **`BedrockChatMemoryRepository` 단위 테스트** (`BedrockAgentCoreClient` mocking) — 구현체 모듈
  - `findByConversationId`: `actorId:sessionId` 파싱 및 `listEvents` 호출 검증, 반환 메시지 오름차순 정렬 검증
  - `saveAll`: 각 메시지에 대해 `createEvent` 호출 검증 (append 동작), 빈 리스트 시 API 호출 없음 검증
  - `deleteByConversationId`: no-op (API 호출 없음) 검증
  - `maxTurns * 2`가 `listEvents.maxResults`에 반영되는지 검증
- **`BedrockChatMemoryAutoConfiguration` autoconfigure 테스트** — autoconfigure 모듈
  - `memory-id` 설정 시 `BedrockChatMemoryRepository` 빈 등록 검증
  - `memory-id` 미설정 시 `BedrockChatMemoryRepository` 빈 미등록 검증 (Spring AI InMemory 폴백 동작)
- **`DefaultInvocationService` 단위 테스트** (`ChatMemoryRepository` mocking) — host-agent
  - `actorId`/`sessionId` null 시 UUID 자동 생성 검증
  - `saveAll` 호출 시 새 메시지 2개만 전달되는지 검증
  - 항상 non-null `actorId`/`sessionId` 반환 검증
- `DefaultInvocationServiceTest` **전체 재작성**: 기존 `serviceWith(MemoryMode)` 헬퍼 및 `MemoryMode`/`ShortTermMemoryService`/`LongTermMemoryService` Mock 의존성 완전 제거. `ChatMemoryRepository` Mock으로 교체
- 기존 `BedrockShortTermMemoryServiceTest`, `BedrockLongTermMemoryServiceTest` 제거

---

## Additional Deliverables

- `CLAUDE.md` 업데이트: `BedrockMemoryProperties`, `MemoryMode`, `BedrockConversationMemoryService`, `BedrockLongTermMemoryService`, `NoOpMemoryConfiguration` 관련 내용을 새 설계로 교체. `springAiVersion` 1.1.2 → 1.1.3 수정
