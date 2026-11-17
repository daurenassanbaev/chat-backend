package com.messenger.chatgateway;

import com.messenger.chatgateway.presence.service.PresenceService;
import org.chat.shared.caching.RedisConstants;
import org.junit.jupiter.api.Test;
import org.messenger.testsupport.KafkaRedisIntegrationTest;
import org.messenger.testsupport.StompTestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PresenceIntegrationTests extends KafkaRedisIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired StringRedisTemplate redis;
    @Autowired PresenceService service;

    @Test
    void disconnectingOneOfTwoRealSessionsKeepsOtherOnline() throws Exception {
        String user = UUID.randomUUID().toString();
        String key = RedisConstants.PRESENCE_SESSIONS_KEY.formatted(user);
        try (var first = new StompTestClient(port, user)) {
            var second = new StompTestClient(port, user);
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
    void repeatedDisconnectDoesNotRemoveOtherSession() {
        String user = UUID.randomUUID().toString();
        service.heartbeat(user, "A");
        service.heartbeat(user, "B");
        service.disconnected(user, "A");
        service.disconnected(user, "A");
        assertThat(redis.opsForZSet().range(RedisConstants.PRESENCE_SESSIONS_KEY.formatted(user), 0, -1))
                .containsExactly("B");
    }
}
