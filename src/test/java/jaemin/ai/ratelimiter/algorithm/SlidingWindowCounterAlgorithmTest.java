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
