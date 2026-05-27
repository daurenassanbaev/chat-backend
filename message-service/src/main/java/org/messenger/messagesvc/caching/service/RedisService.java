package org.messenger.messagesvc.caching.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, MessageDto> redisTemplate;

    public Boolean exists(
            String key
    ) {
        return redisTemplate.hasKey(key);
    }

    public void add2ZSet(String key, MessageDto value, double score) {
        redisTemplate.opsForZSet()
                .add(key, value, score);
    }

    public Set<MessageDto> getFromZSet(
            String key,
            long start,
            long end
    ) {
        return redisTemplate.opsForZSet()
                .reverseRange(
                        key,
                        start,
                        end
                );
    }

    public void trimZSet(
            String key,
            long maxSize
    ) {

        redisTemplate.opsForZSet()
                .removeRange(
                        key,
                        0,
                        -(maxSize + 1)
                );
    }

    public Long getZSetSize(
            String key
    ) {

        return redisTemplate.opsForZSet()
                .size(
                        key
                );
    }


}
