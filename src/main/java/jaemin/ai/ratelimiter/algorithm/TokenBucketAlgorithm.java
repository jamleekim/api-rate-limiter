package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitInfo tryConsume(String key, long capacity, long rate) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        return bucket.tryConsume(capacity, rate);
    }

    private static class Bucket {
        private double tokens;
        private long lastRefillTimestamp;

        Bucket(long capacity) {
            this.tokens = capacity;
            this.lastRefillTimestamp = System.nanoTime();
        }

        synchronized RateLimitInfo tryConsume(long capacity, long rate) {
            refill(capacity, rate);
            if (tokens >= 1) {
                tokens -= 1;
                return RateLimitInfo.allowed((long) tokens);
            }
            long retryAfterMillis = (long) Math.ceil(1000.0 / rate);
            return RateLimitInfo.rejected(retryAfterMillis);
        }

        private void refill(long capacity, long rate) {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillTimestamp) / 1_000_000_000.0;
            double tokensToAdd = elapsed * rate;
            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
