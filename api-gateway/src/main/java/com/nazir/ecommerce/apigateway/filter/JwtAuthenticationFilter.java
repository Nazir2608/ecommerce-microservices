package com.nazir.ecommerce.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Authentication GatewayFilter.
 * <p>
 * ══════════════════════════════════════════════════════════════════════════
 * — GatewayFilter vs GlobalFilter:
 * <p>
 * GlobalFilter  → applied to ALL routes automatically (LoggingFilter uses this)
 * GatewayFilter → applied to specific routes via configuration (JwtAuthenticationFilter)
 * <p>
 * We use GatewayFilter so public routes (login, register, product catalog)
 * bypass JWT validation while protected routes (orders, user profile) enforce it.
 * <p>
 * ══════════════════════════════════════════════════════════════════════════
 * — Why validate JWT at the gateway (not in each service)?
 * <p>
 * Without gateway auth:
 * Every service must import JWT library, duplicate validation logic,
 * manage the same secret key — 5 services × 50 lines = 250 lines of duplication.
 * <p>
 * With gateway auth:
 * JWT validated ONCE here.
 * Gateway extracts userId + email → adds X-Auth-User-Id and X-Auth-User-Email headers.
 * Services read those trusted headers — zero JWT code in downstream services.
 * <p>
 * Security assumption:
 * Services must be on an internal network (not accessible from internet directly).
 * If a client bypasses the gateway and hits a service directly, they could
 * forge X-Auth-User-Id. In production: use mTLS or a service mesh for this layer.
 * <p>
 * ══════════════════════════════════════════════════════════════════════════
 * — Reactive filter (Mono<Void>):
 * Gateway is built on Project Reactor (non-blocking).
 * Filters return Mono<Void> — they chain asynchronously.
 * chain.filter(request) = "pass this request to the next filter in the chain".
 * exchange.getResponse().setComplete() = "stop here, send this response".
 */
@Component
@Slf4j
public class JwtAuthenticationFilter
        extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // ── Step 1: Extract Bearer token ──────────────────────────────
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[Gateway] Missing/invalid Authorization header: {} {}",
                        request.getMethod(), request.getPath());
                return unauthorized(exchange, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);

            // ── Step 2: Validate token ─────────────────────────────────────
            Claims claims;
            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("[Gateway] Invalid JWT: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                return unauthorized(exchange, "Invalid or expired token");
            }

            // ── Step 3: Extract identity claims ───────────────────────────
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String roles = claims.get("roles", String.class);

            if (userId == null || userId.isBlank()) {
                return unauthorized(exchange, "Token missing subject (userId)");
            }

            // ── Step 4: Forward identity as headers to downstream ─────────
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-Auth-User-Id", userId)
                    .header("X-Auth-User-Email", email != null ? email : "")
                    .header("X-Auth-User-Roles", roles != null ? roles : "")
                    .build();

            log.debug("[Gateway] Authenticated userId={} path={}", userId, request.getPath());

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> unauthorized(
            org.springframework.web.server.ServerWebExchange exchange, String message) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"success\":false,\"message\":\"" + message + "\",\"status\":401}";
        org.springframework.core.io.buffer.DataBuffer buffer =
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
        // No config properties needed for this filter
    }
}
