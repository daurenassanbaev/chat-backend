package org.messenger.messagesvc.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.message.entity.Message;
import org.messenger.messagesvc.message.entity.MessageStatus;
import org.messenger.messagesvc.message.entity.OutboxEvent;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.messenger.messagesvc.message.repository.OutboxRepository;
import org.messenger.messagesvc.message.util.mapper.MessageEventMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(MessageSentEvent eventDto) {

        Message message = new Message();
        fillMessageEntity(eventDto, message);

        try {

            messageRepository.save(message);

            OutboxEvent event = new OutboxEvent();

            event.setId(UUID.randomUUID());
            event.setTopic("message-saved");
            event.setPayload(
                    objectMapper.writeValueAsString(
                            MessageEventMapper.toMessageSavedEvent(eventDto)
                    )
            );
            event.setSent(false);
            event.setCreatedAt(LocalDateTime.now());

            outboxRepository.save(event);

        } catch (DataIntegrityViolationException ex) {

            log.info(
                    "Message {} already processed",
                    eventDto.messageId()
            );

        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize event", ex);
        }
    }

    private void fillMessageEntity(MessageSentEvent eventDto, Message message) {
        message.setId(eventDto.messageId());
        message.setContent(eventDto.content());
        message.setFromUserId(eventDto.fromUserId());
        message.setToUserId(eventDto.toUserId());
        message.setCreated_at(eventDto.createdAt());
        message.setStatus(MessageStatus.SENT);
    }
}
