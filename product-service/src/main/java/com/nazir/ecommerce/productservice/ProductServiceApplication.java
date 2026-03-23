package com.nazir.ecommerce.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Product Service — entry point.
 *
 * Responsibilities:
 *   • Product CRUD (create, read, update, soft-delete)
 *   • Flexible product attributes via Map<String, Object>
 *   • Paginated catalog browsing with category filtering
 *   • Full-text search across name, description, brand
 *   • Stock reservation / release / confirm lifecycle
 *   • Redis Cache-Aside pattern for hot product data
 *   • Kafka event publishing for stock changes
 *
 * Tech stack:
 *   • Database → MongoDB (schema-less, flexible attributes)
 *   • Cache    → Redis   (@Cacheable / @CachePut / @CacheEvict)
 *   • Events   → Kafka   (stock.events topic)
 *   • Docs     → SpringDoc OpenAPI at /swagger-ui.html
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableKafka
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
