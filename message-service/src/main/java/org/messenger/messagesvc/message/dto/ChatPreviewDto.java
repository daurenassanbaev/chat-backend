package org.messenger.messagesvc.message.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatPreviewDto(
        String peerId,
        UUID lastMessageId,
        String lastMessageContent,
        Instant lastMessageAt
) {}