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
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Redis Token Bucket: allows request when tokens available")
    void tokenBucketAllows() {
        RateLimitInfo info = storage.tryConsumeTokenBucket("r-key1", 10, 5);
        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(9);
    }

    @Test
    @DisplayName("Redis Token Bucket: rejects when capacity exhausted")
    void tokenBucketRejects() {
        for (int i = 0; i < 10; i++) {
            storage.tryConsumeTokenBucket("r-key2", 10, 5);
        }
        RateLimitInfo info = storage.tryConsumeTokenBucket("r-key2", 10, 5);
        assertThat(info.allowed()).isFalse();
    }

    @Test
    @DisplayName("Redis Sliding Window: allows request within capacity")
    void slidingWindowAllows() {
        RateLimitInfo info = storage.tryConsumeSlidingWindow("r-key3", 5, 1000);
        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isEqualTo(4);
    }

    @Test
    @DisplayName("Redis Sliding Window: rejects when limit exceeded")
    void slidingWindowRejects() {
        for (int i = 0; i < 5; i++) {
            storage.tryConsumeSlidingWindow("r-key4", 5, 1000);
        }
        RateLimitInfo info = storage.tryConsumeSlidingWindow("r-key4", 5, 1000);
        assertThat(info.allowed()).isFalse();
    }
}
