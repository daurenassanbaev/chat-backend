package org.messenger.messagesvc.chat.controller;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.chat.dto.UserSummary;
import org.messenger.messagesvc.chat.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/users/search")
    public List<UserSummary> search(@AuthenticationPrincipal Jwt jwt, @RequestParam String q) {
        return userService.search(jwt, q);
    }
}
