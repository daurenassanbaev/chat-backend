package com.messenger.chatgateway.messaging.util.mapper;

import com.messenger.chatgateway.messaging.dto.MessageDto;
import lombok.experimental.UtilityClass;
import org.chat.shared.kafka.event.MessageSentEvent;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class MessageMapper {

    public MessageSentEvent toMessageSentEvent(String userId, UUID messageId, MessageDto messageDto) {
        return MessageSentEvent.builder()
                .fromUserId(userId)
                .content(messageDto.text())
                .toUserId(messageDto.toUserId())
                .messageId(messageId)
                .createdAt(Instant.now())
                .build();
    }
}
