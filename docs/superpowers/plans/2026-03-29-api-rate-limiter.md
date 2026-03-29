# API Rate Limiter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 4.0.4 기반의 분산 API Rate Limiter를 구현하여 시스템 디자인 면접 핵심 개념(Token Bucket, Sliding Window, 분산 환경, 캐싱)을 직접 체험한다.

**Architecture:** Rate Limiting 알고리즘(Token Bucket, Sliding Window Counter)을 Strategy 패턴으로 추상화하고, 인메모리/Redis 두 가지 저장소를 지원한다. Spring Filter에서 커스텀 `@RateLimit` 어노테이션을 읽어 API별 제한 정책을 적용한다. Docker Compose로 Redis를 띄워 분산 환경을 로컬에서 테스트한다.

**Tech Stack:** Spring Boot 4.0.4, Spring Data Redis, Gradle (Kotlin DSL), JUnit 5, Testcontainers, Docker Compose, H2 (통계 저장), Lombok

---

## File Structure

```
src/main/java/jaemin/ai/ratelimiter/
├── RateLimiterApplication.java              # Spring Boot 메인
├── annotation/
│   └── RateLimit.java                       # 커스텀 어노테이션
├── algorithm/
│   ├── RateLimitAlgorithm.java              # 알고리즘 인터페이스
│   ├── TokenBucketAlgorithm.java            # Token Bucket 구현
│   └── SlidingWindowCounterAlgorithm.java   # Sliding Window Counter 구현
├── storage/
│   ├── RateLimitStorage.java                # 저장소 인터페이스
│   ├── InMemoryRateLimitStorage.java        # ConcurrentHashMap 기반
│   └── RedisRateLimitStorage.java           # Redis 기반
├── filter/
│   └── RateLimitFilter.java                 # Jakarta Servlet Filter
├── resolver/
│   └── ClientKeyResolver.java               # 클라이언트 식별 (IP 기반)
├── config/
│   ├── RateLimitProperties.java             # 설정 프로퍼티
│   └── RateLimitAutoConfiguration.java      # 자동 설정
├── controller/
│   ├── DemoController.java                  # 테스트용 API
│   └── RateLimitStatsController.java        # 통계 조회 API
├── dto/
│   └── RateLimitInfo.java                   # Rate Limit 응답 DTO (record)
└── exception/
    └── RateLimitExceededException.java      # 429 예외

src/test/java/jaemin/ai/ratelimiter/
├── algorithm/
│   ├── TokenBucketAlgorithmTest.java
│   └── SlidingWindowCounterAlgorithmTest.java
├── storage/
│   ├── InMemoryRateLimitStorageTest.java
│   └── RedisRateLimitStorageTest.java       # Testcontainers 사용
├── filter/
│   └── RateLimitFilterIntegrationTest.java
└── controller/
    └── DemoControllerIntegrationTest.java

docker-compose.yml                            # Redis 컨테이너
```

---

### Task 1: 프로젝트 스캐폴딩 (Spring Boot 4.0.4 + Gradle)

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `src/main/java/jaemin/ai/ratelimiter/RateLimiterApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `docker-compose.yml`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Gradle Wrapper 초기화**

```bash
cd /Users/jaemim.kim/workspace/test-superpowers
gradle wrapper --gradle-version 9.3.0
```

- [ ] **Step 2: `settings.gradle.kts` 작성**

```kotlin
rootProject.name = "api-rate-limiter"
```

- [ ] **Step 3: `build.gradle.kts` 작성**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "jaemin.ai"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    runtimeOnly("com.h2database:h2")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 4: `application.yml` 작성**

```yaml
spring:
  application:
    name: api-rate-limiter
  threads:
    virtual:
      enabled: true
  data:
    redis:
      host: localhost
      port: 6379
  h2:
    console:
      enabled: true
  datasource:
    url: jdbc:h2:mem:ratelimiter
    driver-class-name: org.h2.Driver

rate-limit:
  enabled: true
  default-algorithm: TOKEN_BUCKET
  default-capacity: 10
  default-refill-rate: 5
  storage-type: IN_MEMORY
