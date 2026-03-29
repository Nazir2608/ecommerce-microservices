package com.nazir.ecommerce.orderservice.config;

import com.nazir.ecommerce.orderservice.event.OrderEvent;
import com.nazir.ecommerce.orderservice.event.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Kafka producer (order.events) + consumer (payment.events) configuration.
 *
 * LEARNING POINT — Why separate producer/consumer factories?
 *   Producer serializes Java objects → JSON bytes (for sending)
 *   Consumer deserializes JSON bytes → Java objects (for receiving)
 *   Different classes, different config, different factory beans.
 *
 * LEARNING POINT — Consumer group "order-service-group":
 *   All instances of order-service share the same groupId.
 *   Kafka distributes partitions across instances in the group.
 *   With 3 partitions and 2 instances → instance A gets 2, instance B gets 1.
 *   Each message is consumed ONCE per group (exactly one instance processes it).
 *
 * LEARNING POINT — TRUSTED_PACKAGES:
 *   Jackson deserializer needs to know which packages are safe to deserialize.
 *   Without this, it refuses to deserialize unknown class names (security feature).
 *   "*" allows all — acceptable for internal services on private network.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── Producer (order.events) ──────────────────────────────────────────

    @Bean
    public ProducerFactory<String, OrderEvent> orderEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,   bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG,                "all",   // wait for all replicas
                ProducerConfig.RETRIES_CONFIG,              3,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,  true,    // exactly-once producer
                JsonSerializer.ADD_TYPE_INFO_HEADERS,      false    // no class name in header
        ));
    }

    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate() {
        return new KafkaTemplate<>(orderEventProducerFactory());
    }

    // ── Consumer (payment.events) ────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, PaymentEvent> paymentEventConsumerFactory() {
        JsonDeserializer<PaymentEvent> deserializer = new JsonDeserializer<>(PaymentEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,           "order-service-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false   // manual offset commit
        ), new org.apache.kafka.common.serialization.StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent>
    paymentEventListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentEventConsumerFactory());
        factory.setConcurrency(3);  // 3 consumer threads (matches partition count)
        return factory;
    }
}
