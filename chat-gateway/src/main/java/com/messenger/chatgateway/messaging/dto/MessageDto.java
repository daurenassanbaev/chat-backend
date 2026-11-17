package com.messenger.chatgateway.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MessageDto (
        UUID chatId,
        List<UUID> recipientIds,
        @NotBlank
        @Size(max = 10000) String text,
        UUID clientMessageId
) {
    public MessageDto(UUID chatId, List<UUID> recipientIds, String text) {
        this(chatId, recipientIds, text, null);
    }
}
