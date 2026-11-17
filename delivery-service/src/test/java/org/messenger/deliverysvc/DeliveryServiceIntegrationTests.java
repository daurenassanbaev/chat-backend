package org.messenger.deliverysvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.chat.shared.caching.RedisConstants;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.junit.jupiter.api.Test;
import org.messenger.deliverysvc.delivery.service.DeliveryService;
import org.messenger.testsupport.KafkaRecordReader;
import org.messenger.testsupport.KafkaRedisIntegrationTest;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DeliveryServiceIntegrationTests extends KafkaRedisIntegrationTest {

    @MockitoSpyBean(name = "kafkaTemplate")
    private KafkaTemplate kafkaTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void onlineRecipientReceivesDeliveredEventOnKafka() throws Exception {
        MessageSavedEvent event = event();
        redisTemplate.opsForZSet().add(RedisConstants.PRESENCE_SESSIONS_KEY.formatted(event.recipientId()), "session", System.currentTimeMillis() + 60000);

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
            var queued = redisTemplate.<String, String>opsForHash().values(offlineKey);
            assertThat(queued).isNotNull().anySatisfy(json ->
                    assertThat(json).contains(event.messageId().toString()).contains(event.content()));
        });
    }

    @Test
    void failedOutgoingSendRetriesTheIncomingRecord() throws Exception {
        var event = event();
        redisTemplate.opsForZSet().add(RedisConstants.PRESENCE_SESSIONS_KEY.formatted(event.recipientId()), "session", System.currentTimeMillis() + 60000);
        var attempts = new AtomicInteger();
        Mockito.doAnswer(invocation -> {
            Object value = invocation.getArgument(2);
            if (KafkaTopics.CHAT_DELIVERED.equals(invocation.getArgument(0))
                    && value instanceof MessageDeliveredEvent delivered
                    && event.messageId().equals(delivered.messageId())) {
                if (attempts.incrementAndGet() == 1) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Injected asynchronous producer failure"));
                }
            }
            return invocation.callRealMethod();
        }).when(kafkaTemplate).send(ArgumentMatchers.anyString(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
        try (var records = new KafkaRecordReader()) {
            kafkaTemplate.send(KafkaTopics.CHAT_PROCESSED, event.recipientId().toString(), event)
                    .get(10, TimeUnit.SECONDS);
            records.awaitValue(KafkaTopics.CHAT_DELIVERED,
                    value -> value.contains(event.messageId().toString()), Duration.ofSeconds(20));
            assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void permanentlyInvalidEventGoesToProcessedDlt() throws Exception {
        var event = new MessageSavedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "invalid-recipient", Instant.now());
        redisTemplate.opsForValue().set(RedisConstants.OFFLINE_KEY.formatted(event.recipientId()), "wrong-type");
        try (var records = new KafkaRecordReader()) {
            kafkaTemplate.send(KafkaTopics.CHAT_PROCESSED, "invalid", event).get(10, TimeUnit.SECONDS);
            assertThat(records.awaitValue(KafkaTopics.CHAT_PROCESSED + ".dlt",
                    value -> value.contains(event.messageId().toString()), Duration.ofSeconds(20)))
                    .contains("invalid-recipient");
        }
    }

    @Test
    void repeatedEventUsesOneHashFieldAndRefreshesTtl() throws Exception {
        var event = event();
        var service = applicationService();
        service.processMessageEvent(event);
        service.processMessageEvent(event);
        String key = RedisConstants.OFFLINE_KEY.formatted(event.recipientId());
        assertThat(redisTemplate.opsForHash().size(key)).isEqualTo(1);
        assertThat(redisTemplate.getExpire(key)).isBetween(2591900L, 2592000L);
    }

    @Autowired
    private DeliveryService deliveryService;
    private DeliveryService applicationService() {
        return deliveryService;
    }

    @Test
    void existingListIsMigratedWithoutDroppingMessages() throws Exception {
        var old = event();
        String key = RedisConstants.OFFLINE_KEY.formatted(old.recipientId());
        var mapper = new ObjectMapper().findAndRegisterModules();
        redisTemplate.opsForList().rightPush(key, mapper.writeValueAsString(old));
        var next = new MessageSavedEvent(UUID.randomUUID(), old.chatId(), old.senderId(), old.recipientId(),
                "next", Instant.now());
        deliveryService.processMessageEvent(next);
        assertThat(redisTemplate.opsForHash().size(key)).isEqualTo(2);
        assertThat(redisTemplate.opsForHash().hasKey(key, old.messageId().toString())).isTrue();
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
