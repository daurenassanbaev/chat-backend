package com.messenger.chatgateway.messaging.kafka.config;

import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageDeliveredEvent> kafkaListenerContainerFactory
            (ConsumerFactory<String, MessageDeliveredEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, MessageDeliveredEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        factory.setConcurrency(3);

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        factory.getContainerProperties().setSyncCommits(true);

        return factory;
    }
}
