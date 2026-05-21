package org.chat.shared.kafka.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaTopics {

    public static final String CHAT_MESSAGES  = "chat.messages";   // gateway → message-svc
    public static final String CHAT_PROCESSED = "chat.processed";  // message-svc → delivery
    public static final String CHAT_DELIVERED = "chat.delivered";  // delivery → gateway

}
