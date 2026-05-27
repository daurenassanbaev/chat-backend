package com.messenger.chatgateway.websocket.listener;

import com.messenger.chatgateway.caching.service.RedisService;
import com.messenger.chatgateway.messaging.service.ChatService;
import com.messenger.chatgateway.messaging.service.OfflineMessageService;
import com.messenger.chatgateway.websocket.util.StompSessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Duration;
import java.util.List;

import static org.chat.shared.caching.RedisConstants.*;

@Component
@RequiredArgsConstructor
public class StompSubscribeListener {

    private static final String MESSAGES_DESTINATION = "/user/queue/messages";

    private final RedisService redisService;
    private final ChatService chatService;
    private final OfflineMessageService offlineMessageService;

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        String destination = StompSessionUtils.getDestination(event.getMessage());
        if (!MESSAGES_DESTINATION.equals(destination)) {
            return;
        }
        String userId = StompSessionUtils.getUserIdFromEvent(event);

        String offlineMsgKey = OFFLINE_KEY.formatted(userId);
        List<String> offlineMessages = offlineMessageService.getAndDelete(offlineMsgKey);

        if (offlineMessages != null && !offlineMessages.isEmpty()) {
            chatService.sendOfflineMessages(offlineMessages);
        }

        redisService.setTtl(ONLINE_KEY.formatted(userId), Duration.ofSeconds(60));
    }

}
