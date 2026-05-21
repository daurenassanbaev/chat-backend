package org.messenger.messagesvc.message.controller;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.message.dto.ChatPreviewDto;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.service.ChatService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public List<ChatPreviewDto> getChats(
            @RequestHeader("user-id") String userId
    ) {
        return chatService.getChats(userId);
    }

    @GetMapping("/{peerId}/messages")
    public Page<MessageDto> getMessages(
            @RequestHeader("user-id") String userId,
            @PathVariable String peerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatService.getMessages(userId, peerId, page, size);
    }

}