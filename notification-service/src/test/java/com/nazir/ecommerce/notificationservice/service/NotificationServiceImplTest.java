package com.nazir.ecommerce.notificationservice.service;

import com.nazir.ecommerce.notificationservice.event.OrderEvent;
import com.nazir.ecommerce.notificationservice.event.PaymentEvent;
import com.nazir.ecommerce.notificationservice.event.UserEvent;
import com.nazir.ecommerce.notificationservice.model.NotificationRecord;
import com.nazir.ecommerce.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock
    private EmailTemplateService emailService;
    @Mock
    private DeduplicationService dedup;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    // ── User event tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("handleUserEvent()")
    class UserEventTests {

        @Test
        @DisplayName("should send welcome email on USER_REGISTERED")
        void userRegistered_sendsWelcomeEmail() {
            UserEvent event = userEvent(UserEvent.EventType.USER_REGISTERED);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handleUserEvent(event);

            then(emailService).should().sendWelcomeEmail(event);
        }

        @Test
        @DisplayName("should send suspension email on USER_SUSPENDED")
        void userSuspended_sendsSuspensionEmail() {
            UserEvent event = userEvent(UserEvent.EventType.USER_SUSPENDED);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handleUserEvent(event);

            then(emailService).should().sendAccountSuspendedEmail(event);
        }

        @Test
        @DisplayName("should skip duplicate user event")
        void userEvent_duplicate_skipped() {
            UserEvent event = userEvent(UserEvent.EventType.USER_REGISTERED);
            given(dedup.isNew(event.getEventId())).willReturn(false);

            notificationService.handleUserEvent(event);

            then(emailService).should(never()).sendWelcomeEmail(any());
        }

        @Test
        @DisplayName("should handle null event gracefully")
        void userEvent_null_handled() {
            assertThatCode(() -> notificationService.handleUserEvent(null))
                    .doesNotThrowAnyException();
            then(emailService).should(never()).sendWelcomeEmail(any());
        }
    }

    // ── Order event tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("handleOrderEvent()")
    class OrderEventTests {

        @Test
        @DisplayName("should send confirmed email on ORDER_CONFIRMED")
        void orderConfirmed_sendsEmail() {
            OrderEvent event = orderEvent(OrderEvent.EventType.ORDER_CONFIRMED);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handleOrderEvent(event);

            then(emailService).should().sendOrderConfirmedEmail(event);
        }

        @Test
        @DisplayName("should send shipped email on ORDER_SHIPPED")
        void orderShipped_sendsEmail() {
            OrderEvent event = orderEvent(OrderEvent.EventType.ORDER_SHIPPED);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handleOrderEvent(event);

            then(emailService).should().sendOrderShippedEmail(event);
        }

        @Test
        @DisplayName("should send cancelled email on ORDER_CANCELLED")
        void orderCancelled_sendsEmail() {
            OrderEvent event = orderEvent(OrderEvent.EventType.ORDER_CANCELLED);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handleOrderEvent(event);

            then(emailService).should().sendOrderCancelledEmail(event);
        }

        @Test
        @DisplayName("should NOT send email on ORDER_CREATED (no customer email needed)")
        void orderCreated_noEmail() {
            OrderEvent event = orderEvent(OrderEvent.EventType.ORDER_CREATED);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handleOrderEvent(event);

            then(emailService).should(never()).sendOrderConfirmedEmail(any());
            then(emailService).should(never()).sendOrderCancelledEmail(any());
        }

        @Test
        @DisplayName("should skip duplicate order event")
        void orderEvent_duplicate_skipped() {
            OrderEvent event = orderEvent(OrderEvent.EventType.ORDER_CONFIRMED);
            given(dedup.isNew(event.getEventId())).willReturn(false);

            notificationService.handleOrderEvent(event);

            then(emailService).should(never()).sendOrderConfirmedEmail(any());
        }
    }

    // ── Payment event tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentEvent()")
    class PaymentEventTests {

        @Test
        @DisplayName("should send payment failed email on PAYMENT_FAILED")
        void paymentFailed_sendsEmail() {
            PaymentEvent event = paymentEvent(PaymentEvent.EventType.PAYMENT_FAILED);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handlePaymentEvent(event);

            then(emailService).should().sendPaymentFailedEmail(event);
        }

        @Test
        @DisplayName("should NOT send email on PAYMENT_SUCCESS (ORDER_CONFIRMED handles it)")
        void paymentSuccess_noEmail() {
            PaymentEvent event = paymentEvent(PaymentEvent.EventType.PAYMENT_SUCCESS);
            given(dedup.isNew(event.getEventId())).willReturn(true);

            notificationService.handlePaymentEvent(event);

            then(emailService).should(never()).sendPaymentFailedEmail(any());
        }

        @Test
        @DisplayName("should skip duplicate payment event")
        void paymentEvent_duplicate_skipped() {
            PaymentEvent event = paymentEvent(PaymentEvent.EventType.PAYMENT_FAILED);
            given(dedup.isNew(event.getEventId())).willReturn(false);

            notificationService.handlePaymentEvent(event);

            then(emailService).should(never()).sendPaymentFailedEmail(any());
        }
    }

    // ── Recent notifications tests ────────────────────────────────────────

    @Nested
    @DisplayName("getRecentNotifications()")
    class RecentNotificationsTests {

        @Test
        @DisplayName("should record SENT notifications")
        void recordsSentNotifications() {
            UserEvent event = userEvent(UserEvent.EventType.USER_REGISTERED);
            given(dedup.isNew(event.getEventId())).willReturn(true);
            willDoNothing().given(emailService).sendWelcomeEmail(any());

            notificationService.handleUserEvent(event);

            List<NotificationRecord> recent = notificationService.getRecentNotifications(10);
            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).getStatus()).isEqualTo(NotificationRecord.NotificationStatus.SENT);
            assertThat(recent.get(0).getNotificationType()).isEqualTo("WELCOME");
        }

        @Test
        @DisplayName("should record SKIPPED_DUPLICATE notifications")
        void recordsDuplicateSkips() {
            UserEvent event = userEvent(UserEvent.EventType.USER_REGISTERED);
            given(dedup.isNew(event.getEventId())).willReturn(false);

            notificationService.handleUserEvent(event);

            List<NotificationRecord> recent = notificationService.getRecentNotifications(10);
            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).getStatus())
                    .isEqualTo(NotificationRecord.NotificationStatus.SKIPPED_DUPLICATE);
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────

    private UserEvent userEvent(UserEvent.EventType type) {
        UserEvent e = new UserEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType(type);
        e.setUserId(UUID.randomUUID());
        e.setEmail("nazir@example.com");
        e.setFirstName("Nazir");
        e.setLastName("Khan");
        e.setUsername("nazir");
        return e;
    }

    private OrderEvent orderEvent(OrderEvent.EventType type) {
        OrderEvent e = new OrderEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType(type);
        e.setOrderId(UUID.randomUUID());
        e.setOrderNumber("ORD-20240315-A1B2");
        e.setUserId(UUID.randomUUID());
        e.setUserEmail("nazir@example.com");
        e.setTotalAmount(new BigDecimal("199.99"));
        e.setCurrency("USD");
        return e;
    }

    private PaymentEvent paymentEvent(PaymentEvent.EventType type) {
        PaymentEvent e = new PaymentEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType(type);
        e.setOrderId(UUID.randomUUID());
        e.setUserId(UUID.randomUUID());
        e.setUserEmail("nazir@example.com");
        e.setAmount(new BigDecimal("199.99"));
        e.setCurrency("USD");
        e.setFailureReason("Card declined");
        return e;
    }
}
