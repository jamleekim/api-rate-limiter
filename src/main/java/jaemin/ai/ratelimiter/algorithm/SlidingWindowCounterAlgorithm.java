package jaemin.ai.ratelimiter.algorithm;

import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SlidingWindowCounterAlgorithm implements RateLimitAlgorithm {

    private final long windowSizeMillis;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> windows = new ConcurrentHashMap<>();

    public SlidingWindowCounterAlgorithm(long windowSizeMillis) {
        this.windowSizeMillis = windowSizeMillis;
    }

    @Override
    public RateLimitInfo tryConsume(String key, long capacity, long rate) {
        ConcurrentLinkedDeque<Long> timestamps = windows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeMillis;

        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }

        long currentCount = timestamps.size();

        if (currentCount < capacity) {
            timestamps.addLast(now);
            long remaining = capacity - currentCount - 1;
            return RateLimitInfo.allowed(remaining);
        }

        long oldestTimestamp = timestamps.peekFirst();
        long retryAfter = oldestTimestamp + windowSizeMillis - now;
        return RateLimitInfo.rejected(Math.max(retryAfter, 1));
    }
}
