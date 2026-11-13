package org.messenger.messagesvc.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.message.dto.ChatRecipientDto;
import org.messenger.messagesvc.message.entity.OutboxEvent;
import org.messenger.messagesvc.message.kafka.KafkaProducerService;
import org.messenger.messagesvc.message.repository.OutboxRepository;
import org.messenger.messagesvc.message.util.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService producerService;
    private final ObjectMapper objectMapper;

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

    private OutboxEvent createOutboxEvent(
            MessageSentEvent messageSentEvent,
            ChatRecipientDto recipient
    ) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID());
            event.setTopic("message-saved");
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
