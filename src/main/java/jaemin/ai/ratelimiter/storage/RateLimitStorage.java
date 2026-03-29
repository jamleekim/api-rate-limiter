package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;

public interface RateLimitStorage {
    RateLimitInfo tryConsumeTokenBucket(String key, long capacity, long refillRate);
    RateLimitInfo tryConsumeSlidingWindow(String key, long capacity, long windowSizeMillis);
}
