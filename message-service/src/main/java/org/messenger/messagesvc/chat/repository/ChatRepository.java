package org.messenger.messagesvc.chat.repository;

import org.messenger.messagesvc.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO chats (id, type, created_by, created_at)
            VALUES (:id, :type, :creator, :createdAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("type") String type,
                       @Param("creator") UUID creator,
                       @Param("createdAt") Instant createdAt);
}
