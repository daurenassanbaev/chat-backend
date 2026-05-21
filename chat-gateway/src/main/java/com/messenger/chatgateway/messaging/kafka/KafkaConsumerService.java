package com.messenger.chatgateway.messaging.kafka;

import com.messenger.chatgateway.messaging.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ChatService chatService;

    @KafkaListener(
            topics = KafkaTopics.CHAT_DELIVERED,
            groupId = "gateway-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listenDeliveredEvent(
            MessageDeliveredEvent eventDto,
            Acknowledgment ack
    ) {

        try {

            log.info(
                    "Received MessageDeliveredEvent fromUserId={}, toUserId={}",
                    eventDto.fromUserId(),
                    eventDto.toUserId()
            );

            chatService.sendMessage(eventDto);

            ack.acknowledge();

            log.info(
                    "Successfully processed MessageDeliveredEvent fromUserId={}, toUserId={}",
                    eventDto.fromUserId(),
                    eventDto.toUserId()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to process MessageDeliveredEvent fromUserId={}, toUserId={}, error={}",
                    eventDto.fromUserId(),
                    eventDto.toUserId(),
                    e.getMessage(),
                    e
            );
        }
    }
}
