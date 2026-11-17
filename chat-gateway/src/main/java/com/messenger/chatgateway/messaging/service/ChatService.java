package com.messenger.chatgateway.messaging.service;

import com.messenger.chatgateway.messaging.dto.MessageDto;
import com.messenger.chatgateway.messaging.dto.SendError;
import com.messenger.chatgateway.messaging.kafka.KafkaProducerService;
import com.messenger.chatgateway.messaging.util.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    public static final String QUEUE_MESSAGES_DEST = "/queue/messages";

    private final KafkaProducerService kafkaProducerService;
    private final SimpMessagingTemplate messagingTemplate;


    public void processMessage(MessageDto messageDto, String userId) {
        UUID chatId = messageDto.chatId() != null ? messageDto.chatId() : UUID.randomUUID();
        MessageSentEvent eventDto = MessageMapper.toMessageSentEvent(
                UUID.fromString(userId),
                UUID.randomUUID(),
                chatId,
                messageDto
        );

        try {
            kafkaProducerService.sendMessageSentEvent(eventDto).whenComplete((result, error) -> {
                if (error != null) {
                    reportSendFailure(userId, messageDto.clientMessageId(), error);
                } else {
                    log.info("Message queued in Kafka: {}", eventDto.messageId());
                }
            });
        } catch (RuntimeException error) {
            reportSendFailure(userId, messageDto.clientMessageId(), error);
        }
    }

    private void reportSendFailure(String userId, UUID clientMessageId, Throwable error) {
        log.warn("Kafka send failed for client message {}", clientMessageId, error);
        messagingTemplate.convertAndSendToUser(userId, "/queue/errors",
                new SendError(clientMessageId, "Не отправлено: ошибка Kafka или результат неизвестен"));
    }

    public void sendMessage(MessageDeliveredEvent eventDto) {
        messagingTemplate.convertAndSendToUser(eventDto.recipientId().toString(), QUEUE_MESSAGES_DEST, eventDto);
    }

}
