package org.messenger.deliverysvc.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.messenger.deliverysvc.caching.service.RedisService;
import org.messenger.deliverysvc.delivery.kafka.KafkaProducerService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.chat.shared.caching.RedisConstants.*;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final KafkaProducerService producerService;
    private final RedisService redisService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // REDIS
    // KAFKA

    public void processMessageEvent(MessageSavedEvent eventDto) throws ExecutionException, InterruptedException {
        String key = ONLINE_KEY.formatted(eventDto.toUserId());
        String onlineVal = redisService.get(key);

        if (Objects.isNull(onlineVal)) {
            String offlineKey = OFFLINE_KEY.formatted(eventDto.toUserId());

            try {
                String json = objectMapper.writeValueAsString(this.buildMessageDeliveredEvent(eventDto));
                redisService.lPush(offlineKey, json);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return;
        }

        producerService.sendDeliveredEvent(this.buildMessageDeliveredEvent(eventDto));
    }

    private MessageDeliveredEvent buildMessageDeliveredEvent(MessageSavedEvent eventDto) {
        return MessageDeliveredEvent.builder()
                .fromUserId(eventDto.fromUserId())
                .toUserId(eventDto.toUserId())
                .content(eventDto.content())
                .build();
    }
}
