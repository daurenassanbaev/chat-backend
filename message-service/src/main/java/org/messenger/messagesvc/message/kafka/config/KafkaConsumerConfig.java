package org.messenger.messagesvc.message.kafka.config;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.chat.shared.kafka.event.MessageReceivedEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

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
    public ConcurrentKafkaListenerContainerFactory<String, MessageReceivedEvent> deliveredEventListenerContainerFactory(
            KafkaProperties kafkaProperties,
            DefaultErrorHandler errorHandler
    ) {
        Map<String, Object> consumerProperties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerProperties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MessageReceivedEvent.class.getName());
        ConsumerFactory<String, MessageReceivedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProperties);

        ConcurrentKafkaListenerContainerFactory<String, MessageReceivedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
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

        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
