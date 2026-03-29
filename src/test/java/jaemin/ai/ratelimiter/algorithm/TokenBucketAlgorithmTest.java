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

        Thread.sleep(550);

        RateLimitInfo info = algorithm.tryConsume("client-3", 10, 10);
        assertThat(info.allowed()).isTrue();
        assertThat(info.remainingTokens()).isGreaterThanOrEqualTo(3);
    }
}
