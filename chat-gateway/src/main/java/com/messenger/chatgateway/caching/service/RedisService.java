package com.messenger.chatgateway.caching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

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

    public List<String> getList(String key) {
        return stringRedisTemplate.opsForList().range(key, 0, -1);
    }

    @SuppressWarnings("unchecked")
    public <T> T executeScript(
            String key,
            RedisScript<T> script
    ) {
        return (T) stringRedisTemplate.execute(
                script,
                List.of(key)
        );
    }

}
