package com.messenger.chatgateway.websocket.listener;

import com.messenger.chatgateway.presence.service.PresenceService;
import com.messenger.chatgateway.websocket.util.StompSessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Component
@RequiredArgsConstructor
public class StompDisconnectListener {
    private final PresenceService presenceService;
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() == null) return; // Handshake closed before an authenticated CONNECT.
        String userId = StompSessionUtils.getUserIdFromEvent(event);
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        presenceService.disconnected(userId, sessionId);
    }
}
