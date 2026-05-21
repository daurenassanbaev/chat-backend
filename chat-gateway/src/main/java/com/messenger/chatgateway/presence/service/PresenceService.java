package com.messenger.chatgateway.presence.service;


import com.messenger.chatgateway.presence.dto.PresenceEvent;
import com.messenger.chatgateway.presence.dto.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    public static final String TOPIC_PRESENCE_DEST = "/topic/presence.";
    private final SimpMessagingTemplate messagingTemplate;

    public void sendUserStatusEvent(String userId, UserStatus status) {
        PresenceEvent event = PresenceEvent.builder()
                .userId(userId)
                .status(status.getValue())
                .build();

        messagingTemplate.convertAndSend(
                TOPIC_PRESENCE_DEST + userId,
                event
        );
    }
}
