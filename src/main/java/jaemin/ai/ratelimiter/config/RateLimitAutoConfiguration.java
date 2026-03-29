package jaemin.ai.ratelimiter.config;

import jaemin.ai.ratelimiter.filter.RateLimitFilter;
import jaemin.ai.ratelimiter.resolver.ClientKeyResolver;
import jaemin.ai.ratelimiter.storage.InMemoryRateLimitStorage;
import jaemin.ai.ratelimiter.storage.RateLimitStorage;
import jaemin.ai.ratelimiter.storage.RedisRateLimitStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "rate-limit.storage-type", havingValue = "IN_MEMORY", matchIfMissing = true)
    public RateLimitStorage inMemoryRateLimitStorage() {
        return new InMemoryRateLimitStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.storage-type", havingValue = "REDIS")
    public RateLimitStorage redisRateLimitStorage(StringRedisTemplate redisTemplate) {
        return new RedisRateLimitStorage(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitFilter rateLimitFilter(
            RateLimitStorage storage,
            RateLimitProperties properties,
            ClientKeyResolver keyResolver,
            RequestMappingHandlerMapping handlerMapping) {
        return new RateLimitFilter(storage, properties, keyResolver, handlerMapping);
    }
}
