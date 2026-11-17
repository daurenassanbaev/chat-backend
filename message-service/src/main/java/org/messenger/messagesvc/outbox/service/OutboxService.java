package org.messenger.messagesvc.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.chat.dto.ChatRecipientDto;
import org.messenger.messagesvc.outbox.entity.OutboxEvent;
import org.messenger.messagesvc.message.kafka.KafkaProducerService;
import org.messenger.messagesvc.outbox.repository.OutboxRepository;
import org.messenger.messagesvc.message.util.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.chat.shared.kafka.constants.KafkaTopics;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService producerService;
    private final ObjectMapper objectMapper;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    public void saveMessageSavedEvents(
            MessageSentEvent messageSentEvent,
            List<ChatRecipientDto> recipients
    ) {
        outboxRepository.saveAll(
                recipients.stream()
                        .map(recipient -> createOutboxEvent(messageSentEvent, recipient))
                        .toList()
        );
    }

    @Transactional
    public void processBatch() {
        List<OutboxEvent> events = outboxRepository.lockBatch(batchSize);

        for (OutboxEvent event : events) {
            try {
                MessageSavedEvent savedEvent =
                        objectMapper.readValue(
                                event.getPayload(),
                                MessageSavedEvent.class
                        );
                producerService.sendMessage(savedEvent).get(5, TimeUnit.SECONDS);

                event.setSent(true);


            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Outbox interrupted at {}", event.getId(), ex);
                break;
            } catch (Exception ex) {
                log.warn("Outbox send failed or result unknown at {}", event.getId(), ex);
                break;
            }
        }
    }

    @Transactional
    public int cleanupSentBatch() {
        return outboxRepository.deleteOldSentBatch(batchSize);
    }

    private OutboxEvent createOutboxEvent(
            MessageSentEvent messageSentEvent,
            ChatRecipientDto recipient
    ) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID());
            event.setTopic(KafkaTopics.CHAT_PROCESSED);
            event.setPayload(
                    objectMapper.writeValueAsString(
                            MessageMapper.toMessageSavedEvent(
                                    messageSentEvent,
                                    recipient.userId()
                            )
                    )
            );
            event.setSent(false);
            event.setCreatedAt(LocalDateTime.now());
            return event;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize message-saved event", ex);
        }
    }
}
