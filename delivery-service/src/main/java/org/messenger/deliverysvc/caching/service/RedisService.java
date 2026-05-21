package org.messenger.deliverysvc.caching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    public void addWithTtl(String key, String value, Duration timeout) {
        stringRedisTemplate.opsForValue().set(key, value, timeout);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public void setTtl(String key, Duration timeout) {
        stringRedisTemplate.expire(key, timeout);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void lPush(String key, String value) {
        stringRedisTemplate.opsForList().rightPush(key, value);
    }

}
