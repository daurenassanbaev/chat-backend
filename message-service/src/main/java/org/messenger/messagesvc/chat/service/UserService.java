package org.messenger.messagesvc.chat.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.chat.repository.UserRepository;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void ensureUserExists(UUID userId) {
        userRepository.insertUserIfAbsent(userId, userId.toString());
    }

    public void ensureUsersExist(Collection<UUID> userIds) {
        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(this::ensureUserExists);
    }
}
