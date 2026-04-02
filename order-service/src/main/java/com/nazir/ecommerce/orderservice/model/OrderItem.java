package com.nazir.ecommerce.orderservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Price snapshot:
 * productName and unitPrice are copied from product-service AT ORDER TIME.
 * They never change after the order is placed.
 * If the seller updates price tomorrow, this order still shows what was charged.
 * productId is only kept for stock operations (reserve/release/confirm).
 */
@Entity
@Table(name = "order_items",
        indexes = @Index(name = "idx_order_items_order", columnList = "order_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, length = 36)
    private UUID orderId;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;    // snapshot

    @Column(name = "product_sku", length = 100)
    private String productSku;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;  // snapshot of price at order time

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice; // quantity × unitPrice
}
