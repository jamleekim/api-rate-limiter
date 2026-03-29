# API Rate Limiter

Spring Boot 4.0.4 기반의 분산 API Rate Limiter 구현체.
시스템 디자인 면접에서 자주 등장하는 Rate Limiting의 핵심 개념을 실제 코드로 구현한 프로젝트입니다.

## Tech Stack

- **Spring Boot 4.0.4** (Spring Framework 7 / Jakarta EE 11)
- **Java 25** (Virtual Threads 활성화)
- **Spring Data Redis** + Lua scripting
- **Gradle 9.3** (Kotlin DSL)
- **Testcontainers** (Redis 통합 테스트)
- **Docker Compose** (로컬 Redis 환경)

## Architecture

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────┐
│  RateLimitFilter (OncePerRequestFilter) │  ← @RateLimit 어노테이션 해석
│  ├─ ClientKeyResolver                  │  ← IP:METHOD:URI 기반 키 생성
│  └─ RateLimitStorage                   │  ← 알고리즘 실행 위임
│       ├─ InMemoryRateLimitStorage       │  ← 단일 서버 (ConcurrentHashMap)
│       └─ RedisRateLimitStorage          │  ← 분산 환경 (Lua Script)
│            ├─ token_bucket.lua          │
│            └─ sliding_window.lua        │
└─────────────────────────────────────────┘
     │
     ▼
  200 OK  or  429 Too Many Requests
  + X-RateLimit-Remaining 헤더
  + Retry-After 헤더
```

## 설계 의도

### 1. 두 가지 알고리즘 비교 구현

| | Token Bucket | Sliding Window Counter |
|---|---|---|
| **특징** | 버스트 트래픽 허용, 평균 속도 제한 | 정확한 윈도우 내 요청 수 제한 |
| **자료구조** | 토큰 수 + 마지막 리필 시각 | 타임스탬프 리스트 (Deque) |
| **시간 정밀도** | `System.nanoTime()` (monotonic clock) | `System.currentTimeMillis()` |
| **메모리** | 키당 고정 (double + long) | 키당 가변 (요청 수만큼 타임스탬프) |
| **면접 포인트** | 리필 공식, capacity와 rate의 관계 | 윈도우 경계 처리, 오래된 엔트리 정리 |

### 2. Storage 추상화 (Strategy 패턴)

```
RateLimitStorage (interface)
├── InMemoryRateLimitStorage   ← 단일 서버, 빠른 프로토타이핑
└── RedisRateLimitStorage      ← 분산 환경, 프로덕션
```

`@ConditionalOnProperty`로 설정값 하나만 바꾸면 저장소가 전환됩니다.
면접에서 자주 나오는 "단일 서버에서 다중 서버로 어떻게 확장할 것인가?"에 대한 실전적 답변입니다.

### 3. Redis Lua Script로 원자성 보장

분산 환경에서 rate limiting의 핵심 문제는 **read-decide-write** 사이의 race condition입니다.

```
Server A: GET tokens → 1 남음 → 허용 → SET tokens=0
Server B: GET tokens → 1 남음 → 허용 → SET tokens=0  ← 초과 허용!
```

Redis의 `EVAL` 명령으로 Lua 스크립트를 실행하면 단일 스레드에서 원자적으로 처리되어 이 문제를 해결합니다.

- **Token Bucket** (`token_bucket.lua`): Redis Hash(`HMGET`/`HMSET`)로 토큰 수와 마지막 리필 시각 관리
- **Sliding Window** (`sliding_window.lua`): Redis Sorted Set(`ZADD`/`ZREMRANGEBYSCORE`)으로 타임스탬프 관리, score를 시각으로 활용하여 O(log N) 범위 연산

### 4. 선언적 Rate Limiting (`@RateLimit` 어노테이션)

```java
@GetMapping("/ping")
@RateLimit(capacity = 5, refillRate = 2)
public Map<String, Object> ping() { ... }

