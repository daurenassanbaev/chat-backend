package org.messenger.messagesvc.message.kafka.config;

import org.apache.kafka.common.TopicPartition;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageSentEvent> kafkaListenerContainerFactory
            (ConsumerFactory<String, MessageSentEvent> consumerFactory, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, MessageSentEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        factory.setConcurrency(3);

        factory.setCommonErrorHandler(errorHandler);

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        factory.getContainerProperties().setSyncCommits(true);

        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> new TopicPartition(
                record.topic() + ".dlt",
                record.partition()
        ));

        FixedBackOff backOff = new FixedBackOff(
                1000L,
                3
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, backOff
        );

        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
