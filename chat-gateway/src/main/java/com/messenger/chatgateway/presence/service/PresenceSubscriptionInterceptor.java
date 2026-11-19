package com.messenger.chatgateway.presence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.UUID;

@Component
public class PresenceSubscriptionInterceptor implements ExecutorChannelInterceptor {
    private final PresenceService presence;
    private final MessageChannel outbound;
    private final ObjectMapper json;

    public PresenceSubscriptionInterceptor(@Lazy PresenceService presence,
            @Lazy @Qualifier("clientOutboundChannel") MessageChannel outbound, ObjectMapper json) {
        this.presence = presence;
        this.outbound = outbound;
        this.json = json;
    }

    // Read Redis after the broker registers the subscription, so a transition cannot
    // fall between the initial read and the start of live delivery.
    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception error) {
        var headers = message.getHeaders();
        if (error != null || !(handler instanceof SimpleBrokerMessageHandler)
                || SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.SUBSCRIBE) return;

        String destination = SimpMessageHeaderAccessor.getDestination(headers);
        if (destination == null || !destination.startsWith(PresenceService.TOPIC_PRESENCE_DEST)) return;
        String peer = destination.substring(PresenceService.TOPIC_PRESENCE_DEST.length());
        try {
            if (!UUID.fromString(peer).toString().equals(peer)) return;
        } catch (IllegalArgumentException invalidId) {
            return;
        }
        var reply = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        reply.setSessionId(SimpMessageHeaderAccessor.getSessionId(headers));
        reply.setSubscriptionId(SimpMessageHeaderAccessor.getSubscriptionId(headers));
        reply.setDestination(destination);
        reply.setContentType(MimeTypeUtils.APPLICATION_JSON);
        reply.setNativeHeader("presence-snapshot", "true");
        try {
            var payload = json.writeValueAsBytes(presence.currentStatus(peer));
            outbound.send(MessageBuilder.createMessage(payload, reply.getMessageHeaders()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize presence", e);
        }
    }
}
