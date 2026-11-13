package org.messenger.messagesvc.message.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.message.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Temporary local user provisioning. This is intentionally the only place
 * that creates users until the Keycloak/user service integration replaces it.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void ensureMockUsersExist(Collection<UUID> userIds) {
        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(userId -> userRepository.insertMockUserIfAbsent(userId, "mock-" + userId));
    }
}
