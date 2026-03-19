# /invocations용 AgentCore Memory 설계

블로그 [Deep dive into AgentCore Memory using the Java SDK](https://jettro.dev/deep-dive-into-agentcore-memory-using-the-java-sdk-9446a60126df)를 참조한, **POST /invocations**에서 Bedrock AgentCore Memory를 사용하기 위한 객체지향 설계.  
**SSO는 사용하지 않으며**, 인증은 기본 자격증명(DefaultCredentialsProvider, IAM 역할 등)만 전제로 한다.

---

## 1. 목적

- **AgentCore Memory**로 **short-term**(대화 이벤트)과 **long-term**(전략으로 추출된 지식)을 **상황에 따라** 선택해 사용한다.
- `/invocations` 호출 시, 설정·상황에 따라 short-term만, long-term만, 또는 둘 다 LLM 컨텍스트에 넣고, 이번 턴은 short-term에 저장한다.
- TaskStore(A2A Task 상태)와는 분리: Memory는 **대화·기억**만 담당한다.

---

## 2. 개념 정리 (블로그 기준)

### 2.1 공통

| 개념 | 설명 |
|------|------|
| **Memory 리소스** | 이벤트가 저장되는 “메모리 하나”. Control Plane으로 생성·조회(전략 포함 여부 설정), Data Plane으로 이벤트/레코드 읽기·쓰기. |
| **actorId** | 세션 소유자. 세션·long-term 네임스페이스 모두 actor 단위로 격리. |
| **sessionId** | 한 actor 내에서의 대화 스레드. short-term 이벤트는 (actorId, sessionId)로 구분. |

### 2.2 Short-term

| 개념 | 설명 |
|------|------|
| **Event** | 한 번의 발화(또는 턴). payload는 **Conversational**(USER/ASSISTANT 등), eventTimestamp, memoryId·actorId·sessionId로 구분. |
| **접근** | `listEvents(memoryId, actorId, sessionId)` → 시간순 이벤트. |
| **저장** | `createEvent`로 USER/ASSISTANT 턴 적재. |
| **만료** | Memory 리소스의 `eventExpiryDuration`(일 단위)에 따라 자동 삭제. |

- Memory 리소스를 short-term만 쓰려면 `eventExpiryDuration`만 지정하고 전략을 넣지 않으면 된다.

### 2.3 Long-term

| 개념 | 설명 |
|------|------|
| **전략(Strategy)** | short-term 이벤트에서 **지식**을 추출해 저장. 예: 시맨틱(Semantic) 전략 → 벡터 스토어에 저장. |
| **네임스페이스** | actor별 격리. 기본 형태: `/strategies/{memoryStrategyId}/actors/{actorId}`. |
| **접근** | **시맨틱 검색**. `retrieveMemoryRecords(memoryId, namespace, searchCriteria)` — `searchQuery`(및 strategyId)로 “질문과 관련된 기록”만 조회. |
| **저장** | short-term 이벤트가 쌓이면 **서비스 측에서 전략이 비동기로 추출**해 long-term에 적재. 앱은 createEvent만 하면 됨. |

- Long-term을 쓰려면 Memory 리소스 생성 시 해당 **memoryStrategies**(예: SemanticMemoryStrategyInput)를 포함해야 한다.
- Short-term 없이 long-term만 둘 수는 없다. 항상 short-term 이벤트가 먼저 쌓이고, 그걸 기반으로 long-term이 채워진다.

### 2.4 상황에 따른 선택

| 상황 | 사용 | 비고 |
|------|------|------|
| **최근 대화만 중요** | short-term만 | 토큰 절약, 구현 단순. |
| **과거 요약·사실·선호 재사용** | long-term만 | 세션이 바뀌어도 “이 사용자에 대해 알고 있는 것”만 검색해 넣을 때. |
| **최근 맥락 + 과거 지식** | short-term + long-term | 품질·맥락을 모두 활용할 때. (권장) |

설계에서는 **설정(또는 요청 메타)**으로 “이번 호출에서 short-term 사용 여부”, “long-term 사용 여부”를 선택할 수 있게 한다.

---

## 3. 객체지향 설계

### 3.1 역할 분리

- **Controller**  
  HTTP 요청/응답만 처리. 요청에서 `prompt`, `actorId`, `sessionId`(선택)를 받아, **메모리 서비스**에 위임. **메모리 사용 모드**는 설정 또는 요청 메타에 따라 결정.

- **Short-term: Conversation Memory 서비스 (인터페이스)**  
  “이 actor·세션의 **대화 이력** 가져오기”, “사용자/어시스턴트 한 턴 **저장**” 담당.  
  호출부는 Bedrock/AgentCore에 의존하지 않는다.

- **Long-term: Long-term Memory 조회 (인터페이스)**  
  “이 actor에 대해 **질의와 관련된 long-term 기록** 검색” 담당.  
  예: `retrieveRelevant(actorId, searchQuery)` → 텍스트 리스트(또는 DTO).  
  Memory 리소스에 long-term 전략이 없으면 비활성화(빈 결과 반환 또는 호출 안 함).

- **Bedrock 구현체**  
  - Short-term: `listEvents` / `createEvent` (Conversational payload).  
  - Long-term: `retrieveMemoryRecords`(memoryId, namespace, searchCriteria).  
  memoryId·strategyId·namespace 규칙은 설정에서 주입.

- **이벤트 ↔ 메시지 변환**  
  AgentCore **Event** 리스트(Conversational) → Spring AI **Message** 리스트.  
  Controller/서비스는 “메시지 리스트”만 알면 되고, Event 구조는 변환기 내부에만 존재.

- **설정**  
  Memory 리소스 식별자(`memoryId`), **메모리 사용 모드**(short-term만 / long-term만 / 둘 다), long-term 사용 시 strategyId(및 maxResults 등).  
  actorId/sessionId는 **요청에서 받거나, 첫 메시지 시 host-agent가 생성해 응답으로 돌려줌**. 설정에는 두지 않는다.

### 3.2 메모리 사용 모드 (상황에 따른 선택)

**모드**는 설정(예: `agent.host.memory.mode`) 또는 요청별 메타로 정한다.

| 모드 | Short-term | Long-term | 용도 |
|------|------------|-----------|------|
| `short_term` | 사용 (이력 로드 + 턴 저장) | 사용 안 함 | 최근 대화만 컨텍스트, 구현 단순. |
| `long_term` | 턴만 저장(이력 로드는 안 함) | 사용 (검색 결과만 프롬프트에) | 세션 무관하게 “이 사용자에 대한 지식”만 넣을 때. |
| `both` | 사용 (이력 로드 + 턴 저장) | 사용 (검색 결과를 시스템/컨텍스트에 추가) | 최근 맥락 + 과거 지식 동시 활용. (권장) |

- **저장**: 이번 턴(USER + ASSISTANT)은 **항상 short-term에 저장**한다. (long-term은 전략이 이벤트를 추출해 채움.)
- **로드**: 모드가 short-term 포함이면 `loadHistory(actorId, sessionId)` → 메시지 리스트를 ChatClient에 넘김.  
  모드가 long-term 포함이면 `retrieveRelevant(actorId, searchQuery)`(searchQuery는 이번 `prompt` 또는 그 요약) → 결과 텍스트를 시스템 프롬프트 또는 별도 컨텍스트 블록으로 넣음.

### 3.3 클래스/역할 요약

| 구성요소 | 역할 |
|----------|------|
| **ConversationMemoryService** (인터페이스) | Short-term. `loadHistory(actorId, sessionId)` → 대화 이력(Message 리스트). `appendUserTurn`, `appendAssistantTurn`. |
| **LongTermMemoryService** (인터페이스) | Long-term. `retrieveRelevant(actorId, searchQuery)` → 관련 기록 텍스트 리스트. Memory에 전략이 없으면 미구현/빈 구현. |
| **BedrockConversationMemoryService** | Short-term 구현. listEvents, createEvent(Conversational). |
| **BedrockLongTermMemoryService** | Long-term 구현. retrieveMemoryRecords(namespace, searchCriteria). strategyId·namespace 규칙은 설정. |
| **AgentCoreEventToMessageConverter** | List&lt;Event&gt; → List&lt;Message&gt;. |
| **Memory 설정 (Properties)** | memoryId, **mode**(short_term / long_term / both), region. long-term 사용 시 strategyId, maxResults(선택). |
| **InvocationsController** | ① actorId/sessionId 확정 ② **모드에 따라** short-term 이력 로드 및/또는 long-term 검색 ③ 사용자 턴 저장 ④ ChatClient(system + short-term 메시지 + long-term 텍스트 + user prompt) ⑤ 어시스턴트 턴 저장 ⑥ 응답 반환(첫 메시지 시 sessionId·actorId 포함). |

### 3.4 actorId / sessionId의 주체 — Runtime이 자동으로 넣지 않는 경우

Bedrock AgentCore Runtime에 host-agent를 올려도, **Runtime이 /invocations 요청에 actorId·sessionId를 자동으로 넣어 주는 동작은 전제하지 않는다.**  
그럴 때는 **host-agent가 첫 메시지에서 생성하고, Memory에 저장한 뒤 응답에 실어 주어, 클라이언트가 이후 요청에 담아 보내는** 방식이 필요하다.

- **첫 메시지**
  - 요청에 `sessionId` 없음(또는 actorId도 없음).
  - host-agent가 **sessionId** 생성(예: UUID).
  - **actorId**는 요청에 있으면 그대로 쓰고, 없으면 생성(예: 익명용 UUID) 또는 기본값 정책에 따름.
  - 이 (actorId, sessionId)로 Memory에 이번 턴(USER + ASSISTANT) 저장.
  - **응답**에 **반드시** `sessionId`(및 필요 시 `actorId`)를 포함해 반환.
  - 클라이언트(또는 AgentCore를 쓰는 쪽)는 이 값을 저장했다가(세션/쿠키/스토리지 등) **다음부터 모든 /invocations 요청에 그대로 실어 보냄.**

- **이후 메시지**
  - 요청에 `actorId`, `sessionId` 포함.
  - host-agent는 이 둘로 `loadHistory(actorId, sessionId)` 후 이어서 대화 처리하고, 이번 턴도 같은 (actorId, sessionId)로 Memory에 저장.
  - 응답 형식은 기존과 동일해도 되고, 매번 sessionId/actorId를 다시 넣어 주어도 됨(클라이언트는 동일 값 유지).

정리하면: **actorId·sessionId는 “Runtime이 알아서 넣어 준다”가 아니라, “첫 메시지에서 host-agent가 정하고 Memory에 쓰고, 응답으로 알려 주면, 클라이언트가 그걸 저장해서 이후 요청에 넣는다”**로 설계한다.

### 3.5 데이터 흐름

1. **요청 수신**  
   `POST /invocations` body: `{ "prompt": "...", "actorId": "?", "sessionId": "?" }` (둘 다 선택.)

2. **actorId / sessionId 확정**  
   - `sessionId` 없음 → **새 sessionId 생성**(예: UUID), **첫 메시지**로 간주.  
   - `actorId` 없음 → 정책에 따라 생성(예: 익명 UUID) 또는 기본값.  
   - 둘 다 있음 → 그대로 사용, **이어지는 대화**로 간주.

3. **컨텍스트 수집 (모드에 따라)**  
   - **Short-term 사용 시**: `ConversationMemoryService.loadHistory(actorId, sessionId)`  
     → 첫 메시지면 빈 리스트, 이어지는 대화면 listEvents 후 Event → Message 변환. (선택) 최근 K턴만 사용.  
   - **Long-term 사용 시**: `LongTermMemoryService.retrieveRelevant(actorId, searchQuery)`  
     → searchQuery는 이번 `prompt` 또는 그 요약. retrieveMemoryRecords(namespace, searchCriteria) 호출.  
     → 반환된 텍스트 리스트를 시스템 프롬프트 또는 별도 “관련 기억” 블록으로 사용.

4. **사용자 턴 저장**  
   `ConversationMemoryService.appendUserTurn(actorId, sessionId, prompt)`  
   → createEvent(USER, prompt). (이벤트는 short-term에 적재되며, 전략이 있으면 long-term 추출의 입력이 됨.)

5. **에이전트 호출**  
   system prompt + **(short-term) history messages** + **(long-term 사용 시) 관련 기억 텍스트** + 이번 user(prompt)로 ChatClient 호출.

6. **어시스턴트 턴 저장**  
   `ConversationMemoryService.appendAssistantTurn(actorId, sessionId, response)`  
   → createEvent(ASSISTANT, response).

7. **응답 반환**  
   - **첫 메시지**인 경우: 응답 body에 **반드시** `sessionId`(및 필요 시 `actorId`) 포함.  
     예: `{ "content": "어시스턴트 응답 텍스트", "sessionId": "uuid-...", "actorId": "uuid-..." }`  
     → 클라이언트는 이 값을 저장해 두었다가 이후 요청마다 그대로 전달.  
   - 이어지는 대화면 기존처럼 `content`만 반환해도 되고, 매번 sessionId/actorId를 다시 넣어 줘도 됨.

### 3.6 SDK 사용 (블로그 기준)

- **Control Plane** (bedrockagentcorecontrol): Memory 리소스 생성·조회(GetMemory).  
  Long-term을 쓰려면 생성 시 **memoryStrategies**(예: SemanticMemoryStrategyInput) 포함.  
  이미 만들어진 Memory를 쓸 경우 memoryId(및 필요 시 strategyId)만 설정에 두면 되고, Control Plane은 선택.
- **Data Plane** (bedrockagentcore):  
  - **Short-term**: **createEvent**(memoryId, actorId, sessionId, eventTimestamp, payload=Conversational), **listEvents**(memoryId, actorId, sessionId, includePayloads=true).  
  - **Long-term**: **retrieveMemoryRecords**(memoryId, namespace=`/strategies/{strategyId}/actors/{actorId}`, searchCriteria={ memoryStrategyId, searchQuery }, maxResults).  
    반환된 memoryRecordSummaries에서 content.text() 등으로 텍스트 추출.

인증은 **SSO를 사용하지 않으며**, 기본 자격증명(DefaultCredentialsProvider, IAM 역할 등)만 전제로 한다.

---

## 4. 설정

- **memoryId**  
  사용할 AgentCore Memory 리소스 ID 또는 ARN.  
  예: 샘플 기본값 `placeholder-memory-id`, 환경변수 `BEDROCK_MEMORY_ID`로 실제 Memory ID 또는 ARN 지정.
- **memory mode** (상황에 따른 선택)  
  `short_term` | `long_term` | `both`.  
  기본값은 `both` 또는 `short_term` 중 정책에 따라 결정. 환경변수/프로파일로 오버라이드 가능.
- **long-term 사용 시**  
  - **strategyId**: Memory 리소스에 붙은 long-term 전략 ID. namespace 구성 및 retrieveMemoryRecords에 사용.  
    예: 샘플 기본값 `placeholder-strategy-id`, 환경변수 `BEDROCK_MEMORY_STRATEGY_ID`로 실제 Strategy ID 지정.  
  - **maxResults**(선택): 검색 시 가져올 기록 수 상한.
- **region**  
  BedrockAgentCoreClient용. 기존 `spring.ai.bedrock.aws.region` 또는 `BEDROCK_REGION` 재사용 가능.
- actorId / sessionId는 **요청에서 받거나, 첫 메시지 시 host-agent가 생성해 응답으로 반환**. 설정에는 두지 않음.

---

## 5. TaskStore와의 관계

- **TaskStore**  
  A2A 프로토콜의 Task 상태(save/get/delete, isTaskActive/isTaskFinalized) 전용.  
  Memory 설계와 무관하게 기존대로 두거나, 기본 InMemoryTaskStore 사용.
- **Memory**  
  /invocations의 **대화·기억** 담당. Short-term(Conversational 이벤트 저장·조회)과 Long-term(전략 기반 검색)을 모드에 따라 사용.  
  TaskStore와는 목적·데이터 모델이 다르므로 분리 유지.

---

## 6. 요약

- **Memory** = /invocations용 **short-term**(대화 이벤트) + **long-term**(전략으로 추출된 지식). **상황(설정)에 따라** short-term만, long-term만, 또는 둘 다 사용.
- **메모리 모드**: `short_term` / `long_term` / `both`. 설정(또는 요청 메타)으로 선택. 턴 저장은 항상 short-term에 수행.
- **ConversationMemoryService**: short-term 이력 로드·사용자/어시스턴트 턴 저장. **LongTermMemoryService**: long-term 시맨틱 검색(actorId, searchQuery) → 관련 텍스트 리스트.
- **Bedrock 구현체**: Short-term은 createEvent/listEvents, Long-term은 retrieveMemoryRecords(namespace, searchCriteria).
- **AgentCoreEventToMessageConverter**: Event ↔ Spring AI Message 변환.
- **actorId·sessionId**: Runtime이 자동으로 넣어 주지 않는다고 가정. 첫 메시지에서 host-agent가 생성 → Memory에 저장 → 응답에 sessionId(·actorId) 포함 → 클라이언트가 저장 후 이후 요청마다 전달.
- **SSO는 사용하지 않음.** 인증은 기본 자격증명(DefaultCredentialsProvider, IAM 역할 등)만 전제.
