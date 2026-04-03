package com.nazir.ecommerce.paymentservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment entity — MySQL.
 * <p>
 * LEARNING POINT — Why MySQL for payments?
 * InnoDB storage engine: full ACID compliance, row-level locking
 * Better suited than PostgreSQL for high write throughput (financial transactions)
 * Industry standard for payment processing databases
 * Flyway MySQL dialect support included
 * <p>
 * LEARNING POINT — Idempotency key (THE most important field here):
 * idempotencyKey = orderId (one payment attempt per order)
 * UNIQUE constraint at DB level — if payment-service crashes and Kafka
 * redelivers ORDER_CREATED, the second insert hits the UNIQUE constraint.
 * We catch this and return the existing payment → no double charge.
 * <p>
 * This is the IDEMPOTENCY PATTERN:
 * First call  → process payment → insert row → publish SUCCESS
 * Second call → UNIQUE violation → look up existing → return existing result
 * Customer is NEVER charged twice regardless of Kafka retry behavior.
 */
@Entity
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_payments_order_id", columnList = "order_id"),
                @Index(name = "idx_payments_user_id", columnList = "user_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, columnDefinition = "varchar(36)")
    private UUID id;

    /**
     * THE idempotency key — orderId ensures one payment per order.
     * DB UNIQUE constraint prevents double-processing on Kafka retry.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;    // = orderId.toString()

    @Column(name = "order_id", nullable = false, columnDefinition = "varchar(36)")
    private UUID orderId;

    @Column(name = "user_id", nullable = false, columnDefinition = "varchar(36)")
    private UUID userId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "varchar(20)")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30, columnDefinition = "varchar(30)")
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CARD;

    /**
     * Transaction ID returned by the payment gateway (Stripe/PayPal ref)
     */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "gateway_response", columnDefinition = "text")
    private String gatewayResponse;   // raw gateway response (truncated)

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // ── Domain methods ────────────────────────────────────────────────────

    public void markSuccess(String transactionId, String gatewayResponse) {
        this.status = PaymentStatus.SUCCESS;
        this.transactionId = transactionId;
        this.gatewayResponse = truncate(gatewayResponse, 1000);
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String reason, String gatewayResponse) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = truncate(reason, 500);
        this.gatewayResponse = truncate(gatewayResponse, 1000);
        this.processedAt = LocalDateTime.now();
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    // ── Enums ─────────────────────────────────────────────────────────────

    public enum PaymentStatus {
        PENDING,    // created, gateway call not yet made
        SUCCESS,    // gateway confirmed payment
        FAILED,     // gateway rejected or error
        REFUNDED    // payment reversed
    }

    public enum PaymentMethod {
        CARD,
        UPI,
        NET_BANKING,
        WALLET
    }
}
