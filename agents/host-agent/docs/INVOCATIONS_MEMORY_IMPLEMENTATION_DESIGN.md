# /invocations AgentCore Memory — 구현 설계

상위 설계는 [INVOCATIONS_AGENTCORE_MEMORY_DESIGN.md](./INVOCATIONS_AGENTCORE_MEMORY_DESIGN.md)를 따른다.  
본 문서는 **구현 단위**(패키지·클래스·메서드·설정·DTO·흐름)를 정의한다.

---

## 1. 패키지 구조

```
io.github.cokelee777.agent.host
├── InvocationsController          # 기존. 요청/응답만, 오케스트레이션은 InvocationService에 위임.
├── invocation                     # 신규: /invocations 처리 오케스트레이션
│   ├── InvocationService         # 인터페이스: invoke(prompt, actorId?, sessionId?) → InvocationResponse
│   ├── DefaultInvocationService  # 구현: 메모리 로드/저장 + ChatClient 호출
│   ├── InvocationRequest          # DTO: prompt, actorId?, sessionId? (기존에서 확장)
│   └── InvocationResponse          # DTO: content, sessionId, actorId (항상 not null)
├── memory                         # 신규: 메모리 추상화 (구현 무관)
│   ├── ConversationMemoryService       # 인터페이스 (short-term)
│   ├── LongTermMemoryService            # 인터페이스 (long-term)
│   ├── MemoryMode                       # enum. Bedrock 외부에 두어 구현 공통으로 사용
│   └── bedrock                         # Bedrock 구현
│       ├── BedrockConversationMemoryService
│       ├── BedrockLongTermMemoryService
│       ├── AgentCoreEventToMessageConverter
│       └── BedrockMemoryProperties      # 설정
└── RemoteAgentConnections, RemoteAgentProperties, HostAgentApplication  # 기존 유지
```

- `invocation`: HTTP와 무관한 “한 번의 invocation 처리” 로직. Controller는 이 레이어만 호출.
- `memory`: short/long-term 추상화 및 Bedrock 구현. `invocation` 패키지가 `memory`에만 의존.

---

## 2. 설정 (Properties)

### 2.1 BedrockMemoryProperties

- **위치**: `io.github.cokelee777.agent.host.memory.bedrock.BedrockMemoryProperties`
- **바인딩 prefix**: `aws.bedrock.agentcore.memory`
- **필드**:

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|------|------|------|--------|------|
| `memoryId` | String | 예 | `placeholder-memory-id` | Memory 리소스 ID 또는 ARN. (기존 memory-id와 동일) |
| `mode` | MemoryMode | 아니오 | BOTH | NONE, SHORT_TERM, LONG_TERM, BOTH. |
| `shortTermMaxTurns` | int | 아니오 | 10 | loadHistory 시 최근 N턴만 사용 (토큰 제한). |
| `strategyId` | String | 모드에 따라 | `placeholder-strategy-id` | long-term 사용 시 API에 사용. Memory 전략 ID. |
| `longTermMaxResults` | int | 아니오 | 4 | retrieveRelevant 시 상한. |

- **memoryId 기본값**: `placeholder-memory-id` (환경변수 `BEDROCK_MEMORY_ID`로 오버라이드). 배포 시 콘솔의 Memory ID 또는 Memory ARN으로 교체.
- **mode** 기본값: `BOTH`. `BEDROCK_MEMORY_MODE` 등으로 오버라이드.
- **strategyId 기본값**: `placeholder-strategy-id` (환경변수 `BEDROCK_MEMORY_STRATEGY_ID`로 오버라이드). 배포 시 콘솔의 Strategy ID로 교체.
- **strategyId 사용**: mode가 LONG_TERM 또는 BOTH일 때 long-term 검색에 사용. Memory 리소스에 해당 전략이 있어야 함.

### 2.2 application.yml 예시

```yaml
aws:
  bedrock:
    agentcore:
      memory:
        memory-id: ${BEDROCK_MEMORY_ID:placeholder-memory-id}
        mode: ${BEDROCK_MEMORY_MODE:both}   # none | short_term | long_term | both
        short-term-max-turns: 10
        strategy-id: ${BEDROCK_MEMORY_STRATEGY_ID:placeholder-strategy-id}
        long-term-max-results: 4
```

- region은 기존 `spring.ai.bedrock.aws.region` / `BEDROCK_REGION` 재사용. BedrockAgentCoreClient 빈 생성 시 사용.

---

## 3. DTO

### 3.1 InvocationRequest (확장)

- **위치**: `io.github.cokelee777.agent.host.invocation.InvocationRequest`
- **용도**: `POST /invocations` request body.

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `prompt` | String | 예 | 사용자 발화. |
| `actorId` | String | 아니오 | 없으면 첫 메시지에서 생성. |
| `sessionId` | String | 아니오 | 없으면 첫 메시지에서 생성. |

