package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * User Service — responsible for:
 *  - User registration and login (JWT-based)
 *  - Refresh token management (stored in Redis)
 *  - User profile CRUD
 *  - Role-based access control (RBAC)
 *  - Publishing domain events to Kafka (user.registered, user.updated)
 *
 * Database: PostgreSQL (relational data, strong ACID guarantees needed for user accounts)
 * Cache: Redis (refresh token blacklist, session data)
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
