package org.messenger.messagesvc.message.dto;

import org.messenger.messagesvc.message.entity.Message;
import org.messenger.messagesvc.message.entity.MessageStatus;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID chatId,
        UUID senderId,
        String content,
        Instant createdAt,
        MessageStatus status
) {

    public static MessageDto from(Message message) {
        return new MessageDto(
                message.getId(),
                message.getChatId(),
                message.getSenderId(),
                message.getContent(),
                message.getCreatedAt(),
                message.getStatus()
        );
    }
}