- 유효검사: `prompt`는 `@NotBlank`. `actorId`/`sessionId`는 null/blank 허용.

### 3.2 InvocationResponse

- **위치**: `io.github.cokelee777.agent.host.invocation.InvocationResponse`
- **용도**: 한 번의 invocation 결과. Controller가 JSON으로 반환할 때 사용.

| 필드 | 타입 | 설명 |
|------|------|------|
| `content` | String | 어시스턴트 응답 텍스트. |
| `sessionId` | String (not null) | 이번 대화에 사용한 세션 ID. 첫 메시지에서 생성했든, 요청에서 받았든 항상 동일하게 반환. |
| `actorId` | String (not null) | 이번 대화에 사용한 actor ID. 항상 반환. |

- **non-null**: actorId·sessionId는 처리 단계에서 항상 확정되므로(첫 메시지면 생성, 이후면 요청 값) 응답에서도 **null이 아님**으로 둔다. 클라이언트는 매 응답에서 동일한 형태로 받아 저장·재전송하면 된다.

---

## 4. 인터페이스 및 메서드 시그니처

### 4.1 MemoryMode

- **위치**: `io.github.cokelee777.agent.host.memory.MemoryMode`  
  → **bedrock 패키지 밖**에 두어, Bedrock에 한정되지 않고 **어떤 메모리 구현에서도 통용**되는 값으로 쓴다.
- **enum 값** (객체지향적으로 “메모리 사용 방식”을 닫힌 집합으로 표현):
  - `NONE`: 메모리 미사용. loadHistory / appendUserTurn / appendAssistantTurn / retrieveRelevant 호출 없이 ChatClient만 호출.
  - `SHORT_TERM`: short-term만 사용 (이력 로드 + 턴 저장).
  - `LONG_TERM`: long-term만 사용 (검색 결과만 컨텍스트, 턴은 short-term에 저장).
  - `BOTH`: short-term + long-term 둘 다 사용.
- **NONE을 두는 이유**: “메모리 끔”을 빈 부재나 Optional로만 표현하면 분기가 흩어지고, 설정도 mode와 별도 플래그가 필요해진다. enum에 NONE을 두면 모든 경우를 한 곳에서 `mode`로만 분기할 수 있어 일관된 OO 설계가 된다.
- **역할**: 설정에서 읽은 값을 InvocationService 등에서 분기할 때 사용.

### 4.2 ConversationMemoryService (Short-term)

- **위치**: `io.github.cokelee777.agent.host.memory.ConversationMemoryService`

```java
public interface ConversationMemoryService {

    /**
     * 해당 actor·세션의 대화 이력(최근 턴)을 반환. 없으면 빈 리스트.
     */
    List<Message> loadHistory(String actorId, String sessionId);

    /**
     * 사용자 발화 한 턴을 short-term에 저장.
     */
    void appendUserTurn(String actorId, String sessionId, String userText);

    /**
     * 어시스턴트 응답 한 턴을 short-term에 저장.
     */
    void appendAssistantTurn(String actorId, String sessionId, String assistantText);
}
```

- `Message`: `org.springframework.ai.chat.messages.Message` (UserMessage / AssistantMessage 등).

### 4.3 LongTermMemoryService (Long-term)

- **위치**: `io.github.cokelee777.agent.host.memory.LongTermMemoryService`

```java
public interface LongTermMemoryService {

    /**
     * 해당 actor의 long-term 기록 중 searchQuery와 관련된 텍스트 목록 반환.
     * 전략이 없거나 미사용 모드면 빈 리스트.
     */
    List<String> retrieveRelevant(String actorId, String searchQuery);
}
```

- **Optional 빈**: mode가 SHORT_TERM일 때는 `LongTermMemoryService`를 주입하지 않거나, **NoOpLongTermMemoryService**(항상 빈 리스트 반환)를 등록해도 됨.

---

## 5. 구현 클래스

### 5.1 BedrockConversationMemoryService

- **위치**: `io.github.cokelee777.agent.host.memory.bedrock.BedrockConversationMemoryService`
- **구현**: `ConversationMemoryService`
- **의존성**: `BedrockAgentCoreClient`, `BedrockMemoryProperties`, `AgentCoreEventToMessageConverter`
- **로직**:
  - `loadHistory(actorId, sessionId)`: `ListEventsRequest`(memoryId, actorId, sessionId, includePayloads=true, maxResults=shortTermMaxTurns*2 등) → `listEvents` 호출 → `AgentCoreEventToMessageConverter.toMessages(events)` → **시간순 정렬 후 최근 shortTermMaxTurns턴만** 반환.
  - `appendUserTurn` / `appendAssistantTurn`: `CreateEventRequest`(memoryId, actorId, sessionId, eventTimestamp=now, payload=PayloadType.conversational(Content.fromText(text), Role.USER 또는 Role.ASSISTANT)) → `createEvent` 호출.
