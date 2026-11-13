package org.messenger.deliverysvc.delivery.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, MessageDeliveredEvent> kafkaTemplate;

    public void sendDeliveredEvent(MessageDeliveredEvent event) {
        kafkaTemplate.send(
                KafkaTopics.CHAT_DELIVERED,
                event.recipientId().toString(),
                event
        ).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(
                        "Failed to send MessageDeliveredEvent senderId={}, recipientId={}",
                        event.senderId(),
                        event.recipientId(),
                        ex
                );

                return;
            }

            log.info(
                    "Sent MessageDeliveredEvent senderId={}, recipientId={}, partition={}, offset={}",
                    event.senderId(),
                    event.recipientId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        });
    }
}
