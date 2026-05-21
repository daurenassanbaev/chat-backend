package com.messenger.chatgateway.messaging.controller;

import com.messenger.chatgateway.messaging.dto.MessageDto;
import com.messenger.chatgateway.messaging.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat")
    public void handleMessage(
            MessageDto messageDto,
            @Header("user-id") String userId
    ) {
        chatService.processMessage(messageDto, userId);
    }
}
