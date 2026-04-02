package com.nazir.ecommerce.apigateway.config;

import com.nazir.ecommerce.apigateway.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route definitions — alternative to application.yml routes.
 * <p>
 * — Programmatic vs YAML routes:
 * YAML routes   → simple, readable, no code, hot-reloadable via Config Server
 * Java routes   → type-safe, conditional logic, can inject beans (like JwtFilter)
 * <p>
 * We define routes in BOTH places:
 * application.yml → rate limiter, circuit breaker (filter names are string-based)
 * GatewayConfig   → JWT filter (requires injecting the filter bean)
 * <p>
 * The routes defined here COMPLEMENT the YAML routes — not duplicated.
 * <p>
 * — Route priority and matching:
 * Routes are evaluated in ORDER. First match wins.
 * More specific paths (e.g., /api/v1/auth/**) should come BEFORE generic ones.
 * order(N) sets explicit priority — lower number = higher priority.
 * <p>
 * — lb:// URI scheme:
 * lb://user-service → Spring Cloud LoadBalancer resolves this via Eureka.
 * It finds all registered instances of "user-service" and load-balances (Round Robin).
 * Zero hardcoded host/port — services can move to different IPs freely.
 * <p>
 * L — Public vs Protected routes:
 * PUBLIC  → no JwtAuthenticationFilter → anyone can call
 * PROTECTED → JwtAuthenticationFilter applied → must have valid Bearer token
 * <p>
 * Public:    POST /api/v1/auth/register, POST /api/v1/auth/login
 * GET  /api/v1/products/**   (browsing catalog)
 * Protected: GET  /api/v1/users/me      (profile)
 * POST /api/v1/orders         (place order)
 * GET  /api/v1/payments/**    (payment history)
 */
@Configuration
public class GatewayConfig {

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

                // ── user-service: PUBLIC routes (no JWT required) ─────────────
                .route("user-service-public", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("user-service-cb")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .addResponseHeader("X-Gateway-Service", "user-service"))
                        .uri("lb://user-service"))

                // ── user-service: PROTECTED routes (JWT required) ─────────────
                .route("user-service-protected", r -> r
                        .path("/api/v1/users/**", "/api/v1/admin/users/**")
                        .filters(f -> f
                                .filter(jwtFilter.apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(c -> c
                                        .setName("user-service-cb")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .addResponseHeader("X-Gateway-Service", "user-service"))
                        .uri("lb://user-service"))

                // ── product-service: PUBLIC catalog (no JWT) ──────────────────
                .route("product-service-public", r -> r
                        .path("/api/v1/products/**")
                        .and().method("GET")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("product-service-cb")
                                        .setFallbackUri("forward:/fallback/product-service"))
                                .addResponseHeader("X-Gateway-Service", "product-service"))
                        .uri("lb://product-service"))

                // ── product-service: PROTECTED writes (JWT required) ──────────
                .route("product-service-protected", r -> r
                        .path("/api/v1/products/**")
                        .and().method("POST", "PUT", "DELETE", "PATCH")
                        .filters(f -> f
                                .filter(jwtFilter.apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(c -> c
                                        .setName("product-service-cb")
                                        .setFallbackUri("forward:/fallback/product-service"))
                                .addResponseHeader("X-Gateway-Service", "product-service"))
                        .uri("lb://product-service"))

                // ── order-service: ALL protected ──────────────────────────────
                .route("order-service", r -> r
                        .path("/api/v1/orders/**")
                        .filters(f -> f
                                .filter(jwtFilter.apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(c -> c
                                        .setName("order-service-cb")
                                        .setFallbackUri("forward:/fallback/order-service"))
                                .addResponseHeader("X-Gateway-Service", "order-service"))
                        .uri("lb://order-service"))

                // ── payment-service: ALL protected ────────────────────────────
                .route("payment-service", r -> r
                        .path("/api/v1/payments/**")
                        .filters(f -> f
                                .filter(jwtFilter.apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(c -> c
                                        .setName("payment-service-cb")
                                        .setFallbackUri("forward:/fallback/payment-service"))
                                .addResponseHeader("X-Gateway-Service", "payment-service"))
                        .uri("lb://payment-service"))

                // ── notification-service: internal monitoring only ─────────────
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("notification-service-cb")
                                        .setFallbackUri("forward:/fallback/notification-service"))
                                .addResponseHeader("X-Gateway-Service", "notification-service"))
                        .uri("lb://notification-service"))

                // ── Fallback routes (handled by FallbackController) ───────────
                .route("fallback", r -> r
                        .path("/fallback/**")
                        .uri("no://op"))

                .build();
    }
}
