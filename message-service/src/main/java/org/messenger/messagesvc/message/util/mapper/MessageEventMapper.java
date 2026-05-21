package org.messenger.messagesvc.message.util.mapper;

import lombok.experimental.UtilityClass;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.chat.shared.kafka.event.MessageSentEvent;

@UtilityClass
public class MessageEventMapper {

    public MessageSavedEvent toMessageSavedEvent(MessageSentEvent messageSentEvent) {
        return MessageSavedEvent.builder()
                .fromUserId(messageSentEvent.fromUserId())
                .content(messageSentEvent.content())
                .toUserId(messageSentEvent.toUserId())
                .messageId(messageSentEvent.messageId())
                .createdAt(messageSentEvent.createdAt())
                .build();
    }
}
