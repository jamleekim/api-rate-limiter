package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.algorithm.SlidingWindowCounterAlgorithm;
import jaemin.ai.ratelimiter.algorithm.TokenBucketAlgorithm;
import jaemin.ai.ratelimiter.dto.RateLimitInfo;

public class InMemoryRateLimitStorage implements RateLimitStorage {

    private final TokenBucketAlgorithm tokenBucket = new TokenBucketAlgorithm();
    private final SlidingWindowCounterAlgorithm slidingWindow = new SlidingWindowCounterAlgorithm(1000);

    @Override
    public RateLimitInfo tryConsumeTokenBucket(String key, long capacity, long refillRate) {
        return tokenBucket.tryConsume(key, capacity, refillRate);
    }

    @Override
    public RateLimitInfo tryConsumeSlidingWindow(String key, long capacity, long windowSizeMillis) {
        String compositeKey = key + ":w" + windowSizeMillis;
        return slidingWindow.tryConsume(compositeKey, capacity, capacity);
    }
}
