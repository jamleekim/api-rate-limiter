package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;

public interface RateLimitAlgorithm {
    RateLimitInfo tryConsume(String key, long capacity, long rate);
}
