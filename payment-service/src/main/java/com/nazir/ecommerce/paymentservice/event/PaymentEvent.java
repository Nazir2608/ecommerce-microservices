package com.nazir.ecommerce.paymentservice.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published TO payment.events — consumed by order-service and notification-service
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private EventType eventType;
    private UUID orderId;
    private UUID userId;
    private String userEmail;
    private BigDecimal amount;
    private String currency;
    private String transactionId;
    private String failureReason;
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
    @Builder.Default
    private String source = "payment-service";

    public enum EventType {PAYMENT_SUCCESS, PAYMENT_FAILED}
}
