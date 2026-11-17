package com.messenger.chatgateway.messaging.controller;

import com.messenger.chatgateway.messaging.dto.MessageAck;
import com.messenger.chatgateway.messaging.service.MessageAckService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class MessageAckController {
    private final MessageAckService service;
    @MessageMapping("/messages/ack")
    public void acknowledge(MessageAck ack, Principal principal) {
        service.acknowledge(principal.getName(), ack.messageId());
    }
}