@GetMapping("/strict")
@RateLimit(capacity = 2, refillRate = 1, algorithm = "SLIDING_WINDOW")
public Map<String, String> strict() { ... }
```

`RateLimitFilter`가 `RequestMappingHandlerMapping`을 통해 핸들러 메서드의 어노테이션을 런타임에 해석합니다.
어노테이션이 없는 엔드포인트에는 `application.yml`의 기본값이 적용됩니다.

## Project Structure

```
src/main/java/jaemin/ai/ratelimiter/
├── algorithm/
│   ├── RateLimitAlgorithm.java              # 알고리즘 인터페이스
│   ├── TokenBucketAlgorithm.java            # Token Bucket (ConcurrentHashMap + synchronized)
│   └── SlidingWindowCounterAlgorithm.java   # Sliding Window (ConcurrentLinkedDeque)
├── storage/
│   ├── RateLimitStorage.java                # 저장소 인터페이스
│   ├── InMemoryRateLimitStorage.java        # 인메모리 구현
│   └── RedisRateLimitStorage.java           # Redis + Lua 구현
├── filter/
│   └── RateLimitFilter.java                 # Servlet Filter (미들웨어)
├── annotation/
│   └── RateLimit.java                       # 커스텀 어노테이션
├── resolver/
│   └── ClientKeyResolver.java               # IP:METHOD:URI 키 생성
├── config/
│   ├── RateLimitProperties.java             # 외부 설정 바인딩
│   └── RateLimitAutoConfiguration.java      # 조건부 빈 등록
├── controller/
│   ├── DemoController.java                  # 데모 API 엔드포인트
│   └── RateLimitStatsController.java        # 설정 조회 API
├── dto/
│   └── RateLimitInfo.java                   # 응답 DTO (Java record)
└── exception/
    └── RateLimitExceededException.java      # 429 예외

src/main/resources/
├── scripts/
│   ├── token_bucket.lua                     # Redis Token Bucket 스크립트
│   └── sliding_window.lua                   # Redis Sliding Window 스크립트
├── application.yml                          # 기본 설정 (IN_MEMORY)
└── application-redis.yml                    # Redis 프로필

src/test/java/jaemin/ai/ratelimiter/
├── algorithm/
│   ├── TokenBucketAlgorithmTest.java        # 리필, 용량 초과, 키 독립성
│   └── SlidingWindowCounterAlgorithmTest.java
├── storage/
│   ├── InMemoryRateLimitStorageTest.java
│   └── RedisRateLimitStorageTest.java       # Testcontainers + Redis
└── controller/
    └── DemoControllerIntegrationTest.java   # MockMvc E2E
```

## Quick Start

### 인메모리 모드 (기본)

```bash
./gradlew bootRun
```

### Redis 분산 모드

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=redis'
```

### API 테스트

```bash
# 정상 응답 (capacity=5, Token Bucket)
curl -s http://localhost:8080/api/ping | jq
# {"message":"pong","timestamp":"2026-03-29T..."}

# 5회 초과 시 429 응답
for i in $(seq 1 6); do
  echo "Request $i: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/ping)"
done
# Request 1: 200
# ...
# Request 5: 200
# Request 6: 429

# 엄격한 제한 (capacity=2, Sliding Window)
curl -s http://localhost:8080/api/strict | jq

# 현재 설정 확인
curl -s http://localhost:8080/api/rate-limit/config | jq
```

### 테스트 실행

```bash
# 전체 테스트 (Redis 테스트는 Docker 필요)
./gradlew test

# 알고리즘 단위 테스트만
./gradlew test --tests "*.algorithm.*"
```

## Configuration

`application.yml`에서 기본값을 설정합니다:

```yaml
rate-limit:
  enabled: true                    # Rate Limiting 활성화 여부
  default-algorithm: TOKEN_BUCKET  # TOKEN_BUCKET 또는 SLIDING_WINDOW
  default-capacity: 10             # 기본 버킷 크기 / 윈도우당 허용 수
  default-refill-rate: 5           # Token Bucket 초당 리필 속도
  storage-type: IN_MEMORY          # IN_MEMORY 또는 REDIS
```

## 면접에서 논의할 수 있는 확장 포인트

- **사용자별 차등 제한**: `ClientKeyResolver`를 확장하여 API Key, 사용자 등급별로 다른 정책 적용
- **Redis Cluster**: 현재 Lua 스크립트는 단일 키 연산이므로 Redis Cluster에서도 동작 (같은 슬롯)
- **Rate Limit 헤더 표준화**: `X-RateLimit-Limit`, `X-RateLimit-Reset` 등 IETF 초안 표준 헤더 추가
- **Circuit Breaker 연계**: 특정 클라이언트가 지속적으로 429를 받으면 일정 시간 완전 차단
- **모니터링**: Spring Actuator + Micrometer로 rate limit hit/miss 메트릭 수집
