package com.nazir.ecommerce.orderservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate root — PostgreSQL.
 * <p>
 *  State Machine pattern:
 * Business rules for status transitions live inside the entity, not in the service.
 * This is the "rich domain model" approach:
 * order.confirm("txn-123")  — validates PENDING state, sets CONFIRMED
 * order.cancel("reason")    — rejects if SHIPPED/DELIVERED
 * The service layer calls these methods and handles exceptions.
 * Invalid state = IllegalStateException at domain level (not in controller).
 * <p>
 *  @OneToMany cascade:
 * CascadeType.ALL → persisting/deleting Order cascades to OrderItems.
 * orphanRemoval   → removing item from the list deletes it from DB.
 * This makes Order the true "owner" of its items.
 */
@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_created", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "items")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;           // "ORD-20240315-A3F9"

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;             // snapshot — survives user account changes

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "notes", length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // ── Domain methods (state machine) ────────────────────────────────────

    public void confirm(String paymentId) {
        requireStatus(OrderStatus.PENDING, "confirm");
        this.status = OrderStatus.CONFIRMED;
        this.paymentId = paymentId;
        this.confirmedAt = LocalDateTime.now();
    }

    public void ship() {
        requireStatus(OrderStatus.CONFIRMED, "ship");
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = LocalDateTime.now();
    }

    public void deliver() {
        requireStatus(OrderStatus.SHIPPED, "deliver");
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot cancel order in status " + status + ". Only PENDING/CONFIRMED orders can be cancelled.");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    private void requireStatus(OrderStatus required, String op) {
        if (this.status != required) {
            throw new IllegalStateException(
                    "Cannot " + op + " order with status '" + status + "'. Required: " + required);
        }
    }

    // ── Status enum ───────────────────────────────────────────────────────

    public enum OrderStatus {
        PENDING,       // placed, waiting for payment
        CONFIRMED,     // payment successful
        SHIPPED,       // dispatched by warehouse
        DELIVERED,     // received by customer
        CANCELLED,     // cancelled before shipping
        REFUNDED       // payment reversed
    }

    // ── Embedded shipping address ─────────────────────────────────────────

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingAddress {
        @Column(name = "street_address", length = 255)
        private String streetAddress;
        @Column(name = "city", length = 100)
        private String city;
        @Column(name = "state", length = 100)
        private String state;
        @Column(name = "postal_code", length = 20)
        private String postalCode;
        @Column(name = "country", length = 100)
        private String country;
    }
}
