package com.messenger.chatgateway;

import org.chat.shared.kafka.constants.KafkaTopics;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.junit.jupiter.api.Test;
import com.messenger.chatgateway.messaging.dto.MessageDto;
import com.messenger.chatgateway.messaging.service.ChatService;
import org.messenger.testsupport.KafkaRecordReader;
import org.messenger.testsupport.KafkaRedisIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ChatGatewayIntegrationTests extends KafkaRedisIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void readsOfflineJsonWithInstant() throws Exception {
        Instant createdAt = Instant.parse("2026-11-17T10:00:00Z");
        String json = "{\"createdAt\":\"" + createdAt + "\"}";
        assertThat(objectMapper.readValue(json, MessageDeliveredEvent.class).createdAt())
                .isEqualTo(createdAt);
        assertThat(objectMapper.writeValueAsString(createdAt)).isEqualTo("\"" + createdAt + "\"");
    }

    @Test
    void acceptsChatMessageAndPublishesItToKafka() {
        UUID chatId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        String text = "gateway integration " + UUID.randomUUID();

        try (KafkaRecordReader records = new KafkaRecordReader()) {
            chatService.processMessage(new MessageDto(chatId, List.of(recipientId), text), UUID.randomUUID().toString());

            String sent = records.awaitValue(
                    KafkaTopics.CHAT_MESSAGES,
                    value -> value.contains(text),
                    Duration.ofSeconds(20)
            );
            assertThat(sent).contains(chatId.toString()).contains(recipientId.toString());
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
}
