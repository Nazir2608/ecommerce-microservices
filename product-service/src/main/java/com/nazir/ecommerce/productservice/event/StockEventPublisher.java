package com.nazir.ecommerce.productservice.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventPublisher {

    public static final String TOPIC = "product.stock.events";

    private final KafkaTemplate<String, StockEvent> kafkaTemplate;

    public void publish(StockEvent event) {
        // Key = productId → all stock events for same product land on same partition (ordering)
        CompletableFuture<SendResult<String, StockEvent>> future = kafkaTemplate.send(TOPIC, event.getProductId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka] FAILED to publish {} for productId={}: {}", event.getEventType(), event.getProductId(), ex.getMessage());
            } else {
                log.info("[Kafka] Published {} for productId={} → partition={} offset={}", event.getEventType(), event.getProductId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
