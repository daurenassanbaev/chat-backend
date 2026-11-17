package org.messenger.messagesvc;

import org.awaitility.Awaitility;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.caching.constants.RedisKeys;
import org.messenger.messagesvc.caching.service.RedisService;
import org.junit.jupiter.api.Test;
import org.messenger.messagesvc.message.entity.MessageStatus;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.messenger.messagesvc.message.repository.OutboxRepository;
import org.messenger.messagesvc.message.service.OutboxService;
import org.messenger.testsupport.KafkaRecordReader;
import org.messenger.testsupport.PostgresKafkaRedisIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MessageServiceIntegrationTests extends PostgresKafkaRedisIntegrationTest {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private RedisService redisService;

    @Test
    void consumesMessagePersistsItAndOutboxPollerPublishesForDelivery() throws Exception {
        MessageSentEvent event = messageEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        try (KafkaRecordReader records = new KafkaRecordReader()) {
            publishMessage(event);

            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var saved = messageRepository.findById(event.messageId()).orElseThrow();
                assertThat(saved.getContent()).isEqualTo(event.content());
                assertThat(saved.getStatus()).isEqualTo(MessageStatus.SENT);
                assertThat(outboxFor(event.messageId())).hasSize(1);
                assertThat(redisService.getZSetSize(RedisKeys.LAST_MSG_KEY.formatted(event.chatId())))
                        .isEqualTo(1L);
            });

            outboxService.processAll();

            String published = records.awaitValue(
                    KafkaTopics.CHAT_PROCESSED,
                    value -> value.contains(event.messageId().toString()),
                    Duration.ofSeconds(20)
            );
            assertThat(published).contains(event.recipientIds().getFirst().toString());
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(outboxFor(event.messageId()).getFirst().getSent()).isTrue());
        }
    }

    @Test
    void repeatedMessageIdCreatesOnlyOneMessageAndOneOutboxRecord() throws Exception {
        UUID chatId = UUID.randomUUID();
        MessageSentEvent duplicate = messageEvent(UUID.randomUUID(), chatId, UUID.randomUUID());
        MessageSentEvent barrier = MessageSentEvent.builder()
                .messageId(UUID.randomUUID())
                .chatId(chatId)
                .senderId(duplicate.senderId())
                .recipientIds(duplicate.recipientIds())
                .content("barrier " + UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        publishMessage(duplicate);
        publishMessage(duplicate);
        publishMessage(barrier);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(messageRepository.existsById(barrier.messageId())).isTrue());

        assertThat(messageRepository.findById(duplicate.messageId())).isPresent();
        assertThat(outboxFor(duplicate.messageId())).hasSize(1);
    }

    @Test
    void invalidMessageIsSentToDeadLetterTopic() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageSentEvent invalid = MessageSentEvent.builder()
                .messageId(messageId)
                .chatId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .recipientIds(List.of())
                .content("invalid")
                .createdAt(Instant.now())
                .build();

        try (KafkaRecordReader records = new KafkaRecordReader()) {
            publishMessage(invalid);

            String deadLetter = records.awaitValue(
                    KafkaTopics.CHAT_MESSAGES + ".dlt",
                    value -> value.contains(messageId.toString()),
                    Duration.ofSeconds(30)
            );
            assertThat(deadLetter).contains("invalid");
            assertThat(messageRepository.existsById(messageId)).isFalse();
        }
    }

    @Test
    void deliveredEventUpdatesPersistedMessageStatus() throws Exception {
        MessageSentEvent sent = messageEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        publishMessage(sent);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(messageRepository.findById(sent.messageId())).isPresent());

        MessageDeliveredEvent delivered = MessageDeliveredEvent.builder()
                .messageId(sent.messageId())
                .chatId(sent.chatId())
                .senderId(sent.senderId())
                .recipientId(sent.recipientIds().getFirst())
                .content(sent.content())
                .createdAt(sent.createdAt())
                .build();
        kafkaTemplate.send(KafkaTopics.CHAT_DELIVERED, sent.chatId().toString(), delivered)
                .get(10, TimeUnit.SECONDS);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(messageRepository.findById(sent.messageId()).orElseThrow().getStatus())
                        .isEqualTo(MessageStatus.DELIVERED));
    }

    private void publishMessage(MessageSentEvent event) throws Exception {
        kafkaTemplate.send(KafkaTopics.CHAT_MESSAGES, event.chatId().toString(), event)
                .get(10, TimeUnit.SECONDS);
    }

    private List<org.messenger.messagesvc.message.entity.OutboxEvent> outboxFor(UUID messageId) {
        return outboxRepository.findAll().stream()
                .filter(outbox -> outbox.getPayload().contains(messageId.toString()))
                .toList();
    }

    private MessageSentEvent messageEvent(UUID messageId, UUID chatId, UUID recipientId) {
        return MessageSentEvent.builder()
                .messageId(messageId)
                .chatId(chatId)
                .senderId(UUID.randomUUID())
                .recipientIds(List.of(recipientId))
                .content("integration message " + messageId)
                .createdAt(Instant.now())
                .build();
    }
}