```

- [ ] **Step 5: `RateLimiterApplication.java` 작성**

```java
package jaemin.ai.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }
}
```

- [ ] **Step 6: `docker-compose.yml` 작성**

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru
```

- [ ] **Step 7: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋**

```bash
git init
git add .
git commit -m "chore: scaffold Spring Boot 4.0.4 rate limiter project"
```

---

### Task 2: Rate Limit 알고리즘 인터페이스 + Token Bucket 구현 (TDD)

**Files:**
- Create: `src/main/java/jaemin/ai/ratelimiter/algorithm/RateLimitAlgorithm.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/algorithm/TokenBucketAlgorithm.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/dto/RateLimitInfo.java`
- Create: `src/test/java/jaemin/ai/ratelimiter/algorithm/TokenBucketAlgorithmTest.java`

- [ ] **Step 1: `RateLimitInfo.java` DTO 작성**

```java
package jaemin.ai.ratelimiter.dto;

public record RateLimitInfo(
    boolean allowed,
    long remainingTokens,
    long retryAfterMillis
) {
    public static RateLimitInfo allowed(long remaining) {
        return new RateLimitInfo(true, remaining, 0);
    }

    public static RateLimitInfo rejected(long retryAfterMillis) {
        return new RateLimitInfo(false, 0, retryAfterMillis);
    }
}
```

- [ ] **Step 2: `RateLimitAlgorithm.java` 인터페이스 작성**

```java
package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;

public interface RateLimitAlgorithm {

    /**
     * 요청을 허용할지 판단한다.
     * @param key 클라이언트 식별 키 (e.g. IP:endpoint)
     * @param capacity 버킷 최대 용량
     * @param rate 초당 리필 속도 (또는 윈도우당 허용 수)
     * @return 허용 여부 및 남은 토큰 정보
     */
    RateLimitInfo tryConsume(String key, long capacity, long rate);
}
```

- [ ] **Step 3: Token Bucket 테스트 작성**

```java
package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketAlgorithmTest {

    private TokenBucketAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new TokenBucketAlgorithm();
    }

    @Test
    @DisplayName("첫 번째 요청은 항상 허용된다")
    void firstRequestIsAllowed() {
        RateLimitInfo info = algorithm.tryConsume("client-1", 10, 5);

        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(9);
    }

    @Test
    @DisplayName("용량 초과 시 요청이 거부된다")
    void rejectsWhenBucketIsEmpty() {
        for (int i = 0; i < 10; i++) {
            algorithm.tryConsume("client-2", 10, 5);
        }

        RateLimitInfo info = algorithm.tryConsume("client-2", 10, 5);

        assertThat(info.allowed()).isFalse();
        assertThat(info.retryAfterMillis()).isGreaterThan(0);
    }

    @Test
    @DisplayName("서로 다른 키는 독립적으로 동작한다")
    void differentKeysAreIndependent() {
        for (int i = 0; i < 10; i++) {
            algorithm.tryConsume("client-A", 10, 5);
        }

        RateLimitInfo info = algorithm.tryConsume("client-B", 10, 5);

        assertThat(info.allowed()).isTrue();
    }

    @Test
    @DisplayName("시간이 지나면 토큰이 리필된다")
    void tokensRefillOverTime() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            algorithm.tryConsume("client-3", 10, 10);
        }
        assertThat(algorithm.tryConsume("client-3", 10, 10).allowed()).isFalse();

        // 500ms 대기 → 10 tokens/sec * 0.5sec = 5 tokens 리필 기대
        Thread.sleep(550);

        RateLimitInfo info = algorithm.tryConsume("client-3", 10, 10);
        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isGreaterThanOrEqualTo(3);
    }
}
```

- [ ] **Step 4: 테스트 실행 - 실패 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.algorithm.TokenBucketAlgorithmTest" -i`
Expected: FAIL - `TokenBucketAlgorithm` 클래스가 존재하지 않음

- [ ] **Step 5: `TokenBucketAlgorithm.java` 구현**

