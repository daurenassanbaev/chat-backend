package com.messenger.chatgateway.offline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.chat.shared.caching.PendingMessageScripts;
import org.chat.shared.caching.RedisConstants;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OfflineMessageService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private static final RedisScript<List> READ = RedisScript.of(PendingMessageScripts.READ, List.class);

    public List<MessageDeliveredEvent> readPending(String userId) {
        List<?> values = redis.execute(READ, List.of(RedisConstants.OFFLINE_KEY.formatted(userId)));
        if (values == null) return List.of();
        return values.stream().map(value -> parse((String) value))
                .sorted(Comparator.comparing(MessageDeliveredEvent::createdAt)
                        .thenComparing(MessageDeliveredEvent::messageId)).toList();
    }

    private MessageDeliveredEvent parse(String json) {
        try {
            return mapper.readValue(json, MessageDeliveredEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid pending message; kept in Redis", e);
        }
    }
}
