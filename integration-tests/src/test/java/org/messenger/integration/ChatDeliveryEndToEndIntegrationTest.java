package org.messenger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.messenger.chatgateway.ChatGatewayApplication;
import com.messenger.chatgateway.messaging.dto.MessageDto;
import com.messenger.chatgateway.messaging.service.ChatService;
import com.messenger.chatgateway.caching.service.RedisService;
import org.chat.shared.caching.RedisConstants;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.messenger.deliverysvc.DeliveryServiceApplication;
import org.messenger.messagesvc.MessageServiceApplication;
import org.messenger.messagesvc.message.entity.MessageStatus;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.messenger.messagesvc.message.repository.OutboxRepository;
import org.messenger.testsupport.KafkaRecordReader;
import org.messenger.testsupport.PostgresKafkaRedisIntegrationTest;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatDeliveryEndToEndIntegrationTest extends PostgresKafkaRedisIntegrationTest {

    private static ConfigurableApplicationContext messageService;
    private static ConfigurableApplicationContext deliveryService;
    private static ConfigurableApplicationContext chatGateway;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void startServices() {
        Map<String, Object> infrastructure = Map.of(
                "spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers(),
                "spring.data.redis.host", REDIS.getHost(),
                "spring.data.redis.port", REDIS.getFirstMappedPort(),
                "spring.datasource.url", POSTGRES.getJdbcUrl(),
                "spring.datasource.username", POSTGRES.getUsername(),
                "spring.datasource.password", POSTGRES.getPassword(),
                "spring.datasource.driver-class-name", POSTGRES.getDriverClassName()
        );

        messageService = start(MessageServiceApplication.class, infrastructure, true, "e2e-message.yml");
        deliveryService = start(DeliveryServiceApplication.class, infrastructure, false, "e2e-delivery.yml");
        chatGateway = start(ChatGatewayApplication.class, infrastructure, true, "e2e-gateway.yml");
    }

    @AfterAll
    static void stopServices() {
        close(chatGateway);
        close(deliveryService);
        close(messageService);
    }

    @Test
    void gatewayMessageIsPersistedDeliveredAndMarkedDelivered() throws Exception {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        JsonNode chat = createDirectChat(senderId, recipientId);
        UUID chatId = UUID.fromString(chat.get("chatId").asText());
        String body = "e2e message " + UUID.randomUUID();

        chatGateway.getBean(RedisService.class).addWithTtl(
                RedisConstants.ONLINE_KEY.formatted(recipientId), "online", Duration.ofMinutes(1));
        try (KafkaRecordReader records = new KafkaRecordReader()) {
            chatGateway.getBean(ChatService.class).processMessage(
                    new MessageDto(chatId, List.of(recipientId), body), senderId.toString());

            String deliveredEvent = records.awaitValue(
                    KafkaTopics.CHAT_DELIVERED,
                    value -> value.contains(body),
                    Duration.ofSeconds(30)
            );
            assertThat(deliveredEvent).contains(chatId.toString()).contains(recipientId.toString());
        }

        var pending = chatGateway.getBean(com.messenger.chatgateway.messaging.service.OfflineMessageService.class)
                .readPending(recipientId.toString());
        chatGateway.getBean(com.messenger.chatgateway.messaging.service.MessageAckService.class)
                .acknowledge(recipientId.toString(), pending.getFirst().messageId());

        MessageRepository messages = messageService.getBean(MessageRepository.class);
        OutboxRepository outbox = messageService.getBean(OutboxRepository.class);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var saved = messages.findAllByChatIdInOrderByCreatedAtDesc(List.of(chatId)).stream()
                    .filter(message -> message.getContent().equals(body))
                    .findFirst()
                    .orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(MessageStatus.DELIVERED);
            assertThat(outbox.findAll().stream()
                    .filter(event -> event.getPayload().contains(saved.getId().toString())))
                    .isNotEmpty()
                    .allMatch(event -> Boolean.TRUE.equals(event.getSent()));
        });
    }

    @Test
    void offlineMessageIsReplayedUntilBrowserAcknowledges() throws Exception {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID chatId = UUID.fromString(createDirectChat(sender, recipient).get("chatId").asText());
        chatGateway.getBean(ChatService.class).processMessage(
                new MessageDto(chatId, List.of(recipient), "offline-reconnect"), sender.toString());
        var pending = chatGateway.getBean(com.messenger.chatgateway.messaging.service.OfflineMessageService.class);
        Awaitility.await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                assertThat(pending.readPending(recipient.toString())).hasSize(1));
        UUID messageId = pending.readPending(recipient.toString()).getFirst().messageId();
        try (var client = new org.messenger.testsupport.StompTestClient(port(chatGateway), recipient.toString())) {
            client.subscribe();
            assertThat(client.receive(Duration.ofSeconds(10))).contains(messageId.toString());
            // Deliberately disconnect without application ACK.
        }
        assertThat(pending.readPending(recipient.toString())).hasSize(1);
        assertThat(messageService.getBean(MessageRepository.class).findById(messageId).orElseThrow().getStatus())
                .isEqualTo(MessageStatus.SENT);
        try (var client = new org.messenger.testsupport.StompTestClient(port(chatGateway), recipient.toString())) {
            client.subscribe();
            assertThat(client.receive(Duration.ofSeconds(10))).contains(messageId.toString());
            client.send("/app/messages/ack", "{\"messageId\":\"" + messageId + "\"}");
            Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(pending.readPending(recipient.toString())).isEmpty();
                assertThat(messageService.getBean(MessageRepository.class).findById(messageId).orElseThrow().getStatus())
                        .isEqualTo(MessageStatus.DELIVERED);
            });
            client.send("/app/messages/ack", "{\"messageId\":\"" + messageId + "\"}");
        }
    }

    private static JsonNode createDirectChat(UUID senderId, UUID recipientId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("user-id", senderId.toString());
        HttpEntity<String> request = new HttpEntity<>(
                OBJECT_MAPPER.writeValueAsString(Map.of("recipientId", recipientId)), headers);
        var response = new RestTemplate().exchange(
                "http://localhost:" + port(messageService) + "/chats/direct",
                HttpMethod.POST,
                request,
                String.class
        );
        return OBJECT_MAPPER.readTree(response.getBody());
    }

    private static ConfigurableApplicationContext start(
            Class<?> application,
            Map<String, Object> properties,
            boolean web,
            String configFile
    ) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(application)
                .web(web ? WebApplicationType.SERVLET : WebApplicationType.NONE);
        List<String> arguments = properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        arguments.add("--spring.config.location=classpath:/" + configFile);
        if (web) {
            arguments.add("--server.port=0");
        } else {
            arguments.add("--spring.main.web-application-type=none");
        }
        return builder.run(arguments.toArray(String[]::new));
    }

    private static int port(ConfigurableApplicationContext context) {
        return context.getEnvironment().getProperty("local.server.port", Integer.class);
    }

    private static void close(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }
}
