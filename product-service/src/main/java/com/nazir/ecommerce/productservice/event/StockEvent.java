package com.nazir.ecommerce.productservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when product stock changes.
 * <p>
 * Why publish stock events?
 * order-service subscribes to know when stock is confirmed/released.
 * An analytics service can subscribe to track inventory levels over time.
 * Adding a new consumer = zero changes to product-service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private EventType eventType;
    private String productId;
    private String sku;
    private String orderId;
    private int quantity;
    private int remainingStock;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Builder.Default
    private String source = "product-service";

    public enum EventType {
        STOCK_RESERVED,      // quantity held for a pending order
        STOCK_RELEASED,      // reservation cancelled (order failed/cancelled)
        STOCK_CONFIRMED,     // permanently deducted (payment succeeded)
        STOCK_ADDED,         // new inventory added
        OUT_OF_STOCK         // stockQuantity reached 0
    }
}
