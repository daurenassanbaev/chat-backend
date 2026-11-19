package org.messenger.messagesvc.chat.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.chat.dto.UserSummary;
import org.messenger.messagesvc.chat.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UUID currentUser(Jwt jwt) {
        String subject = jwt.getSubject();
        UUID id;
        try {
            id = UUID.fromString(subject);
            if (!id.toString().equalsIgnoreCase(subject)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            userRepository.insertUserIfAbsent(id, id.toString());
        } else {
            userRepository.syncUsername(id, username);
        }
        return id;
    }

    @Transactional
    public List<UserSummary> search(Jwt jwt, String query) {
        UUID caller = currentUser(jwt);
        String prefix = query.strip();
        return prefix.isEmpty() ? List.of() : userRepository.search(caller, prefix);
    }

    public void ensureUsersExist(Collection<UUID> userIds) {
        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(id -> userRepository.insertUserIfAbsent(id, id.toString()));
    }
}
