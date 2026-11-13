package org.messenger.deliverysvc.delivery.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic chatMessagesTopic() {
        return TopicBuilder.name(KafkaTopics.CHAT_PROCESSED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
