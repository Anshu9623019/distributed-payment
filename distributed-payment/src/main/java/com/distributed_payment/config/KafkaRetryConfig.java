package com.distributed_payment.config;

import com.distributed_payment.exception.MessageTransformationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier; // <-- Add this import
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@Slf4j
public class KafkaRetryConfig {

    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
        FixedBackOff backOff = new FixedBackOff(2000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        errorHandler.addNotRetryableExceptions(DeserializationException.class);
        errorHandler.addNotRetryableExceptions(MessageTransformationException.class);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> log.warn(
                "Saga Retry Monitor: Attempt {} failed for Kafka record partition-offset [{} - {}] due to: {}",
                deliveryAttempt, record.partition(), record.offset(), ex.getMessage()
        ));

        return errorHandler;
    }

    // Fixed: Explicitly injected 'kafkaTemplate' using @Qualifier to resolve the ambiguity
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            @Qualifier("kafkaTemplate") KafkaTemplate<?, ?> template) {
        return new DeadLetterPublishingRecoverer(template,
                (record, ex) -> {
                    log.error("CRITICAL DLQ INTERACTION: Message at partition-offset [{} - {}] has exhausted all retries. Routing payload to DLT. Root cause: {}",
                            record.partition(), record.offset(), ex.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                });
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}