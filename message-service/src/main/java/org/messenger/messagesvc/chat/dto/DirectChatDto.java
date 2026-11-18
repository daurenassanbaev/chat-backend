package org.messenger.messagesvc.chat.dto;

import java.util.UUID;

public record DirectChatDto(UUID chatId, UUID peerId) {
}
