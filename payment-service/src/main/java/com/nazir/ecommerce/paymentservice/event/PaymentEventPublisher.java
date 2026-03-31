package com.nazir.ecommerce.paymentservice.event;

import com.nazir.ecommerce.paymentservice.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    public static final String TOPIC = "payment.events";
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publishSuccess(Payment payment) {
        publish(payment, PaymentEvent.EventType.PAYMENT_SUCCESS, null);
    }

    public void publishFailure(Payment payment) {
        publish(payment, PaymentEvent.EventType.PAYMENT_FAILED, payment.getFailureReason());
    }

    private void publish(Payment payment, PaymentEvent.EventType type, String failureReason) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType(type)
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .userEmail(payment.getUserEmail())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .transactionId(payment.getTransactionId())
                .failureReason(failureReason)
                .build();

        // Key = orderId → same partition as order events (ordering preserved)
        kafkaTemplate.send(TOPIC, payment.getOrderId().toString(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null)
                        log.error("[Kafka] FAILED {} for orderId={}: {}", type, payment.getOrderId(), ex.getMessage());
                    else
                        log.info("[Kafka] Published {} orderId={}", type, payment.getOrderId());
                });
    }
}
