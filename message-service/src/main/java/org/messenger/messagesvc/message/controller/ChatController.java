package org.messenger.messagesvc.message.controller;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.message.dto.ChatPreviewDto;
import org.messenger.messagesvc.message.dto.CreateDirectChatRequest;
import org.messenger.messagesvc.message.dto.DirectChatDto;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.service.ChatService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public List<ChatPreviewDto> getChats(@RequestHeader("user-id") UUID userId) {
        return chatService.getChats(userId);
    }

    @PostMapping("/direct")
    public DirectChatDto createOrGetDirectChat(
            @RequestHeader("user-id") UUID userId,
            @RequestBody CreateDirectChatRequest request
    ) {
        return chatService.createOrGetDirectChat(userId, request.recipientId());
    }

    @GetMapping("/{chatId}/messages")
    public Page<MessageDto> getMessages(
            @RequestHeader("user-id") UUID userId,
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatService.getMessages(userId, chatId, page, size);
    }

}
