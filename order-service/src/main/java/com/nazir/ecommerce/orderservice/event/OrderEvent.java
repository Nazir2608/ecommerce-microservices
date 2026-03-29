package com.nazir.ecommerce.orderservice.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published to Kafka topic: order.events
 * Consumed by: payment-service, notification-service
 *
 * LEARNING POINT — Choreography Saga flow:
 *
 *  1. order-service  → Kafka: ORDER_CREATED
 *  2. payment-service ← Kafka: ORDER_CREATED  (starts processing payment)
 *  3. payment-service → Kafka: PAYMENT_SUCCESS or PAYMENT_FAILED
 *  4. order-service  ← Kafka: PAYMENT_SUCCESS → confirm order + confirm stock
 *  4. order-service  ← Kafka: PAYMENT_FAILED  → cancel order + release stock
 *  5. notification-service ← Kafka: ORDER_CONFIRMED or ORDER_CANCELLED
 *
 *  No central coordinator needed. Each service reacts to events autonomously.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderEvent {
    @Builder.Default private String eventId = UUID.randomUUID().toString();
    private EventType  eventType;
    private UUID       orderId;
    private String     orderNumber;
    private UUID       userId;
    private String     userEmail;
    private BigDecimal totalAmount;
    private String     currency;
    private String     reason;
    @Builder.Default private LocalDateTime occurredAt = LocalDateTime.now();
    @Builder.Default private String source = "order-service";

    public enum EventType {
        ORDER_CREATED,    // → triggers payment-service to charge the customer
        ORDER_CONFIRMED,  // → triggers notification-service (order confirmed email)
        ORDER_SHIPPED,    // → triggers notification-service (shipping email)
        ORDER_DELIVERED,  // → triggers notification-service
        ORDER_CANCELLED   // → triggers stock release + notification
    }
}
