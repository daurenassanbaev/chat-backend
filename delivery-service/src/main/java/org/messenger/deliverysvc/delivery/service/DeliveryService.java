package org.messenger.deliverysvc.delivery.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.chat.shared.caching.PendingMessageScripts;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.messenger.deliverysvc.delivery.kafka.KafkaProducerService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.chat.shared.caching.RedisConstants.*;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final KafkaProducerService producerService;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private static final RedisScript<Long> SAVE_PENDING = RedisScript.of(PendingMessageScripts.SAVE, Long.class);

    public void processMessageEvent(MessageSavedEvent eventDto) {
        MessageDeliveredEvent delivered = buildMessageDeliveredEvent(eventDto);
        try {
            redis.execute(SAVE_PENDING, List.of(OFFLINE_KEY.formatted(eventDto.recipientId())),
                    eventDto.messageId().toString(), objectMapper.writeValueAsString(delivered));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize pending message", e);
        }
        Long active = redis.opsForZSet().count(PRESENCE_SESSIONS_KEY.formatted(eventDto.recipientId()),
                System.currentTimeMillis() + 1d, Double.POSITIVE_INFINITY);
        if (active != null && active > 0) {
            producerService.sendDeliveredEvent(delivered);
        }
    }

    private MessageDeliveredEvent buildMessageDeliveredEvent(MessageSavedEvent eventDto) {
        return MessageDeliveredEvent.builder()
                .messageId(eventDto.messageId())
                .chatId(eventDto.chatId())
                .senderId(eventDto.senderId())
                .recipientId(eventDto.recipientId())
                .content(eventDto.content())
                .createdAt(eventDto.createdAt())
                .build();
    }
}