```java
package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;

import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitInfo tryConsume(String key, long capacity, long rate) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        return bucket.tryConsume(capacity, rate);
    }

    private static class Bucket {
        private double tokens;
        private long lastRefillTimestamp;

        Bucket(long capacity) {
            this.tokens = capacity;
            this.lastRefillTimestamp = System.nanoTime();
        }

        synchronized RateLimitInfo tryConsume(long capacity, long rate) {
            refill(capacity, rate);

            if (tokens >= 1) {
                tokens -= 1;
                return RateLimitInfo.allowed((long) tokens);
            }

            long retryAfterMillis = (long) Math.ceil(1000.0 / rate);
            return RateLimitInfo.rejected(retryAfterMillis);
        }

        private void refill(long capacity, long rate) {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillTimestamp) / 1_000_000_000.0;
            double tokensToAdd = elapsed * rate;

            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
```

- [ ] **Step 6: 테스트 실행 - 성공 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.algorithm.TokenBucketAlgorithmTest" -i`
Expected: 4 tests PASSED

- [ ] **Step 7: 커밋**

```bash
git add .
git commit -m "feat: implement Token Bucket rate limiting algorithm with TDD"
```

---

### Task 3: Sliding Window Counter 알고리즘 구현 (TDD)

**Files:**
- Create: `src/main/java/jaemin/ai/ratelimiter/algorithm/SlidingWindowCounterAlgorithm.java`
- Create: `src/test/java/jaemin/ai/ratelimiter/algorithm/SlidingWindowCounterAlgorithmTest.java`

- [ ] **Step 1: Sliding Window Counter 테스트 작성**

```java
package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowCounterAlgorithmTest {

    private SlidingWindowCounterAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        // 1초 윈도우
        algorithm = new SlidingWindowCounterAlgorithm(1000);
    }

    @Test
    @DisplayName("윈도우 내 요청이 허용된다")
    void allowsRequestsWithinWindow() {
        RateLimitInfo info = algorithm.tryConsume("client-1", 5, 5);

        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(4);
    }

    @Test
    @DisplayName("윈도우 내 제한 초과 시 거부된다")
    void rejectsWhenWindowLimitExceeded() {
        for (int i = 0; i < 5; i++) {
            algorithm.tryConsume("client-2", 5, 5);
        }

        RateLimitInfo info = algorithm.tryConsume("client-2", 5, 5);

        assertThat(info.allowed()).isFalse();
        assertThat(info.retryAfterMillis()).isGreaterThan(0);
    }

    @Test
    @DisplayName("윈도우가 지나면 다시 요청이 허용된다")
    void allowsAfterWindowExpires() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            algorithm.tryConsume("client-3", 5, 5);
        }
        assertThat(algorithm.tryConsume("client-3", 5, 5).allowed()).isFalse();

        Thread.sleep(1100);

        RateLimitInfo info = algorithm.tryConsume("client-3", 5, 5);
        assertThat(info.allowed()).isTrue();
    }

    @Test
    @DisplayName("서로 다른 키는 독립적으로 동작한다")
    void differentKeysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            algorithm.tryConsume("client-A", 5, 5);
        }

        RateLimitInfo info = algorithm.tryConsume("client-B", 5, 5);
        assertThat(info.allowed()).isTrue();
    }
}
```

- [ ] **Step 2: 테스트 실행 - 실패 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.algorithm.SlidingWindowCounterAlgorithmTest" -i`
Expected: FAIL - `SlidingWindowCounterAlgorithm` 클래스가 존재하지 않음

- [ ] **Step 3: `SlidingWindowCounterAlgorithm.java` 구현**

```java
package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SlidingWindowCounterAlgorithm implements RateLimitAlgorithm {

    private final long windowSizeMillis;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> windows = new ConcurrentHashMap<>();

    public SlidingWindowCounterAlgorithm(long windowSizeMillis) {
        this.windowSizeMillis = windowSizeMillis;
    }

    @Override
    public RateLimitInfo tryConsume(String key, long capacity, long rate) {
        ConcurrentLinkedDeque<Long> timestamps = windows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeMillis;

        // 윈도우 밖의 오래된 타임스탬프 제거
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }

        long currentCount = timestamps.size();

        if (currentCount < capacity) {
            timestamps.addLast(now);
            long remaining = capacity - currentCount - 1;
            return RateLimitInfo.allowed(remaining);
        }

        // 가장 오래된 요청이 윈도우를 벗어나는 시점까지 대기
        long oldestTimestamp = timestamps.peekFirst();
        long retryAfter = oldestTimestamp + windowSizeMillis - now;
        return RateLimitInfo.rejected(Math.max(retryAfter, 1));
    }
}
```

