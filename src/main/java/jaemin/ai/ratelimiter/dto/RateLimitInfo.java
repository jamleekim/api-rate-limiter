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
