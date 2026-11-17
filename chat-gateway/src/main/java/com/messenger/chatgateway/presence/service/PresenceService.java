package com.messenger.chatgateway.presence.service;


import com.messenger.chatgateway.presence.dto.PresenceEvent;
import com.messenger.chatgateway.presence.dto.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.chat.shared.caching.RedisConstants;
import java.util.List;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    public static final String TOPIC_PRESENCE_DEST = "/topic/presence.";
    private final SimpMessagingTemplate messagingTemplate;

    private final StringRedisTemplate redis;
    private static final long SESSION_TTL_MS = Duration.ofSeconds(60).toMillis();
    private static final RedisScript<Long> TOUCH = RedisScript.of("""
            local before = redis.call('ZCOUNT', KEYS[1], '(' .. ARGV[2], '+inf')
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            local count = redis.call('ZCARD', KEYS[1])
            redis.call('EXPIRE', KEYS[1], 120)
            if before == 0 and count > 0 then return 1 end
            return 0
            """, Long.class);
    private static final RedisScript<Long> DISCONNECT = RedisScript.of("""
            local before = redis.call('ZCOUNT', KEYS[1], '(' .. ARGV[2], '+inf')
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            local count = redis.call('ZCARD', KEYS[1])
            if before > 0 and count == 0 then return 1 end
            return 0
            """, Long.class);

    public void heartbeat(String userId, String sessionId) {
        long now = System.currentTimeMillis();
        Long changed = redis.execute(TOUCH, List.of(RedisConstants.PRESENCE_SESSIONS_KEY.formatted(userId)),
                sessionId, Long.toString(now), Long.toString(now + SESSION_TTL_MS));
        if (Long.valueOf(1).equals(changed)) sendUserStatusEvent(userId, UserStatus.ONLINE);
    }

    public void disconnected(String userId, String sessionId) {
        Long changed = redis.execute(DISCONNECT, List.of(RedisConstants.PRESENCE_SESSIONS_KEY.formatted(userId)),
                sessionId, Long.toString(System.currentTimeMillis()));
        if (Long.valueOf(1).equals(changed)) sendUserStatusEvent(userId, UserStatus.OFFLINE);
    }

    public void sendUserStatusEvent(String userId, UserStatus status) {
        PresenceEvent event = PresenceEvent.builder()
                .userId(userId)
                .status(status.getValue())
                .build();

        messagingTemplate.convertAndSend(
                TOPIC_PRESENCE_DEST + userId,
                event
        );
    }
}
