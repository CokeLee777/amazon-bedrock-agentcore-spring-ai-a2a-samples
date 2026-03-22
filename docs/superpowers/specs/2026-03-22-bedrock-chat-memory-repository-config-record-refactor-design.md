# BedrockChatMemoryRepositoryConfig — Record 전환 및 기본값 중복 제거 설계

## 목표

1. `BedrockChatMemoryRepositoryConfig`를 record로 전환하고 compact constructor에서 validation을 수행한다.
2. `BedrockChatMemoryRepositoryProperties`의 `@DefaultValue("10")` 하드코딩을 제거하고 `DEFAULT_MAX_TURNS` 상수를 참조하도록 한다.

## 변경 1 — `BedrockChatMemoryRepositoryConfig` record 전환

### 현재

`final class` + private constructor + Builder 패턴. validation이 Builder setter와 `build()` 두 곳에 분산되어 있다.

### 변경 후

`record`로 전환한다. canonical constructor는 compact constructor로 대체되어 단일 validation 지점이 된다. Builder는 외부 API 호환성과 파라미터명 명시를 위해 유지한다.

```java
public record BedrockChatMemoryRepositoryConfig(String memoryId, int maxTurns) {

    public static final int DEFAULT_MAX_TURNS = 10;

    // compact constructor — 유일한 강제 validation 지점
    public BedrockChatMemoryRepositoryConfig {
        Assert.hasText(memoryId, "memoryId must not be blank");
        Assert.isTrue(maxTurns > 0, "maxTurns must be greater than zero");
    }

    public static Builder builder() { ... }

    public static final class Builder {
        // setter에 Assert 유지 (fail-fast)
        // build()에서 Assert 제거 (compact constructor가 담당)
        public BedrockChatMemoryRepositoryConfig build() {
            return new BedrockChatMemoryRepositoryConfig(this.memoryId, this.maxTurns);
        }
    }
}
```

**결과:**
- `build()` 내 중복 `Assert.hasText` 제거 (compact constructor로 위임)
- Builder setter의 Assert는 유지 (fail-fast 목적)
- 기존 `memoryId()`, `maxTurns()` accessor API는 record accessor로 자동 제공되어 호환성 유지

## 변경 2 — `BedrockChatMemoryRepositoryProperties` 기본값 참조

### 현재

```java
@DefaultValue("10") int maxTurns
```

`10`이 `BedrockChatMemoryRepositoryConfig.DEFAULT_MAX_TURNS`와 별도로 하드코딩되어 있어 값 변경 시 두 곳을 수동으로 동기화해야 한다.

### 변경 후

```java
@DefaultValue("" + BedrockChatMemoryRepositoryConfig.DEFAULT_MAX_TURNS) int maxTurns
```

`public static final int` 상수와 문자열 연결은 Java 컴파일 타임 상수이므로 annotation 값으로 사용 가능하다. autoconfigure 모듈이 이미 `BedrockChatMemoryRepositoryConfig`에 의존하므로 추가 의존성 없다.

## 영향 범위

| 파일 | 변경 |
|------|------|
| `BedrockChatMemoryRepositoryConfig.java` | class → record, compact constructor 추가, build()에서 Assert 제거 |
| `BedrockChatMemoryRepositoryProperties.java` | `@DefaultValue` 값을 상수 참조로 변경 |

`BedrockChatMemoryAutoConfiguration`의 Builder 사용 코드는 변경 없음.