- **예외**: SDK 예외는 그대로 전파하거나, 일괄해서 `RuntimeException`으로 래핑. 로깅 후 재던지기.

### 5.2 AgentCoreEventToMessageConverter

- **위치**: `io.github.cokelee777.agent.host.memory.bedrock.AgentCoreEventToMessageConverter`
- **역할**: `List<Event>` → `List<Message>` (Spring AI).
- **메서드**: `List<Message> toMessages(List<Event> events)`  
  - events를 eventTimestamp 기준 정렬.  
  - 각 Event의 payload에서 `conversational()` 존재 시 `role()` / `content().text()`로 UserMessage 또는 AssistantMessage 생성.  
  - blob만 있거나 알 수 없는 payload는 스킵 또는 OTHER 처리(정책에 따라).

### 5.3 BedrockLongTermMemoryService

- **위치**: `io.github.cokelee777.agent.host.memory.bedrock.BedrockLongTermMemoryService`
- **구현**: `LongTermMemoryService`
- **의존성**: `BedrockAgentCoreClient`, `BedrockMemoryProperties`
- **조건**: `strategyId`가 비어 있으면 `retrieveRelevant`는 항상 빈 리스트 반환(호출하지 않음).
- **로직**: `RetrieveMemoryRecordsRequest`(memoryId, namespace=`/strategies/{strategyId}/actors/{actorId}`, searchCriteria=SearchCriteria.builder().memoryStrategyId(strategyId).searchQuery(searchQuery).build(), maxResults=longTermMaxResults) → `retrieveMemoryRecords` → memoryRecordSummaries에서 content.text() 추출해 `List<String>` 반환.
- **예외**: SDK 예외 시 로그 후 빈 리스트 반환 또는 전파(정책에 따라).

### 5.4 NoOpLongTermMemoryService (선택)

- **위치**: `io.github.cokelee777.agent.host.memory.NoOpLongTermMemoryService`
- **구현**: `LongTermMemoryService` — `retrieveRelevant` 항상 `Collections.emptyList()`.
- **용도**: `mode == SHORT_TERM`일 때 `LongTermMemoryService` 빈으로 등록해 NPE 방지.

---

## 6. 오케스트레이션: InvocationService

### 6.1 InvocationService 인터페이스

- **위치**: `io.github.cokelee777.agent.host.invocation.InvocationService`

```java
public interface InvocationService {

    /**
     * 한 번의 /invocations 요청을 처리. actorId/sessionId가 없으면 생성하고, 결과에 포함.
     */
    InvocationResponse invoke(InvocationRequest request);
}
```

### 6.2 DefaultInvocationService

- **위치**: `io.github.cokelee777.agent.host.invocation.DefaultInvocationService`
- **의존성**: `ConversationMemoryService`, `Optional<LongTermMemoryService>`, `ChatClient`, `RemoteAgentConnections`, `BedrockMemoryProperties`(mode 등)
- **흐름** (설계서 §3.5와 동일):

1. **actorId/sessionId 확정**  
   - `sessionId`가 비어 있으면 `UUID.randomUUID().toString()` 등으로 생성, `firstMessage=true` 플래그.  
   - `actorId`가 비어 있으면 동일하게 생성.

2. **컨텍스트 수집 (mode에 따라)**  
   - **NONE**: 메모리 호출 없음. history·relevantMemories 빈 상태로 다음 단계.
   - **short-term 사용**(SHORT_TERM 또는 BOTH): `conversationMemoryService.loadHistory(actorId, sessionId)` → `List<Message> history`.  
   - **long-term 사용**(LONG_TERM 또는 BOTH): `longTermMemoryService.map(s -> s.retrieveRelevant(actorId, request.prompt())).orElse(emptyList())` → `List<String> relevantMemories`.  
     이 리스트를 시스템 프롬프트 뒤에 “관련 기억: …” 형태로 붙이거나, 별도 블록으로 구성.

3. **사용자 턴 저장 (mode ≠ NONE)**  
   - NONE이면 스킵. 그 외: `conversationMemoryService.appendUserTurn(actorId, sessionId, request.prompt())`.

4. **시스템 프롬프트 조립**  
   - 기존 라우팅 시스템 프롬프트(RemoteAgentConnections.getAgentDescriptions() 포함).  
   - long-term 사용 시: `relevantMemories`가 비어 있지 않으면 문자열로 포맷해 시스템 프롬프트에 추가.

