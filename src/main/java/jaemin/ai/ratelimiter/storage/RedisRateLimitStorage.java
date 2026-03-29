package jaemin.ai.ratelimiter.storage;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

public class RedisRateLimitStorage implements RateLimitStorage {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tokenBucketScript;
    private final RedisScript<List> slidingWindowScript;

    public RedisRateLimitStorage(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
        this.slidingWindowScript = RedisScript.of(new ClassPathResource("scripts/sliding_window.lua"), List.class);
    }

    @Override
    public RateLimitInfo tryConsumeTokenBucket(String key, long capacity, long refillRate) {
        String now = String.valueOf(System.currentTimeMillis());
        List result = redisTemplate.execute(
                tokenBucketScript,
                List.of("rl:tb:" + key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                now
        );
        return parseResult(result);
    }

    @Override
    public RateLimitInfo tryConsumeSlidingWindow(String key, long capacity, long windowSizeMillis) {
        String now = String.valueOf(System.currentTimeMillis());
        List result = redisTemplate.execute(
                slidingWindowScript,
                List.of("rl:sw:" + key),
                String.valueOf(capacity),
                String.valueOf(windowSizeMillis),
                now
        );
        return parseResult(result);
    }

    @SuppressWarnings("unchecked")
    private RateLimitInfo parseResult(List result) {
        long allowed = ((Number) result.get(0)).longValue();
        long remaining = ((Number) result.get(1)).longValue();
        long retryAfter = ((Number) result.get(2)).longValue();

        if (allowed == 1) {
            return RateLimitInfo.allowed(remaining);
        }
        return RateLimitInfo.rejected(retryAfter);
    }
}
