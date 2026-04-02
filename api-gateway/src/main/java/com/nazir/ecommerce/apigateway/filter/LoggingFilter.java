package com.nazir.ecommerce.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global logging filter — applied to ALL routes automatically.
 *
 * LEARNING POINT — GlobalFilter vs GatewayFilter:
 *   GlobalFilter runs on every request without any route configuration.
 *   Use it for cross-cutting concerns: logging, tracing, metrics.
 *
 * LEARNING POINT — Ordered.HIGHEST_PRECEDENCE + 1:
 *   Filters run in order. Lower number = runs first.
 *   We want logging to wrap everything → run first (HIGHEST_PRECEDENCE = first in)
 *   and last (logged after response is done via doFinally/then).
 *
 * LEARNING POINT — Mono.fromRunnable + then():
 *   chain.filter() returns a Mono that completes when the downstream response is done.
 *   .then(Mono.fromRunnable(...)) executes AFTER the response completes.
 *   This is how we measure response time in a reactive (non-blocking) filter.
 */
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest  request  = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // Attach a unique request ID for distributed tracing correlation
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long   startMs   = System.currentTimeMillis();

        ServerHttpRequest mutated = request.mutate()
                .header("X-Request-Id", requestId)
                .build();

        log.info("[Gateway] → {} {} [requestId={}]",
                request.getMethod(), request.getPath(), requestId);

        return chain.filter(exchange.mutate().request(mutated).build())
                .then(Mono.fromRunnable(() -> {
                    long durationMs = System.currentTimeMillis() - startMs;
                    log.info("[Gateway] ← {} {} {} {}ms [requestId={}]",
                            request.getMethod(), request.getPath(),
                            response.getStatusCode(), durationMs, requestId);
                }));
    }
}
