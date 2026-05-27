package com.messenger.chatgateway.messaging.service;

import com.messenger.chatgateway.caching.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfflineMessageService {

    private final RedisService redisService;

    private static final RedisScript<List> GET_AND_DELETE_SCRIPT =
            RedisScript.of("""
                local messages = redis.call(
                    'LRANGE',
                    KEYS[1],
                    0,
                    -1
                )

                redis.call(
                    'DEL',
                    KEYS[1]
                )

                return messages
            """, List.class);

    public List<String> getAndDelete(String key) {
        return redisService.executeScript(key, GET_AND_DELETE_SCRIPT);
    }

}
