package com.nazir.ecommerce.orderservice.config;

import feign.Logger;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign client global configuration.
 *
 * LEARNING POINT — Feign timeouts:
 *   connectTimeout → how long to wait to establish a TCP connection
 *   readTimeout    → how long to wait for the server to return a response
 *   Set these lower than Resilience4j timeLimiter to avoid timeout conflicts.
 *
 * LEARNING POINT — Feign log level:
 *   NONE    → nothing (production)
 *   BASIC   → request URL, response code, duration
 *   HEADERS → BASIC + request/response headers
 *   FULL    → HEADERS + body (only for debugging — logs sensitive data!)
 */
@Configuration
public class FeignConfig {

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                2, TimeUnit.SECONDS,   // connect timeout
                5, TimeUnit.SECONDS,   // read timeout
                true                   // follow redirects
        );
    }

    @Bean
    public Logger.Level feignLogLevel() {
        return Logger.Level.BASIC;  // log request + response code only
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        // Map product-service HTTP errors to appropriate exceptions
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new com.nazir.ecommerce.orderservice.exception.ProductUnavailableException(
                    "Product not found (product-service returned 404)");
            }
            if (response.status() == 409) {
                return new com.nazir.ecommerce.orderservice.exception.ProductUnavailableException(
                    "Insufficient stock (product-service returned 409)");
            }
            return new RuntimeException("product-service error: HTTP " + response.status());
        };
    }
}
