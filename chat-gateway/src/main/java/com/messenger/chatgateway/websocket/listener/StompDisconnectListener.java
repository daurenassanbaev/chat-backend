package com.messenger.chatgateway.websocket.listener;

import com.messenger.chatgateway.caching.service.RedisService;
import com.messenger.chatgateway.presence.dto.UserStatus;
import com.messenger.chatgateway.presence.service.PresenceService;
import com.messenger.chatgateway.websocket.util.StompSessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.chat.shared.caching.RedisConstants.*;

@Component
@RequiredArgsConstructor
public class StompDisconnectListener {

    private final RedisService redisService;
    private final PresenceService presenceService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String userId = StompSessionUtils.getUserIdFromEvent(event);

        redisService.delete(ONLINE_KEY.formatted(userId));

        presenceService.sendUserStatusEvent(userId, UserStatus.OFFLINE);
    }
}