- [ ] **Step 4: 테스트 실행 - 성공 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.algorithm.SlidingWindowCounterAlgorithmTest" -i`
Expected: 4 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add .
git commit -m "feat: implement Sliding Window Counter rate limiting algorithm"
```

---

### Task 4: 저장소 추상화 + InMemory 구현 (TDD)

**Files:**
- Create: `src/main/java/jaemin/ai/ratelimiter/storage/RateLimitStorage.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/storage/InMemoryRateLimitStorage.java`
- Create: `src/test/java/jaemin/ai/ratelimiter/storage/InMemoryRateLimitStorageTest.java`

- [ ] **Step 1: `RateLimitStorage.java` 인터페이스 작성**

```java
package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;

public interface RateLimitStorage {

    /**
     * Token Bucket 방식으로 요청을 처리한다.
     */
    RateLimitInfo tryConsumeTokenBucket(String key, long capacity, long refillRate);

    /**
     * Sliding Window 방식으로 요청을 처리한다.
     */
    RateLimitInfo tryConsumeSlidingWindow(String key, long capacity, long windowSizeMillis);
}
```

- [ ] **Step 2: InMemory 저장소 테스트 작성**

```java
package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStorageTest {

    private InMemoryRateLimitStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryRateLimitStorage();
    }

    @Test
    @DisplayName("Token Bucket: 요청이 허용된다")
    void tokenBucketAllows() {
        RateLimitInfo info = storage.tryConsumeTokenBucket("key1", 10, 5);

        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(9);
    }

    @Test
    @DisplayName("Token Bucket: 용량 초과 시 거부된다")
    void tokenBucketRejects() {
        for (int i = 0; i < 10; i++) {
            storage.tryConsumeTokenBucket("key2", 10, 5);
        }

        RateLimitInfo info = storage.tryConsumeTokenBucket("key2", 10, 5);
        assertThat(info.allowed()).isFalse();
    }

    @Test
    @DisplayName("Sliding Window: 요청이 허용된다")
    void slidingWindowAllows() {
        RateLimitInfo info = storage.tryConsumeSlidingWindow("key3", 5, 1000);

        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(4);
    }

    @Test
    @DisplayName("Sliding Window: 제한 초과 시 거부된다")
    void slidingWindowRejects() {
        for (int i = 0; i < 5; i++) {
            storage.tryConsumeSlidingWindow("key4", 5, 1000);
        }

        RateLimitInfo info = storage.tryConsumeSlidingWindow("key4", 5, 1000);
        assertThat(info.allowed()).isFalse();
    }
}
```

- [ ] **Step 3: 테스트 실행 - 실패 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.storage.InMemoryRateLimitStorageTest" -i`
Expected: FAIL

- [ ] **Step 4: `InMemoryRateLimitStorage.java` 구현**

```java
package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.algorithm.RateLimitAlgorithm;
import jaemin.ai.ratelimiter.algorithm.SlidingWindowCounterAlgorithm;
import jaemin.ai.ratelimiter.algorithm.TokenBucketAlgorithm;
import jaemin.ai.ratelimiter.dto.RateLimitInfo;

public class InMemoryRateLimitStorage implements RateLimitStorage {

    private final RateLimitAlgorithm tokenBucket = new TokenBucketAlgorithm();
    private final SlidingWindowCounterAlgorithm slidingWindow = new SlidingWindowCounterAlgorithm(1000);

    @Override
    public RateLimitInfo tryConsumeTokenBucket(String key, long capacity, long refillRate) {
        return tokenBucket.tryConsume(key, capacity, refillRate);
    }

    @Override
    public RateLimitInfo tryConsumeSlidingWindow(String key, long capacity, long windowSizeMillis) {
        // windowSizeMillis를 인스턴스별로 처리하기 위해 키에 윈도우 크기를 포함
        String compositeKey = key + ":w" + windowSizeMillis;
        return slidingWindow.tryConsume(compositeKey, capacity, capacity);
    }
}
```

