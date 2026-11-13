package com.messenger.chatgateway.messaging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.chatgateway.messaging.dto.MessageDto;
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

    private final ObjectMapper objectMapper;

    public void processMessage(MessageDto messageDto, String userId) {
        UUID chatId = messageDto.chatId() != null ? messageDto.chatId() : UUID.randomUUID();
        MessageSentEvent eventDto = MessageMapper.toMessageSentEvent(
                UUID.fromString(userId),
                UUID.randomUUID(),
                chatId,
                messageDto
        );

        kafkaProducerService.sendMessageSentEvent(eventDto);

        log.info("Message sent to Kafka: {}", eventDto);
    }

    public void sendMessage(MessageDeliveredEvent eventDto) {
        messagingTemplate.convertAndSendToUser(eventDto.recipientId().toString(), QUEUE_MESSAGES_DEST, eventDto);
    }

    public void sendOfflineMessages(List<String> offlineMessages) {

        offlineMessages
                .forEach(json -> {
                    try {
                        this.sendMessage(
                                objectMapper.readValue(
                                        json,
                                        MessageDeliveredEvent.class
                        ));

                    } catch (Exception e) {
                        log.error("Failed to parse offline message: {}", json, e);
                    }
                });
    }
}
