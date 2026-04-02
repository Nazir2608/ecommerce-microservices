package com.nazir.ecommerce.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumed from topic: payment.events (published by payment-service)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
