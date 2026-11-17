package com.messenger.chatgateway.messaging.kafka;

import lombok.RequiredArgsConstructor;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, MessageSentEvent> kafkaTemplate;

    public CompletableFuture<SendResult<String, MessageSentEvent>> sendMessageSentEvent(MessageSentEvent event) {
        return kafkaTemplate.send(KafkaTopics.CHAT_MESSAGES, event.chatId().toString(), event);
    }
}
