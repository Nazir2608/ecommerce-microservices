package com.nazir.ecommerce.notificationservice.config;

import com.nazir.ecommerce.notificationservice.event.OrderEvent;
import com.nazir.ecommerce.notificationservice.event.PaymentEvent;
import com.nazir.ecommerce.notificationservice.event.UserEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * Three separate consumer factories — one per event type.
 * <p>
 * Why separate factories?
 * Each Kafka topic publishes a different class (UserEvent, OrderEvent, PaymentEvent).
 * The JSON deserializer needs to know the target class at factory creation time.
 * Three factories = three type-safe deserializers.
 * <p>
 * concurrency = 3:
 * With 3 partitions per topic and concurrency=3, Spring creates 3 consumer threads.
 * Each thread handles 1 partition — true parallel processing.
 * Scale: add more partitions + increase concurrency for higher throughput.
 * <p>
 * EARLIEST offset reset:
 * "earliest" → on first start, read all messages from the beginning.
 * "latest"   → on first start, read only new messages.
 * Use "earliest" in dev (see all test messages),
 * use "latest" in production (skip old messages after deployment).
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps(String groupId) {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10   // process 10 at a time
        );
    }

    private <T> ConsumerFactory<String, T> factory(Class<T> clazz) {
        JsonDeserializer<T> deser = new JsonDeserializer<>(clazz);
        deser.addTrustedPackages("*");
        deser.setUseTypeMapperForKey(false);
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps("notification-service-group"), new StringDeserializer(), deser);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
            ConsumerFactory<String, T> cf) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, UserEvent> userEventConsumerFactory() {
        return factory(UserEvent.class);
    }

    @Bean
    public ConsumerFactory<String, OrderEvent> orderEventConsumerFactory() {
        return factory(OrderEvent.class);
    }

    @Bean
    public ConsumerFactory<String, PaymentEvent> paymentEventConsumerFactory() {
        return factory(PaymentEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserEvent>
    userEventListenerFactory() {
        return listenerFactory(userEventConsumerFactory());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent>
    orderEventListenerFactory() {
        return listenerFactory(orderEventConsumerFactory());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent>
    paymentEventListenerFactory() {
        return listenerFactory(paymentEventConsumerFactory());
    }
}
