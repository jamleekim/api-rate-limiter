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