- [ ] **Step 5: 테스트 실행 - 성공 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.storage.InMemoryRateLimitStorageTest" -i`
Expected: 4 tests PASSED

- [ ] **Step 6: 커밋**

```bash
git add .
git commit -m "feat: add storage abstraction with in-memory implementation"
```

---

### Task 5: Redis 저장소 구현 (TDD + Testcontainers)

**Files:**
- Create: `src/main/java/jaemin/ai/ratelimiter/storage/RedisRateLimitStorage.java`
- Create: `src/test/java/jaemin/ai/ratelimiter/storage/RedisRateLimitStorageTest.java`
- Create: `src/main/resources/scripts/token_bucket.lua`
- Create: `src/main/resources/scripts/sliding_window.lua`

- [ ] **Step 1: Token Bucket Lua 스크립트 작성**

```lua
-- token_bucket.lua
-- KEYS[1]: 버킷 키
-- ARGV[1]: capacity, ARGV[2]: refillRate, ARGV[3]: 현재 시간(ms)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    lastRefill = now
end

-- 리필 계산
local elapsed = (now - lastRefill) / 1000.0
local tokensToAdd = elapsed * refillRate
tokens = math.min(capacity, tokens + tokensToAdd)
lastRefill = now

if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    redis.call('PEXPIRE', key, math.ceil(capacity / refillRate) * 1000 + 1000)
    return {1, math.floor(tokens), 0}
else
    local retryAfter = math.ceil(1000 / refillRate)
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    redis.call('PEXPIRE', key, math.ceil(capacity / refillRate) * 1000 + 1000)
    return {0, 0, retryAfter}
end
```

- [ ] **Step 2: Sliding Window Lua 스크립트 작성**

```lua
-- sliding_window.lua
-- KEYS[1]: 윈도우 키
-- ARGV[1]: capacity, ARGV[2]: windowSizeMs, ARGV[3]: 현재 시간(ms)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local windowSize = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local windowStart = now - windowSize

-- 윈도우 밖의 오래된 항목 제거
redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

local currentCount = redis.call('ZCARD', key)

if currentCount < capacity then
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('PEXPIRE', key, windowSize + 1000)
    local remaining = capacity - currentCount - 1
    return {1, remaining, 0}
else
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfter = 1
    if #oldest >= 2 then
        retryAfter = math.max(tonumber(oldest[2]) + windowSize - now, 1)
    end
    return {0, 0, retryAfter}
end
```

- [ ] **Step 3: Redis 저장소 테스트 작성**

```java
package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RedisRateLimitStorageTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RedisRateLimitStorage storage;

    @BeforeEach
    void setUp() {
        storage = new RedisRateLimitStorage(redisTemplate);
        // 테스트 간 격리를 위해 Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Redis Token Bucket: 요청이 허용된다")
    void tokenBucketAllows() {
        RateLimitInfo info = storage.tryConsumeTokenBucket("r-key1", 10, 5);

        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(9);
    }

    @Test
    @DisplayName("Redis Token Bucket: 용량 초과 시 거부된다")
    void tokenBucketRejects() {
        for (int i = 0; i < 10; i++) {
            storage.tryConsumeTokenBucket("r-key2", 10, 5);
        }

        RateLimitInfo info = storage.tryConsumeTokenBucket("r-key2", 10, 5);
        assertThat(info.allowed()).isFalse();
    }

    @Test
    @DisplayName("Redis Sliding Window: 요청이 허용된다")
    void slidingWindowAllows() {
        RateLimitInfo info = storage.tryConsumeSlidingWindow("r-key3", 5, 1000);

        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(4);
    }

    @Test
    @DisplayName("Redis Sliding Window: 제한 초과 시 거부된다")
    void slidingWindowRejects() {
        for (int i = 0; i < 5; i++) {
            storage.tryConsumeSlidingWindow("r-key4", 5, 1000);
        }

        RateLimitInfo info = storage.tryConsumeSlidingWindow("r-key4", 5, 1000);
        assertThat(info.allowed()).isFalse();
    }
}
```

