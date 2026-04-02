package com.nazir.ecommerce.notificationservice.service.impl;

import com.nazir.ecommerce.notificationservice.event.OrderEvent;
import com.nazir.ecommerce.notificationservice.event.PaymentEvent;
import com.nazir.ecommerce.notificationservice.event.UserEvent;
import com.nazir.ecommerce.notificationservice.model.NotificationRecord;
import com.nazir.ecommerce.notificationservice.service.DeduplicationService;
import com.nazir.ecommerce.notificationservice.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Notification service — fan-in Kafka consumer.
 * <p>
 * ═══════════════════════════════════════════════════════════════════════
 * Fan-in pattern:
 * ═══════════════════════════════════════════════════════════════════════
 * <p>
 * user.events    ──┐
 * order.events   ──┼──► NotificationServiceImpl ──► EmailTemplateService ──► SMTP
 * payment.events ──┘
 * <p>
 * Each @KafkaListener method handles one topic independently.
 * Adding new notification type = add one method here + one template.
 * user-service, order-service, payment-service don't know we exist.
 * <p>
 * ═══════════════════════════════════════════════════════════════════════
 * — Deduplication before every action:
 * ═══════════════════════════════════════════════════════════════════════
 * <p>
 * Pattern used in every listener:
 * if (!dedup.isNew(event.getEventId())) return;  ← SKIP duplicate
 * emailService.sendXxx(event);                   ← PROCESS new event
 * <p>
 * dedup.isNew() calls Redis SETNX — atomic, thread-safe.
 * Works correctly even with multiple service instances running.
 * <p>
 * ═══════════════════════════════════════════════════════════════════════
 * — Consumer group isolation:
 * ═══════════════════════════════════════════════════════════════════════
 * <p>
 * groupId = "notification-service-group"
 * Different from order-service-group and payment-service-group.
 * Kafka delivers each message to ONE consumer per group.
 * notification-service receives ALL events independently of other services.
 * order-service also gets order events (its own group) — no conflict.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl {

    private final EmailTemplateService emailService;
    private final DeduplicationService dedup;

    // In-memory recent activity (last 100) — for health/monitoring endpoint
    private final List<NotificationRecord> recentNotifications =
            Collections.synchronizedList(new ArrayList<>());

    // ── Listener 1: user.events ───────────────────────────────────────────

    @KafkaListener(topics = "user.events", groupId = "notification-service-group", containerFactory = "userEventListenerFactory")
    public void handleUserEvent(UserEvent event) {
        if (event == null || event.getEventId() == null) return;
        log.info("[Kafka] user.events → eventType={} userId={}", event.getEventType(), event.getUserId());

        if (!dedup.isNew(event.getEventId())) {
            record(event.getEventId(), "USER_" + event.getEventType(), event.getEmail(), "SKIPPED", null);
            return;
        }

        try {
            switch (event.getEventType()) {
                case USER_REGISTERED -> {
                    emailService.sendWelcomeEmail(event);
                    record(event.getEventId(), "WELCOME", event.getEmail(), "SENT", null);
                }
                case USER_SUSPENDED -> {
                    emailService.sendAccountSuspendedEmail(event);
                    record(event.getEventId(), "ACCOUNT_SUSPENDED", event.getEmail(), "SENT", null);
                }
                default -> log.debug("No email configured for user event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("[Email] Failed for user event {}: {}", event.getEventId(), e.getMessage());
            record(event.getEventId(), "USER_" + event.getEventType(), event.getEmail(), "FAILED", e.getMessage());
        }
    }

    // ── Listener 2: order.events ──────────────────────────────────────────

    @KafkaListener(topics = "order.events", groupId = "notification-service-group", containerFactory = "orderEventListenerFactory")
    public void handleOrderEvent(OrderEvent event) {
        if (event == null || event.getEventId() == null) return;
        log.info("[Kafka] order.events → eventType={} orderId={}", event.getEventType(), event.getOrderId());
        if (!dedup.isNew(event.getEventId())) {
            record(event.getEventId(), "ORDER_" + event.getEventType(), event.getUserEmail(), "SKIPPED", null);
            return;
        }

        try {
            switch (event.getEventType()) {
                case ORDER_CONFIRMED -> {
                    emailService.sendOrderConfirmedEmail(event);
                    record(event.getEventId(), "ORDER_CONFIRMED", event.getUserEmail(), "SENT", null);
                }
                case ORDER_SHIPPED -> {
                    emailService.sendOrderShippedEmail(event);
                    record(event.getEventId(), "ORDER_SHIPPED", event.getUserEmail(), "SENT", null);
                }
                case ORDER_DELIVERED -> {
                    emailService.sendOrderDeliveredEmail(event);
                    record(event.getEventId(), "ORDER_DELIVERED", event.getUserEmail(), "SENT", null);
                }
                case ORDER_CANCELLED -> {
                    emailService.sendOrderCancelledEmail(event);
                    record(event.getEventId(), "ORDER_CANCELLED", event.getUserEmail(), "SENT", null);
                }
                case ORDER_CREATED -> log.debug("ORDER_CREATED — no customer email for this event");
            }
        } catch (Exception e) {
            log.error("[Email] Failed for order event {}: {}", event.getEventId(), e.getMessage());
            record(event.getEventId(), "ORDER_" + event.getEventType(), event.getUserEmail(), "FAILED", e.getMessage());
        }
    }

    // ── Listener 3: payment.events ────────────────────────────────────────

    @KafkaListener(topics = "payment.events", groupId = "notification-service-group", containerFactory = "paymentEventListenerFactory"
    )
    public void handlePaymentEvent(PaymentEvent event) {
        if (event == null || event.getEventId() == null) return;
        log.info("[Kafka] payment.events → eventType={} orderId={}", event.getEventType(), event.getOrderId());

        if (!dedup.isNew(event.getEventId())) {
            record(event.getEventId(), "PAYMENT_" + event.getEventType(), event.getUserEmail(), "SKIPPED", null);
            return;
        }

        try {
            if (event.getEventType() == PaymentEvent.EventType.PAYMENT_FAILED) {
                emailService.sendPaymentFailedEmail(event);
                record(event.getEventId(), "PAYMENT_FAILED", event.getUserEmail(), "SENT", null);
            } else {
                log.debug("PAYMENT_SUCCESS — no email needed (ORDER_CONFIRMED handles it)");
            }
        } catch (Exception e) {
            log.error("[Email] Failed for payment event {}: {}", event.getEventId(), e.getMessage());
            record(event.getEventId(), "PAYMENT_" + event.getEventType(), event.getUserEmail(), "FAILED", e.getMessage());
        }
    }

    // ── In-memory recent activity ─────────────────────────────────────────

    public List<NotificationRecord> getRecentNotifications(int limit) {
        int size = recentNotifications.size();
        int from = Math.max(0, size - limit);
        return new ArrayList<>(recentNotifications.subList(from, size));
    }

    private void record(String eventId, String type, String email, String status, String error) {
        NotificationRecord.NotificationStatus s = switch (status) {
            case "SENT" -> NotificationRecord.NotificationStatus.SENT;
            case "SKIPPED" -> NotificationRecord.NotificationStatus.SKIPPED_DUPLICATE;
            default -> NotificationRecord.NotificationStatus.FAILED;
        };
        recentNotifications.add(NotificationRecord.builder().eventId(eventId).notificationType(type)
                .recipientEmail(email).status(s).errorMessage(error).build());
        // Keep last 100 only
        if (recentNotifications.size() > 100) {
            recentNotifications.remove(0);
        }
    }
}
