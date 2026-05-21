package org.messenger.messagesvc.message.dto;

import org.messenger.messagesvc.message.entity.MessageStatus;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        String fromUserId,
        String toUserId,
        String content,
        Instant createdAt,
        MessageStatus status
) {}