- [ ] **Step 4: 테스트 실행 - 실패 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.storage.RedisRateLimitStorageTest" -i`
Expected: FAIL - `RedisRateLimitStorage` 클래스가 존재하지 않음

- [ ] **Step 5: `RedisRateLimitStorage.java` 구현**

```java
package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

public class RedisRateLimitStorage implements RateLimitStorage {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tokenBucketScript;
    private final RedisScript<List> slidingWindowScript;

    public RedisRateLimitStorage(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
        this.slidingWindowScript = RedisScript.of(new ClassPathResource("scripts/sliding_window.lua"), List.class);
    }

    @Override
    public RateLimitInfo tryConsumeTokenBucket(String key, long capacity, long refillRate) {
        String now = String.valueOf(System.currentTimeMillis());
        List result = redisTemplate.execute(
                tokenBucketScript,
                List.of("rl:tb:" + key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                now
        );
        return parseResult(result);
    }

    @Override
    public RateLimitInfo tryConsumeSlidingWindow(String key, long capacity, long windowSizeMillis) {
        String now = String.valueOf(System.currentTimeMillis());
        List result = redisTemplate.execute(
                slidingWindowScript,
                List.of("rl:sw:" + key),
                String.valueOf(capacity),
                String.valueOf(windowSizeMillis),
                now
        );
        return parseResult(result);
    }

    @SuppressWarnings("unchecked")
    private RateLimitInfo parseResult(List result) {
        long allowed = ((Number) result.get(0)).longValue();
        long remaining = ((Number) result.get(1)).longValue();
        long retryAfter = ((Number) result.get(2)).longValue();

        if (allowed == 1) {
            return RateLimitInfo.allowed(remaining);
        }
        return RateLimitInfo.rejected(retryAfter);
    }
}
```

- [ ] **Step 6: 테스트 실행 - 성공 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.storage.RedisRateLimitStorageTest" -i`
Expected: 4 tests PASSED

- [ ] **Step 7: 커밋**

```bash
git add .
git commit -m "feat: add Redis-based rate limit storage with Lua scripts"
```

---

### Task 6: 커스텀 어노테이션 + Filter + 설정 (TDD)

**Files:**
- Create: `src/main/java/jaemin/ai/ratelimiter/annotation/RateLimit.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/resolver/ClientKeyResolver.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/filter/RateLimitFilter.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/exception/RateLimitExceededException.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/config/RateLimitProperties.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/config/RateLimitAutoConfiguration.java`

- [ ] **Step 1: `RateLimit.java` 어노테이션 작성**

```java
package jaemin.ai.ratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 최대 요청 수 (Token Bucket: 버킷 크기, Sliding Window: 윈도우당 허용 수)
     */
    long capacity() default 10;

    /**
     * Token Bucket: 초당 리필 속도 / Sliding Window: 무시됨
     */
    long refillRate() default 5;

    /**
     * 사용할 알고리즘 ("TOKEN_BUCKET" 또는 "SLIDING_WINDOW")
     */
    String algorithm() default "";
}
```

- [ ] **Step 2: `ClientKeyResolver.java` 작성**

```java
package jaemin.ai.ratelimiter.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientKeyResolver {

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null) ? forwardedFor.split(",")[0].trim() : request.getRemoteAddr();
        return ip + ":" + request.getMethod() + ":" + request.getRequestURI();
    }
}
```

- [ ] **Step 3: `RateLimitExceededException.java` 작성**

```java
package jaemin.ai.ratelimiter.exception;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;

public class RateLimitExceededException extends RuntimeException {

    private final RateLimitInfo rateLimitInfo;

    public RateLimitExceededException(RateLimitInfo rateLimitInfo) {
        super("Rate limit exceeded");
        this.rateLimitInfo = rateLimitInfo;
    }

    public RateLimitInfo getRateLimitInfo() {
        return rateLimitInfo;
    }
}
```

- [ ] **Step 4: `RateLimitProperties.java` 작성**

```java
package jaemin.ai.ratelimiter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private String defaultAlgorithm = "TOKEN_BUCKET";
    private long defaultCapacity = 10;
    private long defaultRefillRate = 5;
    private long defaultWindowSizeMillis = 1000;
    private String storageType = "IN_MEMORY"; // IN_MEMORY 또는 REDIS
}
```

