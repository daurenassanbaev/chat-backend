package com.messenger.chatgateway.websocket.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;

import java.security.Principal;
import java.util.Objects;

@Slf4j
@UtilityClass
public class StompSessionUtils {

    public static String getUserIdFromEvent(AbstractSubProtocolEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();

        if (Objects.isNull(user)) {
            log.warn("STOMP event without user. sessionId={}", accessor.getSessionId());
            throw new IllegalStateException("User is null in STOMP session");
        }

        String userId = user.getName();

        if (userId.isEmpty()) {
            log.warn("UserId is empty. user={}, sessionId={}", user, accessor.getSessionId());
            throw new IllegalStateException("UserId is null or empty");
        }

        log.debug("Resolved userId={} for sessionId={}", userId, accessor.getSessionId());

        return userId;
    }

    public static String getDestination(Message<byte[]> message) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        return accessor.getDestination();
    }



}