5. **ChatClient 호출**  
   - `chatClient.prompt().system(assembledSystemPrompt).messages(history).user(request.prompt()).call().content()`.  
   - history가 비어 있으면 `.messages(history)` 생략 가능(API에 따라).

6. **어시스턴트 턴 저장 (모드 ≠ NONE)**  
   - NONE이면 스킵. 그 외: `conversationMemoryService.appendAssistantTurn(actorId, sessionId, response)`.

7. **InvocationResponse 반환**  
   - `content` = 응답 텍스트.  
   - `sessionId`, `actorId` = 이번 요청에서 확정된 값(생성했든 요청에서 받았든)을 **항상** 설정(not null).

- **예외**: Memory/API 예외는 로깅 후 그대로 전파. Controller에서 5xx 또는 일괄 처리 가능.

---

## 7. Controller 변경

- **InvocationsController**:
  - **Request**: body를 `InvocationRequest`(prompt, actorId?, sessionId?)로 받음.
  - **Delegate**: `InvocationService.invoke(request)` 호출.
  - **Response**: 항상 `InvocationResponse`를 JSON으로 반환 (`content`, `sessionId`, `actorId` 모두 not null). 클라이언트는 매 응답에서 sessionId/actorId를 저장해 다음 요청에 그대로 실어 보냄.

- 기존 `InvocationsController`의 `InvocationsRequest`는 `invocation.InvocationRequest`로 대체하거나, 같은 레코드로 이전(패키지만 이동).

---

## 8. 빈 등록 및 조건

| 빈 | 조건 | 비고 |
|----|------|------|
| BedrockMemoryProperties | — | @ConfigurationProperties. memoryId 등 필수 검증. |
| BedrockAgentCoreClient | — | 기존처럼 region으로 생성. |
| AgentCoreEventToMessageConverter | — | Stateless. |
| BedrockConversationMemoryService | — | ConversationMemoryService 유일 구현 시 기본 주입. |
| BedrockLongTermMemoryService | mode가 LONG_TERM 또는 BOTH이고 strategyId가 비어 있지 않을 때 | @ConditionalOnProperty 등으로 제한 가능. |
| NoOpLongTermMemoryService | mode가 NONE 또는 SHORT_TERM일 때 | LongTermMemoryService 타입으로 등록(또는 NONE이면 LongTerm 빈 자체 미등록). |
| DefaultInvocationService | — | InvocationService 유일 구현. |
| InvocationsController | — | InvocationService 주입. |

- LongTermMemoryService가 없을 때: `DefaultInvocationService`에서 `Optional<LongTermMemoryService>`로 주입받고, `empty()`면 long-term 단계 스킵.

---

## 9. 에러 처리

- **Memory API 실패** (createEvent, listEvents, retrieveMemoryRecords):  
  - 로그 후 예외 전파 → Controller/전역 핸들러에서 503 또는 500 + 메시지.  
  - 또는 “메모리 없이” 폴백: history 빈 리스트, long-term 빈 리스트로 ChatClient만 호출 (정책에 따라 선택).
- **actorId/sessionId null**:  
  - 확정 단계에서 항상 생성하므로, 이후 단계에서는 null이 아님. 요청에 없어도 문제 없음.
- **ChatClient 예외**:  
  - 기존과 동일. 메모리 저장(appendAssistantTurn)은 “응답 성공 후”에만 호출하면 됨. 실패 시 사용자 턴만 저장된 상태로 둘지, 롤백할 수 없으므로 그대로 둠.

---

## 10. 구현 순서 제안

1. **설정·DTO**: `BedrockMemoryProperties`, `MemoryMode`, `InvocationRequest`(확장), `InvocationResponse`.
2. **Short-term**: `ConversationMemoryService`, `AgentCoreEventToMessageConverter`, `BedrockConversationMemoryService`.
3. **오케스트레이션**: `InvocationService`, `DefaultInvocationService` (long-term 호출은 Optional 빈으로 스킵).
4. **Controller**: `InvocationsController`가 `InvocationService` 사용, 응답을 `InvocationResponse` 기반으로 변경.
5. **Long-term**: `LongTermMemoryService`, `BedrockLongTermMemoryService`, `NoOpLongTermMemoryService`, 모드/strategyId에 따른 빈 조건 및 `DefaultInvocationService`에서 long-term 조합.
6. **통합·테스트**: short_term / long_term / both 등 모드별, 첫 메시지·이어지는 대화 시나리오.

이 순서로 구현하면 상위 설계(INVOCATIONS_AGENTCORE_MEMORY_DESIGN.md)의 short-term/long-term 상황별 선택과 SSO 미사용을 만족할 수 있다.
