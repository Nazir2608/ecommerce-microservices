package com.nazir.ecommerce.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Order Service — Phase 3.
 * <p>
 * Key patterns:
 * OpenFeign   → calls product-service to validate stock
 * Resilience4j → circuit breaker + retry around Feign calls
 * Kafka pub    → ORDER_CREATED triggers payment-service (Saga start)
 * Kafka sub    → payment.events drives order confirm/cancel (Saga reaction)
 * Flyway       → manages orderdb schema (separate DB from userdb)
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
