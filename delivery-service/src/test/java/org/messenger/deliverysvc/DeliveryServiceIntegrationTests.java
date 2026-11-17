package org.messenger.deliverysvc;

import org.awaitility.Awaitility;
import org.chat.shared.caching.RedisConstants;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.junit.jupiter.api.Test;
import org.messenger.testsupport.KafkaRecordReader;
import org.messenger.testsupport.KafkaRedisIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DeliveryServiceIntegrationTests extends KafkaRedisIntegrationTest {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void onlineRecipientReceivesDeliveredEventOnKafka() throws Exception {
        MessageSavedEvent event = event();
        redisTemplate.opsForValue().set(RedisConstants.ONLINE_KEY.formatted(event.recipientId()), "online");

        try (KafkaRecordReader records = new KafkaRecordReader()) {
            kafkaTemplate.send(KafkaTopics.CHAT_PROCESSED, event.recipientId().toString(), event)
                    .get(10, TimeUnit.SECONDS);

            String delivered = records.awaitValue(
                    KafkaTopics.CHAT_DELIVERED,
                    value -> value.contains(event.messageId().toString()),
                    Duration.ofSeconds(20)
            );
            assertThat(delivered)
                    .contains(event.chatId().toString())
                    .contains(event.recipientId().toString())
                    .contains(event.content());
        }
    }

    @Test
    void offlineRecipientMessageIsQueuedInRedis() throws Exception {
        MessageSavedEvent event = event();
        String offlineKey = RedisConstants.OFFLINE_KEY.formatted(event.recipientId());
        redisTemplate.delete(offlineKey);

        kafkaTemplate.send(KafkaTopics.CHAT_PROCESSED, event.recipientId().toString(), event)
                .get(10, TimeUnit.SECONDS);

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var queued = redisTemplate.opsForList().range(offlineKey, 0, -1);
            assertThat(queued).isNotNull().anySatisfy(json ->
                    assertThat(json).contains(event.messageId().toString()).contains(event.content()));
        });
    }

    private MessageSavedEvent event() {
        return MessageSavedEvent.builder()
                .messageId(UUID.randomUUID())
                .chatId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .content("delivery integration " + UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }
}
