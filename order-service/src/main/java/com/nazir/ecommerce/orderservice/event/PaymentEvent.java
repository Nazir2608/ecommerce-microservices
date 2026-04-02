package com.nazir.ecommerce.orderservice.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumed FROM Kafka topic: payment.events
 * Published BY: payment-service
 * <p>
 * At-least-once delivery:
 * Kafka guarantees at-least-once delivery (same message may arrive twice).
 * The @KafkaListener in OrderServiceImpl must be IDEMPOTENT:
 * check if order is already CONFIRMED before confirming again.
 * This prevents double stock deductions if the same payment event arrives twice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {
    private String eventId;
    private EventType eventType;
    private UUID orderId;
    private UUID userId;
    private String userEmail;
    private BigDecimal amount;
    private String currency;
    private String transactionId;
    private String failureReason;
    private LocalDateTime occurredAt;

    public enum EventType {
        PAYMENT_SUCCESS,
        PAYMENT_FAILED
    }
}
