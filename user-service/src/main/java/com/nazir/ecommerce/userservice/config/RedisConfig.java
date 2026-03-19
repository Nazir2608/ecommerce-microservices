package com.nazir.ecommerce.userservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — What we store in Redis (user-service)                  │
 * │                                                                          │
 * │  Key pattern               │ Value        │ TTL                          │
 * │  ─────────────────────────────────────────────────────                   │
 * │  refresh_token:{userId}    │ token string │ 7 days                      │
 * │  blacklist:{accessToken}   │ "1"          │ remaining token TTL          │
 * │                                                                          │
 * │  Why Redis and not the DB?                                               │
 * │  • TTL support — Redis auto-deletes expired keys. No cleanup cron.       │
 * │  • In-memory — O(1) lookup, <1ms latency. Perfect for per-request check. │
 * │  • Atomic setIfAbsent — prevents race conditions on duplicate events.    │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — StringRedisTemplate vs RedisTemplate                   │
 * │                                                                          │
 * │  StringRedisTemplate: keys and values are always plain strings.          │
 * │    → Use for simple string data (tokens, flags, counters).               │
 * │                                                                          │
 * │  RedisTemplate<String, Object>: values serialized as JSON.               │
 * │    → Use for complex objects (DTOs, maps).                               │
 * │                                                                          │
 * │  We use StringRedisTemplate for token storage (tokens are strings).      │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate — pre-configured for String key + String value.
     * Spring Boot auto-configures this if a RedisConnectionFactory bean exists.
     * We declare it explicitly to be intentional and clear.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Generic RedisTemplate for storing complex objects as JSON.
     * Uses Jackson for serialization with Java 8 date/time support.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key serializer — always String (readable in Redis CLI)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value serializer — Jackson JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());                        // LocalDateTime support
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);    // ISO-8601 format

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
