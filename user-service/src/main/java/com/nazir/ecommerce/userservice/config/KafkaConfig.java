package com.nazir.ecommerce.userservice.config;

import com.nazir.ecommerce.userservice.event.UserEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for user-service.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Kafka producer acknowledgment modes (acks)             │
 * │                                                                          │
 * │  acks=0  → Fire and forget. No confirmation. Fastest, but data loss risk.│
 * │  acks=1  → Leader broker acknowledges. Fast, mild risk if leader crashes. │
 * │  acks=all→ All in-sync replicas (ISR) acknowledge. Slowest, zero loss.  │
 * │                                                                          │
 * │  For user events (registration email, audit log) we use acks=all.        │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Idempotent producer (enable.idempotence=true)          │
 * │                                                                          │
 * │  Without idempotence: a network retry can produce duplicate messages.    │
 * │  With idempotence: Kafka assigns a sequence number per producer session. │
 * │  Duplicate sends with the same sequence → broker deduplicates.           │
 * │  Requires: acks=all, retries >= 1.                                       │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — Message key selection                                  │
 * │                                                                          │
 * │  We use userId as the message key.                                       │
 * │  Kafka routes messages with the same key to the same partition.          │
 * │  → All events for a given user are ordered chronologically.              │
 * │  → notification-service processes events in the correct order per user.  │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, UserEvent> userEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Batching — wait up to 5ms to batch messages → higher throughput
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);  // 16KB batch

        // Compression — snappy is fast and reduces network bandwidth
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // JSON: add type info so consumers can deserialize without knowing the class
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate — the primary interface for sending messages.
     * Usage: kafkaTemplate.send(topic, key, value)
     */
    @Bean
    public KafkaTemplate<String, UserEvent> kafkaTemplate() {
        return new KafkaTemplate<>(userEventProducerFactory());
    }
}
