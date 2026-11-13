package com.messenger.chatgateway.messaging.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, MessageSentEvent> kafkaTemplate;

    public void sendMessageSentEvent(MessageSentEvent eventDto) {
        kafkaTemplate.send(KafkaTopics.CHAT_MESSAGES, eventDto.chatId().toString(), eventDto)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send event: {}", eventDto.messageId(), ex);
                    } else {
                        log.debug("Sent to partition {} offset {}" ,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());                    }
                });
    }
}
