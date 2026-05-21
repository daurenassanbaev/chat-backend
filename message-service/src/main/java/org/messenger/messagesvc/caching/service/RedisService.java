package org.messenger.messagesvc.caching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    public Boolean setIfAbsent(String key, String value, Duration ttl) {
        return stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, ttl);
    }
}
