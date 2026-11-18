package org.messenger.messagesvc.message.kafka;

import lombok.RequiredArgsConstructor;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, MessageSavedEvent> kafkaTemplate;
    public CompletableFuture<SendResult<String, MessageSavedEvent>> sendMessage(MessageSavedEvent event) {
        return kafkaTemplate.send(KafkaTopics.CHAT_PROCESSED, event.recipientId().toString(), event);
    }
}
