package com.messenger.chatgateway.messaging.util.mapper;

import com.messenger.chatgateway.messaging.dto.MessageDto;
import lombok.experimental.UtilityClass;
import org.chat.shared.kafka.event.MessageSentEvent;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class MessageMapper {

    public MessageSentEvent toMessageSentEvent(
            UUID userId,
            UUID messageId,
            UUID chatId,
            MessageDto messageDto
    ) {
        return MessageSentEvent.builder()
                .chatId(chatId)
                .senderId(userId)
                .recipientIds(messageDto.recipientIds())
                .content(messageDto.text())
                .messageId(messageId)
                .createdAt(Instant.now())
                .build();
    }
}
