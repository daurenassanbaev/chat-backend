package com.messenger.chatgateway.messaging.dto;
import java.util.UUID;
// UI correlation only; neither this record nor clientMessageId is persisted.
public record SendAccepted(UUID clientMessageId, UUID messageId) {}
