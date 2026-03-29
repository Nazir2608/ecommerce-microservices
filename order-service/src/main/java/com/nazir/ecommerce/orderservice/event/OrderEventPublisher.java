package com.nazir.ecommerce.orderservice.event;

import com.nazir.ecommerce.orderservice.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    public static final String TOPIC = "order.events";
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publish(Order order, OrderEvent.EventType type) {
        OrderEvent event = OrderEvent.builder()
                .eventType(type)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .reason(order.getCancellationReason())
                .build();

        // Key = orderId → all events for same order land on same partition (ordering)
        kafkaTemplate.send(TOPIC, order.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] FAILED {} for orderId={}: {}", type, order.getId(), ex.getMessage());
                    } else {
                        log.info("[Kafka] Published {} orderId={} partition={} offset={}",
                                type, order.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
