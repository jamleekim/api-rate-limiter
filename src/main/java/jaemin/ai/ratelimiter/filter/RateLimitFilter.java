package jaemin.ai.ratelimiter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jaemin.ai.ratelimiter.annotation.RateLimit;
import jaemin.ai.ratelimiter.config.RateLimitProperties;
import jaemin.ai.ratelimiter.dto.RateLimitInfo;
import jaemin.ai.ratelimiter.resolver.ClientKeyResolver;
import jaemin.ai.ratelimiter.storage.RateLimitStorage;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStorage storage;
    private final RateLimitProperties properties;
    private final ClientKeyResolver keyResolver;
    private final RequestMappingHandlerMapping handlerMapping;

    public RateLimitFilter(
            RateLimitStorage storage,
            RateLimitProperties properties,
            ClientKeyResolver keyResolver,
            RequestMappingHandlerMapping handlerMapping) {
        this.storage = storage;
        this.properties = properties;
        this.keyResolver = keyResolver;
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimit rateLimit = resolveAnnotation(request);
        String key = keyResolver.resolve(request);

        String algorithm = (rateLimit != null && !rateLimit.algorithm().isEmpty())
                ? rateLimit.algorithm()
                : properties.getDefaultAlgorithm();
        long capacity = (rateLimit != null) ? rateLimit.capacity() : properties.getDefaultCapacity();
        long refillRate = (rateLimit != null) ? rateLimit.refillRate() : properties.getDefaultRefillRate();

        RateLimitInfo info;
        if ("SLIDING_WINDOW".equals(algorithm)) {
            info = storage.tryConsumeSlidingWindow(key, capacity, properties.getDefaultWindowSizeMillis());
        } else {
            info = storage.tryConsumeTokenBucket(key, capacity, refillRate);
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(info.remainingTokens()));

        if (!info.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(info.retryAfterMillis() / 1000 + 1));
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"error": "Too Many Requests", "retryAfterMs": %d}""".formatted(info.retryAfterMillis()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimit resolveAnnotation(HttpServletRequest request) {
        try {
            Object handler = handlerMapping.getHandler(request).getHandler();
            if (handler instanceof HandlerMethod handlerMethod) {
                return handlerMethod.getMethodAnnotation(RateLimit.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
