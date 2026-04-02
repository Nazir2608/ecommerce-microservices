package com.nazir.ecommerce.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiter key resolvers.
 *
 * LEARNING POINT — Redis Token Bucket algorithm:
 *   Each "key" (IP address here) gets a "bucket" of tokens in Redis.
 *   replenishRate = N tokens added per second (steady-state rate)
 *   burstCapacity = max tokens bucket can hold (allows brief bursts)
 *
 *   Example: replenishRate=20, burstCapacity=40
 *     Normal usage:   up to 20 req/s → always succeeds
 *     Burst:          up to 40 req/s briefly → succeeds while tokens remain
 *     Over limit:     429 Too Many Requests
 *     After slowing down: tokens refill → requests succeed again
 *
 * LEARNING POINT — KeyResolver options:
 *   Per IP      → fair for all users, prevents single IP from abusing
 *   Per user    → fairer, requires authenticated user (userId from JWT header)
 *   Per API key → for B2B APIs with quotas per customer
 *
 *   We provide both: ipKeyResolver (used on public routes)
 *                   userKeyResolver (for authenticated routes)
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Rate limit per IP address.
     * Used on public routes (product catalog, auth endpoints).
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * Rate limit per authenticated user (userId from JWT, forwarded by JwtAuthFilter).
     * Used on protected routes where JWT is already validated.
     * Falls back to IP if header not present.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-Auth-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            // Fallback to IP for unauthenticated requests
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
