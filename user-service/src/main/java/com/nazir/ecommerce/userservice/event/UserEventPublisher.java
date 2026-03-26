package com.nazir.ecommerce.userservice.event;

import com.nazir.ecommerce.userservice.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes {@link UserEvent}s to the {@code user.events} Kafka topic.
 * <p>
 * Async Kafka send
 * <p>
 * kafkaTemplate.send() is non-blocking — it returns a CompletableFuture.
 * We attach callbacks via whenComplete() to log success/failure.
 * <p>
 * We do NOT block the calling thread waiting for Kafka acknowledgment.
 * If the DB write succeeded but Kafka fails, the user is still created
 * but the welcome email won't be sent.
 * <p>
 * Production solution: Transactional Outbox Pattern
 * Save event to a DB table in the same transaction as the User.
 * A background process reads the table and publishes to Kafka.
 * This guarantees exactly-once publishing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    public static final String TOPIC = "user.events";

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    public void publishUserRegistered(User user) {
        publish(user, UserEvent.EventType.USER_REGISTERED);
    }

    public void publishUserUpdated(User user) {
        publish(user, UserEvent.EventType.USER_UPDATED);
    }

    public void publishUserDeleted(User user) {
        publish(user, UserEvent.EventType.USER_DELETED);
    }

    public void publishUserSuspended(User user) {
        publish(user, UserEvent.EventType.USER_SUSPENDED);
    }

    public void publishPasswordChanged(User user) {
        publish(user, UserEvent.EventType.PASSWORD_CHANGED);
    }


    private void publish(User user, UserEvent.EventType type) {
        UserEvent event = UserEvent.builder()
                .eventType(type)
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();

        // Key = userId → all events for same user land on same partition (ordering guaranteed)
        CompletableFuture<SendResult<String, UserEvent>> future = kafkaTemplate.send(TOPIC, user.getId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka] FAILED to publish {} for userId={}: {}",
                        type, user.getId(), ex.getMessage());
                // TODO: store in outbox table for retry
            } else {
                log.info("[Kafka] Published {} for userId={} → partition={} offset={}", type, user.getId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}

