package org.messenger.messagesvc.chat.repository;

import org.messenger.messagesvc.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            INSERT INTO chats (id, type, created_by, created_at)
            VALUES (:id, :type, :creator, :createdAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@org.springframework.data.repository.query.Param("id") UUID id,
                       @org.springframework.data.repository.query.Param("type") String type,
                       @org.springframework.data.repository.query.Param("creator") UUID creator,
                       @org.springframework.data.repository.query.Param("createdAt") java.time.Instant createdAt);
}
