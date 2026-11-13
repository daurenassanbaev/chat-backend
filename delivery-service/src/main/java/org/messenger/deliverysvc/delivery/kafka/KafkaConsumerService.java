package org.messenger.deliverysvc.delivery.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.messenger.deliverysvc.delivery.service.DeliveryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final DeliveryService deliveryService;

    @KafkaListener(
            topics = KafkaTopics.CHAT_PROCESSED,
            groupId = "delivery-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listenSavedEvent(MessageSavedEvent eventDto, Acknowledgment ack) {
        try {
            log.info(
                    "Received MessageSavedEvent messageId={}, senderId={}, recipientId={}",
                    eventDto.messageId(),
                    eventDto.senderId(),
                    eventDto.recipientId()
            );

            deliveryService.processMessageEvent(eventDto);

            ack.acknowledge();

            log.info(
                    "Successfully processed MessageSavedEvent messageId={}, senderId={}, recipientId={}",
                    eventDto.messageId(),
                    eventDto.senderId(),
                    eventDto.recipientId()
            );
        } catch (Exception e) {

            log.error(
                    "Failed to process MessageSavedEvent messageId={}, senderId={}, recipientId={}, error={}",
                    eventDto.messageId(),
                    eventDto.senderId(),
                    eventDto.recipientId(),
                    e.getMessage(),
                    e
            );
            throw new RuntimeException(e);
        }
    }
}
