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
