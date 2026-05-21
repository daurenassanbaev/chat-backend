package org.chat.shared.kafka.event;

import lombok.Builder;

@Builder
public record MessageDeliveredEvent(
        String fromUserId,
        String toUserId,
        String content
){}
