package com.messenger.chatgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.chatgateway.messaging.dto.MessageDto;
import com.messenger.chatgateway.messaging.dto.SendAccepted;
import com.messenger.chatgateway.messaging.dto.SendError;
import com.messenger.chatgateway.messaging.kafka.KafkaProducerService;
import com.messenger.chatgateway.messaging.service.ChatService;
import com.messenger.chatgateway.offline.service.MessageAckService;
import org.assertj.core.api.Assertions;
import org.chat.shared.caching.RedisConstants;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.junit.jupiter.api.Test;
import org.messenger.testsupport.KafkaRecordReader;
import org.messenger.testsupport.KafkaRedisIntegrationTest;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ChatGatewayIntegrationTests extends KafkaRedisIntegrationTest {

    @Autowired
    private ChatService chatService;

    @MockitoSpyBean(name = "kafkaTemplate")
    private KafkaTemplate kafkaTemplate;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readsOfflineJsonWithInstant() throws Exception {
        Instant createdAt = Instant.parse("2026-11-17T10:00:00Z");
        String json = "{\"createdAt\":\"" + createdAt + "\"}";
        assertThat(objectMapper.readValue(json, MessageDeliveredEvent.class).createdAt())
                .isEqualTo(createdAt);
        assertThat(objectMapper.writeValueAsString(createdAt)).isEqualTo("\"" + createdAt + "\"");
    }

    @Test
    void acceptsChatMessageAndPublishesItToKafka() throws Exception {
        UUID chatId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        String text = "gateway integration " + UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String senderId = UUID.randomUUID().toString();

        try (KafkaRecordReader records = new KafkaRecordReader()) {
            chatService.processMessage(new MessageDto(chatId, List.of(recipientId), text, clientId), senderId);

            String sent = records.awaitValue(
                    KafkaTopics.CHAT_MESSAGES,
                    value -> value.contains(text),
                    Duration.ofSeconds(20)
            );
            assertThat(sent).contains(chatId.toString()).contains(recipientId.toString());
            UUID serverId = UUID.fromString(objectMapper.readTree(sent).get("messageId").asText());
            verify(messagingTemplate, timeout(5000)).convertAndSendToUser(eq(senderId), eq("/queue/send-results"),
                    eq(new SendAccepted(clientId, serverId)));
        }
    }

    @Test
    void consumesDeliveredEventAndRoutesItToTheRecipient() throws Exception {
        UUID recipientId = UUID.randomUUID();
        MessageDeliveredEvent event = MessageDeliveredEvent.builder()
                .messageId(UUID.randomUUID())
                .chatId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .recipientId(recipientId)
                .content("gateway delivery " + UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        kafkaTemplate.send(KafkaTopics.CHAT_DELIVERED, recipientId.toString(), event)
                .get(10, TimeUnit.SECONDS);

        verify(messagingTemplate, timeout(15_000)).convertAndSendToUser(
                eq(recipientId.toString()),
                eq(ChatService.QUEUE_MESSAGES_DEST),
                eq(event)
        );
    }
    @MockitoSpyBean
    private KafkaProducerService producer;

    @Test
    void reportsSynchronousAndAsynchronousSendErrors() {
        for (boolean synchronous : List.of(true, false)) {
            UUID clientId = UUID.randomUUID();
            String user = UUID.randomUUID().toString();
            Mockito.doAnswer(invocation -> {
                if (synchronous) throw new IllegalStateException("sync failure");
                return CompletableFuture.failedFuture(new IllegalStateException("async failure"));
            }).when(producer).sendMessageSentEvent(ArgumentMatchers.any());
            chatService.processMessage(new MessageDto(UUID.randomUUID(), List.of(), "test", clientId), user);
            verify(messagingTemplate).convertAndSendToUser(eq(user), eq("/queue/errors"),
                    ArgumentMatchers.argThat(value -> value instanceof
                            SendError error
                            && clientId.equals(error.clientMessageId())));
        }
    }
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private MessageAckService ackService;

    @Test
    void failedAckPublicationKeepsPendingMessage() throws Exception {
        UUID recipient = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var event = new MessageDeliveredEvent(id, UUID.randomUUID(), UUID.randomUUID(), recipient,
                "pending", Instant.now());
        String key = RedisConstants.OFFLINE_KEY.formatted(recipient);
        redis.opsForHash().put(key, id.toString(), objectMapper.writeValueAsString(event));
        Mockito.doAnswer(call -> {
            if (KafkaTopics.CHAT_RECEIVED.equals(call.getArgument(0))) {
                return CompletableFuture.failedFuture(new IllegalStateException("Kafka down"));
            }
            return call.callRealMethod();
        }).when(kafkaTemplate).send(ArgumentMatchers.anyString(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
        Assertions.assertThatThrownBy(() -> ackService.acknowledge(recipient.toString(), id))
                .isInstanceOf(IllegalStateException.class);
        assertThat(redis.opsForHash().hasKey(key, id.toString())).isTrue();
    }
}
