package com.messenger.chatgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.chatgateway.presence.service.PresenceService;
import org.chat.shared.caching.RedisConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.messenger.testsupport.TestTokenIssuer;
import org.messenger.testsupport.KafkaRecordReader;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.messenger.testsupport.KafkaRedisIntegrationTest;
import org.messenger.testsupport.StompTestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PresenceIntegrationTests extends KafkaRedisIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired StringRedisTemplate redis;
    @Autowired PresenceService service;

    static final TestTokenIssuer tokens = new TestTokenIssuer();

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", tokens::issuer);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> tokens.issuer() + "/certs");
    }

    @AfterAll
    static void closeIssuer() {
        tokens.close();
    }

    @Test
    void invalidConnectionsReceiveAnError() throws Exception {
        var subject = UUID.randomUUID().toString();
        try (var otherIssuer = new TestTokenIssuer()) {
            for (String token : java.util.Arrays.asList(null, tokens.token(subject, Instant.now().minusSeconds(1)),
                    otherIssuer.token(subject))) {
                assertThatThrownBy(() -> new StompTestClient(port, token))
                        .isInstanceOf(IllegalStateException.class).hasMessage("CONNECT rejected");
            }
        }
    }

    @Test
    void senderInKafkaComesFromConnect() throws Exception {
        var user = UUID.randomUUID().toString();
        var body = "Authenticated STOMP " + UUID.randomUUID();
        try (var client = new StompTestClient(port, tokens.token(user)); var reader = new KafkaRecordReader()) {
            client.send("/app/chat", new ObjectMapper().writeValueAsString(Map.of("text", body)),
                    "user-id:" + UUID.randomUUID() + "\n");
            var record = reader.awaitValue(KafkaTopics.CHAT_MESSAGES, value -> value.contains(body), Duration.ofSeconds(15));
            assertThat(new ObjectMapper().readTree(record).get("senderId").asText()).isEqualTo(user);
        }
    }

    @Test
    void disconnectingOneOfTwoRealSessionsKeepsOtherOnline() throws Exception {
        String user = UUID.randomUUID().toString();
        String key = RedisConstants.PRESENCE_SESSIONS_KEY.formatted(user);
        try (var first = new StompTestClient(port, tokens.token(user))) {
            var second = new StompTestClient(port, tokens.token(user));
            try {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(redis.opsForZSet().size(key)).isEqualTo(2));
            } finally {
                second.close();
            }
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(redis.opsForZSet().count(key, System.currentTimeMillis() + 1d,
                            Double.POSITIVE_INFINITY)).isEqualTo(1));
            // Expire remaining session without waiting a minute; server heartbeat must revive it.
            for (String session : redis.opsForZSet().range(key, 0, -1)) {
                redis.opsForZSet().add(key, session, System.currentTimeMillis() - 1000);
            }
            first.send("/app/presence/heartbeat", "{}");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(redis.opsForZSet().count(key, System.currentTimeMillis() + 1d,
                            Double.POSITIVE_INFINITY)).isEqualTo(1));
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(redis.hasKey(key)).isFalse());
    }

    @Test
    void lateSubscriberSeesExistingSessionAndItsDisconnect() throws Exception {
        String peerId = UUID.randomUUID().toString();
        service.heartbeat(peerId, "already-connected");
        try (var watcher = new StompTestClient(port, tokens.token(UUID.randomUUID().toString()))) {
            watcher.subscribe("/topic/presence." + peerId);
            var mapper = new ObjectMapper();
            var initial = mapper.readTree(watcher.receive(Duration.ofSeconds(5)));
            assertThat(initial.path("userId").asText()).isEqualTo(peerId);
            assertThat(initial.path("status").asText()).isEqualTo("online");

            service.disconnected(peerId, "already-connected");
            assertThat(mapper.readTree(watcher.receive(Duration.ofSeconds(5))).path("status").asText())
                    .isEqualTo("offline");
        }
    }

    @Test
    void repeatedDisconnectDoesNotRemoveOtherSession() {
        String user = UUID.randomUUID().toString();
        service.heartbeat(user, "A");
        service.heartbeat(user, "B");
        service.disconnected(user, "A");
        service.disconnected(user, "A");
        assertThat(redis.opsForZSet().range(RedisConstants.PRESENCE_SESSIONS_KEY.formatted(user), 0, -1))
                .containsExactly("B");
    }
    @Test
    void invalidStompTextReturnsErrorToCurrentSession() throws Exception {
        try (var client = new StompTestClient(port, tokens.token(UUID.randomUUID().toString()))) {
            client.subscribe("/user/queue/errors");
            // Same inbound executor order ensures SUBSCRIBE precedes SEND.
            String payload = new ObjectMapper().writeValueAsString(
                    Map.of("text", "   ", "clientMessageId", UUID.randomUUID().toString()));
            client.send("/app/chat", payload);
            assertThat(client.receive(Duration.ofSeconds(10))).contains("10000");
        }
    }
}
