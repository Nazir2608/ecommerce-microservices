package com.nazir.ecommerce.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway — Phase 6. Single entry point for all external traffic.
 *
 * LEARNING POINT — What the gateway does:
 *
 *   CLIENT → port 8080 (gateway) → routes to services
 *
 *   1. JWT Authentication Filter
 *      Validates Bearer token on every protected route.
 *      Extracts userId + email from claims → forwards as X-Auth-User-Id / X-Auth-User-Email headers.
 *      Downstream services TRUST these headers — they never re-validate the JWT.
 *      This is the "perimeter auth" pattern: validate once at the edge, propagate identity.
 *
 *   2. Per-IP Rate Limiting (Redis token bucket)
 *      Prevents abuse — each IP gets N requests/second before hitting 429.
 *      Redis stores the token bucket state (reactive Redis required for WebFlux).
 *
 *   3. Circuit Breaker Fallbacks
 *      If a downstream service is down, gateway returns a friendly 503 JSON response
 *      immediately — no waiting for timeout on every request.
 *
 *   4. Centralized CORS
 *      Single place to configure allowed origins, headers, methods.
 *      Downstream services have zero CORS config.
 *
 *   5. Request/Response Logging
 *      Every request logged with traceId, method, path, duration, status.
 *
 * LEARNING POINT — Why NOT @EnableDiscoveryClient sometimes skipped?
 *   Gateway uses lb:// URIs to resolve services via Eureka/Consul.
 *   @EnableDiscoveryClient tells Spring to register with and poll the registry.
 *   Without it: lb:// URIs fail (no registry to resolve from).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
