package com.nazir.ecommerce.paymentservice.service;

import com.nazir.ecommerce.paymentservice.event.OrderEvent;
import com.nazir.ecommerce.paymentservice.event.PaymentEventPublisher;
import com.nazir.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.nazir.ecommerce.paymentservice.gateway.MockPaymentGateway;
import com.nazir.ecommerce.paymentservice.gateway.PaymentGateway;
import com.nazir.ecommerce.paymentservice.gateway.PaymentGatewayResult;
import com.nazir.ecommerce.paymentservice.mapper.PaymentMapper;
import com.nazir.ecommerce.paymentservice.model.Payment;
import com.nazir.ecommerce.paymentservice.repository.PaymentRepository;
import com.nazir.ecommerce.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl")
class PaymentServiceImplTest {

    @Mock private PaymentRepository     paymentRepository;
    @Mock private PaymentGateway        paymentGateway;
    @Mock private PaymentEventPublisher eventPublisher;
    @Mock private PaymentMapper         paymentMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID  = UUID.randomUUID();

    private OrderEvent orderEvent() {
        OrderEvent e = new OrderEvent();
        e.setEventType(OrderEvent.EventType.ORDER_CREATED);
        e.setOrderId(ORDER_ID);
        e.setUserId(USER_ID);
        e.setUserEmail("user@example.com");
        e.setTotalAmount(new BigDecimal("199.99"));
        e.setCurrency("USD");
        e.setOrderNumber("ORD-TEST-001");
        return e;
    }

    private Payment savedPendingPayment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(ORDER_ID.toString())
                .orderId(ORDER_ID).userId(USER_ID)
                .userEmail("user@example.com")
                .amount(new BigDecimal("199.99")).currency("USD")
                .status(Payment.PaymentStatus.PENDING)
                .paymentMethod(Payment.PaymentMethod.CARD)
                .retryCount(0)
                .build();
    }

    // ── processOrderPayment tests ─────────────────────────────────────────

    @Nested @DisplayName("processOrderPayment()")
    class ProcessPaymentTests {

        @Test @DisplayName("should charge gateway and publish SUCCESS on successful payment")
        void processPayment_success() {
            Payment pending = savedPendingPayment();
            given(paymentRepository.findByIdempotencyKey(ORDER_ID.toString()))
                    .willReturn(Optional.empty());
            given(paymentRepository.save(any())).willReturn(pending);
            given(paymentGateway.charge(any(), any(), any(), any()))
                    .willReturn(PaymentGatewayResult.builder()
                            .success(true).transactionId("TXN-ABCD1234").rawResponse("{\"status\":\"ok\"}")
                            .build());

            paymentService.processOrderPayment(orderEvent());

            then(paymentGateway).should().charge(eq(ORDER_ID), any(), any(), any());
            then(eventPublisher).should().publishSuccess(any());
            then(eventPublisher).should(never()).publishFailure(any());
        }

        @Test @DisplayName("should publish FAILURE when gateway declines payment")
        void processPayment_gatewayFailure() {
            Payment pending = savedPendingPayment();
            given(paymentRepository.findByIdempotencyKey(ORDER_ID.toString()))
                    .willReturn(Optional.empty());
            given(paymentRepository.save(any())).willReturn(pending);
            given(paymentGateway.charge(any(), any(), any(), any()))
                    .willReturn(PaymentGatewayResult.builder()
                            .success(false).failureReason("Card declined").errorCode("DO_NOT_HONOR")
                            .build());

            paymentService.processOrderPayment(orderEvent());

            then(eventPublisher).should().publishFailure(any());
            then(eventPublisher).should(never()).publishSuccess(any());
        }

        @Test @DisplayName("should be idempotent — skip processing if payment already exists")
        void processPayment_idempotent_existingSuccess() {
            Payment existing = savedPendingPayment();
            existing.markSuccess("TXN-EXISTING", "{}");

            given(paymentRepository.findByIdempotencyKey(ORDER_ID.toString()))
                    .willReturn(Optional.of(existing));

            paymentService.processOrderPayment(orderEvent());

            // Gateway should NOT be called — payment already processed
            then(paymentGateway).should(never()).charge(any(), any(), any(), any());
        }

        @Test @DisplayName("should ignore non-ORDER_CREATED events")
        void processPayment_ignoresOtherEventTypes() {
            OrderEvent event = orderEvent();
            event.setEventType(OrderEvent.EventType.ORDER_CANCELLED);

            paymentService.processOrderPayment(event);

            then(paymentGateway).should(never()).charge(any(), any(), any(), any());
            then(paymentRepository).should(never()).save(any());
        }

        @Test @DisplayName("should handle null event gracefully")
        void processPayment_nullEvent() {
            assertThatCode(() -> paymentService.processOrderPayment(null))
                    .doesNotThrowAnyException();
        }
    }

    // ── getByOrderId tests ────────────────────────────────────────────────

    @Nested @DisplayName("getByOrderId()")
    class GetByOrderIdTests {

        @Test @DisplayName("should throw PaymentNotFoundException when no payment for order")
        void getByOrderId_notFound() {
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
            assertThatThrownBy(() -> paymentService.getByOrderId(ORDER_ID))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }

    // ── MockPaymentGateway tests ──────────────────────────────────────────

    @Nested @DisplayName("MockPaymentGateway")
    class MockGatewayTests {

        @Test @DisplayName("amount 0.01 always triggers INSUFFICIENT_FUNDS")
        void mockGateway_insufficientFunds() {
            MockPaymentGateway gateway = new MockPaymentGateway();
            PaymentGatewayResult result = gateway.charge(
                    UUID.randomUUID(), new BigDecimal("0.01"), "USD", "CARD");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        }

        @Test @DisplayName("amount 0.02 always triggers CARD_DECLINED")
        void mockGateway_cardDeclined() {
            MockPaymentGateway gateway = new MockPaymentGateway();
            PaymentGatewayResult result = gateway.charge(
                    UUID.randomUUID(), new BigDecimal("0.02"), "USD", "CARD");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo("CARD_DECLINED");
        }

        @Test @DisplayName("refund always succeeds with refund ID")
        void mockGateway_refund() {
            MockPaymentGateway gateway = new MockPaymentGateway();
            PaymentGatewayResult result = gateway.refund("TXN-12345", new BigDecimal("50.00"));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTransactionId()).startsWith("REF-");
        }
    }
}
