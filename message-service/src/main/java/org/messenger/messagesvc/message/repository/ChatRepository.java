package org.messenger.messagesvc.message.repository;

import org.messenger.messagesvc.message.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
}
