package jaemin.ai.ratelimiter.controller;

import jaemin.ai.ratelimiter.config.RateLimitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rate-limit")
public class RateLimitStatsController {

    private final RateLimitProperties properties;

    public RateLimitStatsController(RateLimitProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "defaultAlgorithm", properties.getDefaultAlgorithm(),
                "defaultCapacity", properties.getDefaultCapacity(),
                "defaultRefillRate", properties.getDefaultRefillRate(),
                "storageType", properties.getStorageType()
        );
    }
}
