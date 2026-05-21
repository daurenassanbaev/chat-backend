package org.messenger.messagesvc.message.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, MessageSavedEvent> kafkaTemplate;

    public void sendMessage(MessageSavedEvent event) {

        try {

            var result = kafkaTemplate
                    .send(
                            KafkaTopics.CHAT_PROCESSED,
                            event.toUserId(),
                            event
                    )
                    .get();

            log.info(
                    "Sent MessageSavedEvent messageId={}, toUserId={}, partition={}, offset={}",
                    event.messageId(),
                    event.toUserId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to send MessageSavedEvent messageId={}, toUserId={}",
                    event.messageId(),
                    event.toUserId(),
                    ex
            );

            throw new RuntimeException(ex);
        }
    }
}
