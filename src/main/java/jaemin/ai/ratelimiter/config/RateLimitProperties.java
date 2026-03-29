package jaemin.ai.ratelimiter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private String defaultAlgorithm = "TOKEN_BUCKET";
    private long defaultCapacity = 10;
    private long defaultRefillRate = 5;
    private long defaultWindowSizeMillis = 1000;
    private String storageType = "IN_MEMORY";
}
