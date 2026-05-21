package org.messenger.deliverysvc.delivery.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, MessageDeliveredEvent> kafkaTemplate;

    public void sendDeliveredEvent(
            MessageDeliveredEvent event
    ) {

        kafkaTemplate.send(
                KafkaTopics.CHAT_DELIVERED,
                event.toUserId(),
                event
        ).whenComplete((result, ex) -> {

            if (ex != null) {

                log.error(
                        "Failed to send MessageDeliveredEvent fromUserId={}, toUserId={}",
                        event.fromUserId(),
                        event.toUserId(),
                        ex
                );

                return;
            }

            log.info(
                    "Sent MessageDeliveredEvent fromUserId={}, toUserId={}, partition={}, offset={}",
                    event.fromUserId(),
                    event.toUserId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        });
    }
}
