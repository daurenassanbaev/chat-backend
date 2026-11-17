package org.messenger.messagesvc.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatPreviewDto(
        UUID chatId,
        UUID peerId,
        UUID lastMessageId,
        String lastMessageContent,
        Instant lastMessageAt
) {}