- [ ] **Step 5: `RateLimitAutoConfiguration.java` 작성**

```java
package jaemin.ai.ratelimiter.config;

import jaemin.ai.ratelimiter.filter.RateLimitFilter;
import jaemin.ai.ratelimiter.resolver.ClientKeyResolver;
import jaemin.ai.ratelimiter.storage.InMemoryRateLimitStorage;
import jaemin.ai.ratelimiter.storage.RateLimitStorage;
import jaemin.ai.ratelimiter.storage.RedisRateLimitStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "rate-limit.storage-type", havingValue = "IN_MEMORY", matchIfMissing = true)
    public RateLimitStorage inMemoryRateLimitStorage() {
        return new InMemoryRateLimitStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.storage-type", havingValue = "REDIS")
    public RateLimitStorage redisRateLimitStorage(StringRedisTemplate redisTemplate) {
        return new RedisRateLimitStorage(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitFilter rateLimitFilter(
            RateLimitStorage storage,
            RateLimitProperties properties,
            ClientKeyResolver keyResolver,
            RequestMappingHandlerMapping handlerMapping) {
        return new RateLimitFilter(storage, properties, keyResolver, handlerMapping);
    }
}
```

- [ ] **Step 6: `RateLimitFilter.java` 작성**

```java
package jaemin.ai.ratelimiter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jaemin.ai.ratelimiter.annotation.RateLimit;
import jaemin.ai.ratelimiter.config.RateLimitProperties;
import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import jaemin.ai.ratelimiter.resolver.ClientKeyResolver;
import jaemin.ai.ratelimiter.storage.RateLimitStorage;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStorage storage;
    private final RateLimitProperties properties;
    private final ClientKeyResolver keyResolver;
    private final RequestMappingHandlerMapping handlerMapping;

    public RateLimitFilter(
            RateLimitStorage storage,
            RateLimitProperties properties,
            ClientKeyResolver keyResolver,
            RequestMappingHandlerMapping handlerMapping) {
        this.storage = storage;
        this.properties = properties;
        this.keyResolver = keyResolver;
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimit rateLimit = resolveAnnotation(request);
        String key = keyResolver.resolve(request);

        String algorithm = (rateLimit != null && !rateLimit.algorithm().isEmpty())
                ? rateLimit.algorithm()
                : properties.getDefaultAlgorithm();
        long capacity = (rateLimit != null) ? rateLimit.capacity() : properties.getDefaultCapacity();
        long refillRate = (rateLimit != null) ? rateLimit.refillRate() : properties.getDefaultRefillRate();

        RateLimitInfo info;
        if ("SLIDING_WINDOW".equals(algorithm)) {
            info = storage.tryConsumeSlidingWindow(key, capacity, properties.getDefaultWindowSizeMillis());
        } else {
            info = storage.tryConsumeTokenBucket(key, capacity, refillRate);
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(info.remainingTokens()));

        if (!info.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(info.retryAfterMillis() / 1000 + 1));
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"error": "Too Many Requests", "retryAfterMs": %d}""".formatted(info.retryAfterMillis()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimit resolveAnnotation(HttpServletRequest request) {
        try {
            Object handler = handlerMapping.getHandler(request).getHandler();
            if (handler instanceof HandlerMethod handlerMethod) {
                return handlerMethod.getMethodAnnotation(RateLimit.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
```

- [ ] **Step 7: 커밋**

```bash
git add .
git commit -m "feat: add @RateLimit annotation, filter, and auto-configuration"
```

---

### Task 7: Demo 컨트롤러 + 통합 테스트

**Files:**
- Create: `src/main/java/jaemin/ai/ratelimiter/controller/DemoController.java`
- Create: `src/main/java/jaemin/ai/ratelimiter/controller/RateLimitStatsController.java`
- Create: `src/test/java/jaemin/ai/ratelimiter/filter/RateLimitFilterIntegrationTest.java`
- Create: `src/test/java/jaemin/ai/ratelimiter/controller/DemoControllerIntegrationTest.java`

