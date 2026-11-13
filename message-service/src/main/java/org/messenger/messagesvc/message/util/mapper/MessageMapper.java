package org.messenger.messagesvc.message.util.mapper;

import lombok.experimental.UtilityClass;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.entity.Message;

import java.util.UUID;

@UtilityClass
public class MessageMapper {

    public MessageSavedEvent toMessageSavedEvent(
            MessageSentEvent messageSentEvent,
            UUID recipientId
    ) {

        return MessageSavedEvent.builder()
                .messageId(messageSentEvent.messageId())
                .chatId(messageSentEvent.chatId())
                .senderId(messageSentEvent.senderId())
                .recipientId(recipientId)
                .content(messageSentEvent.content())
                .createdAt(messageSentEvent.createdAt())
                .build();
    }

    public MessageDto toMessageDto(
            Message message
    ) {
        return MessageDto.from(message);
    }
}
