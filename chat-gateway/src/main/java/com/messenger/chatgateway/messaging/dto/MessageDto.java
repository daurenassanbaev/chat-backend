package com.messenger.chatgateway.messaging.dto;

import java.util.UUID;
import java.util.List;

public record MessageDto (
        UUID chatId,
        List<UUID> recipientIds,
        String text,
        UUID clientMessageId
) {
    public MessageDto(UUID chatId, List<UUID> recipientIds, String text) {
        this(chatId, recipientIds, text, null);
    }
}
