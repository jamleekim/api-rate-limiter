package jaemin.ai.ratelimiter.controller;

import jaemin.ai.ratelimiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/ping")
    @RateLimit(capacity = 5, refillRate = 2)
    public Map<String, Object> ping() {
        return Map.of(
                "message", "pong",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/unlimited")
    public Map<String, String> unlimited() {
        return Map.of("message", "This endpoint has default rate limits");
    }

    @GetMapping("/strict")
    @RateLimit(capacity = 2, refillRate = 1, algorithm = "SLIDING_WINDOW")
    public Map<String, String> strict() {
        return Map.of("message", "This endpoint is strictly rate-limited");
    }
}
