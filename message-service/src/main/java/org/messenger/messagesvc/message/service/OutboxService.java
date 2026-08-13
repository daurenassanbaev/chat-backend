package org.messenger.messagesvc.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.messenger.messagesvc.message.entity.OutboxEvent;
import org.messenger.messagesvc.message.kafka.KafkaProducerService;
import org.messenger.messagesvc.message.repository.OutboxRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService producerService;
    private final ObjectMapper objectMapper;

    public void processAll() {
        List<OutboxEvent> events = outboxRepository.findBySentFalse();

        for (OutboxEvent event : events) {
            try {
                MessageSavedEvent savedEvent =
                        objectMapper.readValue(
                                event.getPayload(),
                                MessageSavedEvent.class
                        );
                producerService.sendMessage(savedEvent);

                event.setSent(true);

                outboxRepository.save(event);
            } catch (Exception ex) {
                log.error(
                        """
                                Failed processing event:
                                id={}
                                topic={}
                                payload={}
                                error={}
                                """,
                        event.getId(),
                        event.getTopic(),
                        event.getPayload(),
                        ex.getMessage(),
                        ex
                );
            }
        }
    }
}
