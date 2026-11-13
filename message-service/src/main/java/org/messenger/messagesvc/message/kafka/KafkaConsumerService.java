package org.messenger.messagesvc.message.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.messenger.messagesvc.message.service.MessageService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final MessageService messageService;

    @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGES,
            groupId = "message-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMessage(MessageSentEvent eventDto, Acknowledgment ack) {
        try {
            log.info("Received MessageSentEvent chatId={}, senderId={}", eventDto.chatId(), eventDto.senderId());

            messageService.save(eventDto);

            ack.acknowledge();

            log.info("Successfully processed MessageSentEvent chatId={}, senderId={}", eventDto.chatId(), eventDto.senderId());
        } catch (Exception e) {
            log.error(
                    "Failed to process MessageSentEvent chatId={}, senderId={}, error={}",
                    eventDto.chatId(),
                    eventDto.senderId(),
                    e.getMessage(),
                    e
            );
            throw new RuntimeException(e);
        }
    }
}
