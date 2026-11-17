package org.messenger.messagesvc.message.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageReceivedEvent;
import org.messenger.messagesvc.message.service.MessageStatusService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDeliveredConsumer {

    private final MessageStatusService messageStatusService;

    @KafkaListener(
            topics = KafkaTopics.CHAT_RECEIVED,
            groupId = "message-status-group",
            containerFactory = "deliveredEventListenerContainerFactory"
    )
    public void consume(MessageReceivedEvent event, Acknowledgment acknowledgment) {
        messageStatusService.markDelivered(event);
        acknowledgment.acknowledge();
        log.info("Marked message {} as delivered to {}", event.messageId(), event.recipientId());
    }
}
