package org.chat.shared.kafka.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MessageSavedEvent(
        UUID messageId,
        UUID chatId,
        UUID senderId,
        UUID recipientId,
        String content,
        Instant createdAt
) {}
