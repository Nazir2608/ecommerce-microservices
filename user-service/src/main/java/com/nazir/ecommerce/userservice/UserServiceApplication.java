package com.nazir.ecommerce.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * User Service — entry point.
 *
 * Responsibilities:
 *   • User registration with email-uniqueness validation
 *   • JWT-based authentication (access + refresh tokens)
 *   • Refresh token rotation stored in Redis
 *   • Token blacklisting on logout
 *   • User profile CRUD (GET, PATCH, DELETE)
 *   • Role-based access control (CUSTOMER, ADMIN, SELLER)
 *   • Publishing domain events to Kafka on register / update / delete
 *
 * Tech stack:
 *   • Database   → PostgreSQL (via Spring Data JPA + Flyway migrations)
 *   • Cache      → Redis (refresh tokens, token blacklist)
 *   • Messaging  → Kafka (domain event publishing)
 *   • Discovery  → Eureka (registers itself, discovered by API Gateway)
 *   • Config     → Spring Cloud Config Server
 *   • Security   → Spring Security 6 + JWT (JJWT 0.12.x)
 *   • Docs       → SpringDoc OpenAPI (Swagger UI at /swagger-ui.html)
 */
@SpringBootApplication
@EnableDiscoveryClient   // registers this instance in Eureka
@EnableKafka             // activates Kafka listener + producer infrastructure
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
