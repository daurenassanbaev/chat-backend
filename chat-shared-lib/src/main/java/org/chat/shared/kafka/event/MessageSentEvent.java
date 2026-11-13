package org.chat.shared.kafka.event;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record MessageSentEvent(
        UUID messageId,
        UUID chatId,
        UUID senderId,
        List<UUID> recipientIds,
        String content,
        Instant createdAt
) {}
