package com.nazir.ecommerce.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Notification Service — Phase 5.
 * <p>
 * Architecture: Fan-in Kafka consumer + stateless email sender.
 * <p>
 * Fan-in pattern:
 * Three Kafka topics flow INTO one service:
 * user.events    → welcome email, account alerts
 * order.events   → order confirmed, shipped, delivered, cancelled
 * payment.events → payment failed notice
 * <p>
 * Adding a new notification type = add a new @KafkaListener method.
 * Zero changes to the publishing services (user, order, payment).
 * This is the Open/Closed principle at the architecture level.
 * <p>
 * Why stateless (no database)?
 * Emails don't need a DB. The domain state lives in other services.
 * notification-service only needs to:
 * 1. Check Redis: was this event already processed? (dedup key, 24h TTL)
 * 2. If not: render HTML template, send email, mark Redis key
 * Redis IS used but only transiently — it's not the system of record.
 * This makes horizontal scaling trivial: spin up 10 instances, each
 * independently handles Kafka partitions, Redis prevents duplicate emails.
 * <p>
 * Why Redis dedup?
 * Kafka at-least-once delivery means the same event may arrive twice.
 * Without dedup: customer gets two "Your order is confirmed" emails.
 * With Redis setIfAbsent(key, TTL): first call sets the key (processes),
 * second call finds the key already set (skips). Idempotent.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
