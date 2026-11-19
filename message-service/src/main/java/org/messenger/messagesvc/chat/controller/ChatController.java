package org.messenger.messagesvc.chat.controller;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.chat.dto.ChatPreviewDto;
import org.messenger.messagesvc.chat.dto.CreateDirectChatRequest;
import org.messenger.messagesvc.chat.dto.DirectChatDto;
import org.messenger.messagesvc.history.dto.MessageDto;
import org.messenger.messagesvc.chat.service.ChatService;
import org.messenger.messagesvc.chat.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final UserService userService;

    private UUID currentUser(Jwt jwt) {
        String subject = jwt.getSubject();
        UUID id;
        try {
            id = UUID.fromString(subject);
            if (!id.toString().equalsIgnoreCase(subject)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
        userService.ensureUserExists(id);
        return id;
    }

    @GetMapping
    public List<ChatPreviewDto> getChats(@AuthenticationPrincipal Jwt jwt) {
        return chatService.getChats(currentUser(jwt));
    }

    @PostMapping("/direct")
    public DirectChatDto createOrGetDirectChat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateDirectChatRequest request
    ) {
        return chatService.createOrGetDirectChat(currentUser(jwt), request.recipientId());
    }

    @GetMapping("/{chatId}/messages")
    public Page<MessageDto> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatService.getMessages(currentUser(jwt), chatId, page, size);
    }

}
