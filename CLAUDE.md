# CLAUDE.md

## 프로젝트 개요

Amazon Bedrock AgentCore Runtime + Spring AI + A2A 프로토콜을 활용한 멀티 에이전트 오케스트레이션 샘플.
AgentCore Runtime이 세션을 관리하며, 오케스트레이터가 Spring AI tool-calling으로 다운스트림 A2A 에이전트를 호출한다.

## 빌드 / 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 오케스트레이터 실행
./gradlew :a2a-orchestrator:bootRun

# 특정 모듈 컴파일 확인
./gradlew :a2a-common:compileJava
```

Gradle Kotlin DSL 사용: 루트 `build.gradle.kts`, `settings.gradle.kts`, 서브프로젝트 `build.gradle.kts`.

## 모듈 구조

```
amazon-bedrock-agentcore-spring-boot-samples/
├── a2a-common/                              # 공유 유틸리티
│   ├── transport/A2aTransport               # A2A 클라이언트 (AgentCard 캐싱, TaskEvent, timeout)
│   ├── util/TextExtractor                   # Task/Message에서 텍스트 추출
│   └── executor/SkillExecutor               # 스킬 라우팅 인터페이스 (skillId, requiredRole, execute)
├── a2a-spring-boot-autoconfigure/           # A2A 서버·공통 자동 구성
│   ├── A2AServerAutoConfiguration           # AgentExecutor 빈 있을 때만 활성화 (TaskStore, JSON-RPC 등)
│   ├── A2ACommonAutoConfiguration           # AgentCard 빈 있을 때만 활성화 (Ping, AgentCard 컨트롤러)
│   ├── controller/A2AJsonRpcController      # POST / JSON-RPC
│   ├── controller/A2AAgentCardController    # AgentCard 노출
│   └── controller/PingController            # 헬스 체크
└── a2a-orchestrator/                        # 오케스트레이터 에이전트 (port: 9000)
    ├── config/AgentCardConfig               # AgentCard 빈 (orchestrator-url 그대로 url에 반영)
    ├── OrchestratorAgentExecutor            # AgentCore Runtime 진입점
    ├── ChatOrchestrator                     # Spring AI ChatClient 래퍼
    └── tools/
        ├── A2aTool                          # 다운스트림 에이전트 호출 추상 베이스
        ├── OrderAgentTool                   # 주문 조회 / 취소 가능 여부 확인
        ├── DeliveryAgentTool                # 배송 추적
        └── PaymentAgentTool                 # 결제/환불 상태 확인
```

## 핵심 설계 결정 사항

### A2aTransport

- 다운스트림 에이전트 서버는 `emitter.addArtifact()` + `emitter.complete()`로 **Task** 를 반환한다.
  따라서 클라이언트는 반드시 `TaskEvent` 를 수신해야 한다. `MessageEvent` 는 절대 발생하지 않는다.
- `AgentCard` 는 `AtomicReference` + double-checked locking으로 첫 호출 시 1회만 조회 후 캐싱한다.
- `sendMessage()` 는 동기 블로킹 호출이다. `CompletableFuture.supplyAsync()` 로 별도 스레드에서 실행하고
  `.get(timeoutSeconds, TimeUnit.SECONDS)` 로 실제 타임아웃을 강제한다.

### OrchestratorAgentExecutor

- `X-Amzn-Bedrock-AgentCore-Runtime-Session-Id` 헤더에서 세션 ID를 추출한다.
- 로컬 실행 시 헤더가 없으면 UUID로 폴백하여 개발/테스트 편의를 보장한다.

### Spring AI Tool Calling

- `ChatClient.call()` 은 동기 블로킹 agentic loop다. 스트리밍은 `.stream()` 을 사용해야 한다.
- 툴 실행은 기본적으로 **순차 실행**이다. LLM이 한 번의 응답에 여러 툴을 요청해도 Java 측에서 순차 처리한다.
- `MessageChatMemoryAdvisor` 가 세션 ID 단위로 대화 기억을 관리한다 (`ChatMemory.CONVERSATION_ID`).

### AgentCard (orchestrator)

- 오케스트레이터 자신의 AgentCard URL은 `a2a.orchestrator-url`에서 **그대로** 받는다 (정규화 없음).
  로컬 기본값은 `http://localhost:9000`이며, AgentCore Runtime 배포 시에는 런타임/게이트웨이에서
  도달 가능한 URL로 `ORCHESTRATOR_URL` 등으로 덮어쓴다.
- `AgentCardConfig` 에서 스킬 `order_query`, `order_cancellability`, `delivery_tracking` 을 등록한다.

### auto-configure 활성화 조건

- `A2AServerAutoConfiguration` — `AgentExecutor` 빈이 컨텍스트에 있을 때만 활성화 (다운스트림 에이전트 서버용).
- `A2ACommonAutoConfiguration` — `AgentCard` 빈이 컨텍스트에 있을 때만 활성화 (Ping + AgentCard 컨트롤러).

## 의존성 버전 (루트 build.gradle.kts extra)

- `springAiVersion` — 1.1.2  
- `awsSdkVersion` — 2.42.9  
- `a2aVersion` — 0.3.2.Final  
- `gsonVersion` — 2.13.2  

## 코드 컨벤션

- `io.spring.javaformat` 플러그인으로 Spring 공식 포맷을 적용한다.
- 모든 public 타입과 메서드에 Javadoc(`/** */`)을 작성한다.
- 설정값은 환경변수로 오버라이드 가능하도록 `${VAR:default}` 패턴을 사용한다.
- Lombok `@Slf4j` 로 로거를 선언하고, `@RequiredArgsConstructor` 로 생성자 주입을 간소화한다.
