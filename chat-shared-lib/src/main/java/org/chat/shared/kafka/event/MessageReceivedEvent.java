package org.chat.shared.kafka.event;
import java.util.UUID;
public record MessageReceivedEvent(UUID messageId, UUID chatId, UUID recipientId) {}
