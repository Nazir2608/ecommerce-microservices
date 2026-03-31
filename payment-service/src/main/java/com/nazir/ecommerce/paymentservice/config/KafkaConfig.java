package com.nazir.ecommerce.paymentservice.config;

import com.nazir.ecommerce.paymentservice.event.OrderEvent;
import com.nazir.ecommerce.paymentservice.event.PaymentEvent;
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

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── Producer (payment.events) ─────────────────────────────────────────

    @Bean
    public ProducerFactory<String, PaymentEvent> paymentEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,   bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG,               "all",
                ProducerConfig.RETRIES_CONFIG,             3,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,  true,
                JsonSerializer.ADD_TYPE_INFO_HEADERS,      false
        ));
    }

    @Bean
    public KafkaTemplate<String, PaymentEvent> kafkaTemplate() {
        return new KafkaTemplate<>(paymentEventProducerFactory());
    }

    // ── Consumer (order.events) ───────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, OrderEvent> orderEventConsumerFactory() {
        JsonDeserializer<OrderEvent> deser = new JsonDeserializer<>(OrderEvent.class);
        deser.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,          "payment-service-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        ), new StringDeserializer(), deser);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> orderEventListenerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
        factory.setConsumerFactory(orderEventConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }
}
