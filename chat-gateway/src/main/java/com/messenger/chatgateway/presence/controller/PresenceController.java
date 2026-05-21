package com.messenger.chatgateway.presence.controller;

import com.messenger.chatgateway.caching.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
import java.time.Duration;
 
import static org.chat.shared.caching.RedisConstants.ONLINE_KEY;
 
@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceController {
 
    private final RedisService redisService;
 
    @PostMapping("/heartbeat")
    public void heartbeat(@RequestHeader("user-id") String userId) {
        redisService.setTtl(ONLINE_KEY.formatted(userId), Duration.ofSeconds(60));
    }
 
}
 