package com.nazir.ecommerce.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Payment Service — Phase 4.
 * <p>
 * Key patterns:
 * Kafka consumer   → listens to order.events (ORDER_CREATED)
 * Idempotency key  → orderId as unique key — prevents double-charging on retry
 * Payment gateway  → abstracted behind interface (swap Stripe ↔ PayPal freely)
 * Kafka producer   → publishes to payment.events (SUCCESS or FAILED)
 * MySQL            → ACID for financial records, UNIQUE on idempotency_key
 * <p>
 * LEARNING POINT — Why event-driven for payments?
 * Synchronous: order-service calls payment-service via HTTP
 * Problem: if payment-service is down, order fails immediately
 * Problem: if order-service crashes after calling payment but before saving,
 * customer is charged but order is never created
 * <p>
 * Event-driven: order-service publishes ORDER_CREATED to Kafka
 * payment-service picks it up when ready (no tight coupling)
 * Kafka persists the event — no data loss even if service restarts
 * payment-service processes exactly once via idempotency key
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
