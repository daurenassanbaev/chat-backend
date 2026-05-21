package org.chat.shared.kafka.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MessageSentEvent(
        UUID messageId,
        String fromUserId,
        String toUserId,
        String content,
        Instant createdAt
) {}
