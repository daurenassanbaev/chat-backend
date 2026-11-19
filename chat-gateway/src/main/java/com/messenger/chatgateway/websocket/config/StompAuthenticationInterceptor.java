package com.messenger.chatgateway.websocket.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {
    private final JwtDecoder decoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var frame = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (frame == null) return message;
        var command = frame.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticate(frame);
        } else if (command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
            String destination = frame.getDestination();
            if (frame.getUser() == null || destination == null) throw new AccessDeniedException("Sign in required");
            // Only application handlers may publish messages to the broker.
            boolean allowed = command == StompCommand.SEND ? destination.startsWith("/app/")
                    : destination.startsWith("/user/queue/") || destination.startsWith("/topic/presence.");
            if (!allowed) throw new AccessDeniedException("Invalid destination");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor frame) {
        var values = frame.removeNativeHeader(HttpHeaders.AUTHORIZATION);
        if (values == null || values.size() != 1 || !values.getFirst().startsWith("Bearer ")) {
            throw new BadCredentialsException("Bearer token required");
        }
        try {
            var jwt = decoder.decode(values.getFirst().substring(7));
            String subject = jwt.getSubject();
            if (subject == null || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())) {
                throw new BadCredentialsException("Invalid access token");
            }
            String userId = UUID.fromString(subject).toString();
            if (!userId.equalsIgnoreCase(subject)) throw new BadCredentialsException("Invalid token subject");
            frame.setUser(() -> userId);
        } catch (JwtException | IllegalArgumentException invalidToken) {
            throw new BadCredentialsException("Invalid access token");
        }
    }
}
