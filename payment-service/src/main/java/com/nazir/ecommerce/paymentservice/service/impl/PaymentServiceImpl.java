package com.nazir.ecommerce.paymentservice.service.impl;

import com.nazir.ecommerce.paymentservice.dto.request.RefundRequest;
import com.nazir.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.nazir.ecommerce.paymentservice.event.OrderEvent;
import com.nazir.ecommerce.paymentservice.event.PaymentEventPublisher;
import com.nazir.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.nazir.ecommerce.paymentservice.gateway.PaymentGateway;
import com.nazir.ecommerce.paymentservice.gateway.PaymentGatewayResult;
import com.nazir.ecommerce.paymentservice.mapper.PaymentMapper;
import com.nazir.ecommerce.paymentservice.model.Payment;
import com.nazir.ecommerce.paymentservice.repository.PaymentRepository;
import com.nazir.ecommerce.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Payment service implementation.
 * <p>
 * ═══════════════════════════════════════════════════════════════════════════
 * IDEMPOTENCY IMPLEMENTATION (the core of Phase 4):
 * ═══════════════════════════════════════════════════════════════════════════
 * <p>
 * processOrderPayment() is called by @KafkaListener — Kafka may deliver
 * the same ORDER_CREATED message more than once (at-least-once guarantee).
 * <p>
 * Without idempotency:
 * 1st delivery → charge customer $99 → SUCCESS
 * 2nd delivery → charge customer $99 → SUCCESS (double charge!)
 * <p>
 * With idempotency key (orderId as UNIQUE key in DB):
 * 1st delivery → no existing record → charge → insert row → publish SUCCESS
 * 2nd delivery → findByIdempotencyKey(orderId) → found! → return existing result
 * Customer is NEVER double-charged.
 * <p>
 * Alternative (fail-fast): try INSERT first → catch UNIQUE violation → look up existing.
 * We use the check-first approach for clarity.
 * <p>
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * @Transactional behavior:
 * ═══════════════════════════════════════════════════════════════════════════
 * DB write (payment row) + Kafka publish are NOT in the same transaction.
 * If Kafka publish fails after DB commit → order-service never hears result.
 * Real solution: Transactional Outbox Pattern (Phase 7+ stretch goal).
 * For now: DB commit first, then Kafka (if Kafka fails, payment is in DB).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentMapper paymentMapper;

    // ── Kafka consumer — ORDER_CREATED triggers payment ────────────────────

    @Override
    @KafkaListener(
            topics = "order.events",
            groupId = "payment-service-group",
            containerFactory = "orderEventListenerFactory"
    )
    public void processOrderPayment(OrderEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Received null or invalid order event, skipping");
            return;
        }

        // Only react to ORDER_CREATED — ignore other order events
        if (event.getEventType() != OrderEvent.EventType.ORDER_CREATED) {
            log.debug("Ignoring order event type: {}", event.getEventType());
            return;
        }

        log.info("Processing payment for orderId={} amount={} {}",
                event.getOrderId(), event.getTotalAmount(), event.getCurrency());

        // ── IDEMPOTENCY CHECK ─────────────────────────────────────────────
        String idempotencyKey = event.getOrderId().toString();
        paymentRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
            log.info("Payment already processed for orderId={} (status={}) — skipping duplicate event",
                    event.getOrderId(), existing.getStatus());
            // Re-publish the result in case the previous publish failed
            republishResult(existing);
            return;
        });

        if (paymentRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return; // already handled above (lambda limitation with return)
        }

        // ── Create payment record in PENDING state ────────────────────────
        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .userEmail(event.getUserEmail())
                .amount(event.getTotalAmount())
                .currency(event.getCurrency() != null ? event.getCurrency() : "USD")
                .status(Payment.PaymentStatus.PENDING)
                .paymentMethod(Payment.PaymentMethod.CARD)
                .build();

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted the same idempotency key
            log.warn("Concurrent duplicate payment attempt for orderId={} — finding existing",
                    event.getOrderId());
            paymentRepository.findByIdempotencyKey(idempotencyKey).ifPresent(this::republishResult);
            return;
        }

        // ── Call payment gateway ──────────────────────────────────────────
        PaymentGatewayResult result = paymentGateway.charge(
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod().name()
        );

        // ── Update payment record and publish result ───────────────────────
        if (result.isSuccess()) {
            payment.markSuccess(result.getTransactionId(), result.getRawResponse());
            paymentRepository.save(payment);
            eventPublisher.publishSuccess(payment);
            log.info("Payment SUCCESS orderId={} txn={}", payment.getOrderId(), payment.getTransactionId());
        } else {
            payment.markFailed(result.getFailureReason(), result.getRawResponse());
            paymentRepository.save(payment);
            eventPublisher.publishFailure(payment);
            log.warn("Payment FAILED orderId={} reason={}", payment.getOrderId(), result.getFailureReason());
        }
    }

    // ── Read operations ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for order: " + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getByUser(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toResponse);
    }

    // ── Refund ────────────────────────────────────────────────────────────

    @Override
    public PaymentResponse refund(UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != Payment.PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Can only refund successful payments. Current status: " + payment.getStatus());
        }

        PaymentGatewayResult result = paymentGateway.refund(
                payment.getTransactionId(), request.getAmount());

        if (result.isSuccess()) {
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Refund SUCCESS paymentId={} refundId={}", paymentId, result.getTransactionId());
        } else {
            log.error("Refund FAILED paymentId={} reason={}", paymentId, result.getFailureReason());
            throw new RuntimeException("Refund failed: " + result.getFailureReason());
        }

        return paymentMapper.toResponse(payment);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void republishResult(Payment existing) {
        if (existing.getStatus() == Payment.PaymentStatus.SUCCESS) {
            eventPublisher.publishSuccess(existing);
        } else if (existing.getStatus() == Payment.PaymentStatus.FAILED) {
            eventPublisher.publishFailure(existing);
        }
    }
}
