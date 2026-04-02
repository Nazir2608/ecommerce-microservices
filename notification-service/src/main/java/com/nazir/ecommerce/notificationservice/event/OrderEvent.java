package com.nazir.ecommerce.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumed from topic: order.events (published by order-service)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {
    private String eventId;
    private EventType eventType;
    private UUID orderId;
    private String orderNumber;
    private UUID userId;
    private String userEmail;
    private BigDecimal totalAmount;
    private String currency;
    private String reason;
    private LocalDateTime occurredAt;

    public enum EventType {
        ORDER_CREATED,
        ORDER_CONFIRMED,
        ORDER_SHIPPED,
        ORDER_DELIVERED,
        ORDER_CANCELLED
    }
}
