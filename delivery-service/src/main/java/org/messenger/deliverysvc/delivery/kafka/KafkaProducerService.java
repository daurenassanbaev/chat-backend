package org.messenger.deliverysvc.delivery.kafka;

import lombok.RequiredArgsConstructor;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, MessageDeliveredEvent> kafkaTemplate;

    public void sendDeliveredEvent(MessageDeliveredEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.CHAT_DELIVERED, event.recipientId().toString(), event)
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Delivery send interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Delivery send failed or result unknown", e);
        }
    }
}
