package org.messenger.messagesvc.message.dto;

import java.util.UUID;

public record CreateDirectChatRequest(UUID recipientId) {
}
