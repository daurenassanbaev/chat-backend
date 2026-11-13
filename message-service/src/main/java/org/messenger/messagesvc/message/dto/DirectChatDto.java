package org.messenger.messagesvc.message.dto;

import java.util.UUID;

public record DirectChatDto(UUID chatId, UUID peerId) {
}
