package org.messenger.messagesvc.message.poller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.messenger.messagesvc.message.entity.OutboxEvent;
import org.messenger.messagesvc.message.kafka.KafkaProducerService;
import org.messenger.messagesvc.message.repository.OutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService producerService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishEvents() {

        log.info("Outbox poller started");

        List<OutboxEvent> events =
                outboxRepository.findBySentFalse();

        log.info(
                "Found {} unsent events",
                events.size()
        );

        for (OutboxEvent event : events) {

            try {

                log.info(
                        """
                        Processing event:
                        id={}
                        topic={}
                        sent={}
                        createdAt={}
                        payload={}
                        """,
                        event.getId(),
                        event.getTopic(),
                        event.getSent(),
                        event.getCreatedAt(),
                        event.getPayload()
                );

                MessageSavedEvent savedEvent =
                        objectMapper.readValue(
                                event.getPayload(),
                                MessageSavedEvent.class
                        );

                log.info(
                        """
                        Parsed event:
                        messageId={}
                        fromUserId={}
                        toUserId={}
                        """,
                        savedEvent.messageId(),
                        savedEvent.fromUserId(),
                        savedEvent.toUserId()
                );

                producerService.sendMessage(
                        savedEvent
                );

                log.info(
                        "Kafka send successful for outboxId={}",
                        event.getId()
                );

                log.info(
                        "Before sent update: id={} sent={}",
                        event.getId(),
                        event.getSent()
                );

                event.setSent(true);

                // временно для проверки
                outboxRepository.save(event);

                log.info(
                        "After sent update: id={} sent={}",
                        event.getId(),
                        event.getSent()
                );

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

        log.info("Outbox poller finished");
    }
}