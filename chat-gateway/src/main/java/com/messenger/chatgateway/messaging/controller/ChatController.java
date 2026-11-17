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
            @jakarta.validation.Valid MessageDto messageDto,
            @Header("user-id") String userId
    ) {
        chatService.processMessage(messageDto, userId);
    }
    @org.springframework.messaging.handler.annotation.MessageExceptionHandler(
            org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class)
    @org.springframework.messaging.simp.annotation.SendToUser(value = "/queue/errors", broadcast = false)
    public com.messenger.chatgateway.messaging.dto.SendError invalidMessage(
            org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException error) {
        var target = error.getBindingResult().getTarget();
        var id = target instanceof MessageDto dto ? dto.clientMessageId() : null;
        return new com.messenger.chatgateway.messaging.dto.SendError(id, "Некорректный текст: от 1 до 10000 символов");
    }
}
