package com.messenger.chatgateway.messaging.controller;

import com.messenger.chatgateway.messaging.dto.MessageDto;
import com.messenger.chatgateway.messaging.dto.SendError;
import com.messenger.chatgateway.messaging.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat")
    public void handleMessage(
            @Valid MessageDto messageDto,
            Principal principal
    ) {
        chatService.processMessage(messageDto, principal.getName());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public SendError invalidMessage(MethodArgumentNotValidException error) {
        var target = error.getBindingResult().getTarget();
        var id = target instanceof MessageDto dto ? dto.clientMessageId() : null;
        return new SendError(id, "Некорректный текст: от 1 до 10000 символов");
    }
}
