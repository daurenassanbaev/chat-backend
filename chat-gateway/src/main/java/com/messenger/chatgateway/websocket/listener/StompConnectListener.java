package com.messenger.chatgateway.websocket.listener;

import com.messenger.chatgateway.presence.service.PresenceService;
import com.messenger.chatgateway.websocket.util.StompSessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Component
@RequiredArgsConstructor
public class StompConnectListener {
    private final PresenceService presenceService;
    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        String userId = StompSessionUtils.getUserIdFromEvent(event);
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        presenceService.heartbeat(userId, sessionId);
    }
}
