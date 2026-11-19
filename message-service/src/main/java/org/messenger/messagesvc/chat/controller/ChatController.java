package org.messenger.messagesvc.chat.controller;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.chat.dto.ChatPreviewDto;
import org.messenger.messagesvc.chat.dto.CreateDirectChatRequest;
import org.messenger.messagesvc.chat.dto.DirectChatDto;
import org.messenger.messagesvc.history.dto.MessageDto;
import org.messenger.messagesvc.chat.service.ChatService;
import org.messenger.messagesvc.chat.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final UserService userService;

    @GetMapping
    public List<ChatPreviewDto> getChats(@AuthenticationPrincipal Jwt jwt) {
        return chatService.getChats(userService.currentUser(jwt));
    }

    @PostMapping("/direct")
    public DirectChatDto createOrGetDirectChat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateDirectChatRequest request
    ) {
        return chatService.createOrGetDirectChat(userService.currentUser(jwt), request.recipientId());
    }

    @GetMapping("/{chatId}/messages")
    public Page<MessageDto> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatService.getMessages(userService.currentUser(jwt), chatId, page, size);
    }

}
