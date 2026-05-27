package org.messenger.messagesvc.message.util.mapper;

import lombok.experimental.UtilityClass;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.entity.Message;

@UtilityClass
public class MessageMapper {

    public MessageSavedEvent toMessageSavedEvent(
            MessageSentEvent messageSentEvent
    ) {

        return MessageSavedEvent.builder()
                .fromUserId(messageSentEvent.fromUserId())
                .content(messageSentEvent.content())
                .toUserId(messageSentEvent.toUserId())
                .messageId(messageSentEvent.messageId())
                .createdAt(messageSentEvent.createdAt())
                .build();
    }

    public MessageDto toMessageDto(
            Message message
    ) {

        return new MessageDto(
                message.getId(),
                message.getFromUserId(),
                message.getToUserId(),
                message.getContent(),
                message.getCreated_at(),
                message.getStatus()
        );
    }
}