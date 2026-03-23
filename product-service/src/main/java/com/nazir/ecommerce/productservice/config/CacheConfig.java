package com.nazir.ecommerce.productservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache configuration — TTL per cache name.
 *
 * LEARNING POINT — Why different TTLs?
 *   products        (single) → 10 min  — individual product pages, frequently read
 *   products_by_sku          → 10 min  — same data, different key
 *   products_list            →  5 min  — list pages change when new products added
 *
 *   Stock is NOT cached — it changes on every order.
 *   Too-long TTL → stale data. Too-short TTL → too many cache misses.
 *
 * LEARNING POINT — JSON serializer vs JDK serializer:
 *   Default Spring Cache uses JDK serialization (binary, not human-readable).
 *   We use GenericJackson2JsonRedisSerializer → human-readable JSON in Redis.
 *   You can inspect cached values with: redis-cli GET "products::abc123"
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configs = Map.of(
                "products",        defaults.entryTtl(Duration.ofMinutes(10)),
                "products_by_sku", defaults.entryTtl(Duration.ofMinutes(10)),
                "products_list",   defaults.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