- [ ] **Step 1: `DemoController.java` 작성**

```java
package jaemin.ai.ratelimiter.controller;

import jaemin.ai.ratelimiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/ping")
    @RateLimit(capacity = 5, refillRate = 2)
    public Map<String, Object> ping() {
        return Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/unlimited")
    public Map<String, String> unlimited() {
        return Map.of("message", "This endpoint has default rate limits");
    }

    @GetMapping("/strict")
    @RateLimit(capacity = 2, refillRate = 1, algorithm = "SLIDING_WINDOW")
    public Map<String, String> strict() {
        return Map.of("message", "This endpoint is strictly rate-limited");
    }
}
```

- [ ] **Step 2: `RateLimitStatsController.java` 작성**

```java
package jaemin.ai.ratelimiter.controller;

import jaemin.ai.ratelimiter.config.RateLimitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rate-limit")
public class RateLimitStatsController {

    private final RateLimitProperties properties;

    public RateLimitStatsController(RateLimitProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "defaultAlgorithm", properties.getDefaultAlgorithm(),
                "defaultCapacity", properties.getDefaultCapacity(),
                "defaultRefillRate", properties.getDefaultRefillRate(),
                "storageType", properties.getStorageType()
        );
    }
}
```

- [ ] **Step 3: 통합 테스트 작성**

```java
package jaemin.ai.ratelimiter.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.storage-type=IN_MEMORY",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ping 엔드포인트가 정상 응답한다")
    void pingReturns200() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("rate limit 초과 시 429를 반환한다")
    void returns429WhenLimitExceeded() throws Exception {
        // /api/ping은 capacity=5
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    @DisplayName("strict 엔드포인트는 2회 초과 시 429를 반환한다")
    void strictEndpointLimitsTo2() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/strict")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/strict"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("설정 조회 API가 정상 동작한다")
    void configEndpointWorks() throws Exception {
        mockMvc.perform(get("/api/rate-limit/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.defaultAlgorithm").value("TOKEN_BUCKET"));
    }
}
```

- [ ] **Step 4: 테스트 실행 - 성공 확인**

Run: `./gradlew test --tests "jaemin.ai.ratelimiter.controller.DemoControllerIntegrationTest" -i`
Expected: 4 tests PASSED

- [ ] **Step 5: 커밋**

```bash
git add .
git commit -m "feat: add demo controllers and integration tests"
```

---

### Task 8: Docker Compose 통합 및 Redis 모드 E2E 확인

**Files:**
- Modify: `src/main/resources/application.yml` (Redis 프로필 추가)
- Create: `src/main/resources/application-redis.yml`

- [ ] **Step 1: `application-redis.yml` 작성**

```yaml
rate-limit:
  storage-type: REDIS

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

- [ ] **Step 2: Docker Compose로 Redis 기동 후 앱 실행 테스트**

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=redis'
```

별도 터미널에서:
```bash
# 5회 요청 후 6번째에서 429 확인
for i in $(seq 1 6); do
  echo "Request $i:"
  curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080/api/ping
done
```

Expected: 처음 5회는 HTTP 200, 6번째는 HTTP 429

- [ ] **Step 3: 정리 및 커밋**

```bash
docker compose down
git add .
git commit -m "feat: add Redis profile and Docker Compose integration"
```

---

## Summary

| Task | 내용 | 면접 포인트 |
|------|------|------------|
| 1 | 프로젝트 스캐폴딩 | Spring Boot 4.x, Virtual Threads |
| 2 | Token Bucket 알고리즘 | 고전적 Rate Limiting, 시간 기반 리필 |
| 3 | Sliding Window Counter | 윈도우 기반 카운팅, 경계 조건 |
| 4 | Storage 추상화 | Strategy 패턴, 의존성 역전 |
| 5 | Redis 저장소 + Lua | 분산 환경, 원자적 연산, Lua scripting |
| 6 | Filter + 어노테이션 | 미들웨어 패턴, AOP, 설정 외부화 |
| 7 | Demo + 통합 테스트 | E2E 검증, MockMvc |
| 8 | Docker Compose + E2E | 인프라 구성, 프로필 기반 설정 |
