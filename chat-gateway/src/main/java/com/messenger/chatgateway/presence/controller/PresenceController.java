package com.messenger.chatgateway.presence.controller;

import com.messenger.chatgateway.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class PresenceController {
    private final PresenceService presenceService;
    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal, @Header("simpSessionId") String sessionId) {
        presenceService.heartbeat(principal.getName(), sessionId);
    }
}
