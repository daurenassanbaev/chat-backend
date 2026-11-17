package com.messenger.chatgateway.offline.service;

import lombok.RequiredArgsConstructor;
import org.chat.shared.caching.RedisConstants;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageReceivedEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class MessageAckService {
    private final OfflineMessageService pending;
    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, Object> kafka;

    public void acknowledge(String userId, UUID messageId) {
        if (messageId == null) throw new IllegalArgumentException("messageId is required");
        var message = pending.readPending(userId).stream()
                .filter(value -> messageId.equals(value.messageId())).findFirst();
        if (message.isEmpty()) return; // Repeated ACK, or not a pending message of this user.
        var value = message.orElseThrow();
        if (!userId.equals(value.recipientId().toString())) {
            throw new IllegalArgumentException("ACK recipient mismatch");
        }
        var event = new MessageReceivedEvent(messageId, value.chatId(), value.recipientId());
        try {
            kafka.send(KafkaTopics.CHAT_RECEIVED, userId, event).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ACK interrupted; pending kept", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("ACK publication failed or unknown; pending kept", e);
        }
        redis.opsForHash().delete(RedisConstants.OFFLINE_KEY.formatted(userId), messageId.toString());
    }
}
