package com.nazir.ecommerce.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Centralized CORS configuration.
 * <p>
 * Why configure CORS at the gateway?
 * Without gateway CORS: every service (user, product, order...) needs CORS config.
 * With gateway CORS: ONE place to update allowed origins.
 * <p>
 * When a browser makes a cross-origin request:
 * 1. Browser sends OPTIONS preflight: "Can I POST to this URL from localhost:3000?"
 * 2. Gateway responds with CORS headers: "Yes, these methods/headers are allowed"
 * 3. Browser sends the actual request
 * <p>
 * IMPORTANT: Downstream services must NOT add their own CORS headers.
 * If they do, the browser sees duplicate CORS headers → CORS error.
 * <p>
 * CorsWebFilter (reactive) vs CorsFilter (servlet):
 * Gateway uses WebFlux → must use CorsWebFilter (reactive version).
 *
 * @CrossOrigin or WebMvcConfigurer would not work here.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // In production: replace "*" with specific origins
        // e.g., "https://shop.nazir.com", "https://admin.nazir.com"
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-Auth-User-Id", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Gateway-Service", "X-Request-Id", "X-RateLimit-Remaining"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);  // preflight cache: 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
