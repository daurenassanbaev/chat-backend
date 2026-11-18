package org.messenger.messagesvc.message.repository;

import org.messenger.messagesvc.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findAllByChatIdInOrderByCreatedAtDesc(List<UUID> chatIds);

    Page<Message> findByChatIdOrderByCreatedAtDescIdDesc(UUID chatId, Pageable pageable);

    long countByChatId(UUID chatId);

    Optional<Message> findByIdAndChatId(UUID id, UUID chatId);
    @Modifying
    @Query(value = """
            UPDATE messages SET status = 'DELIVERED'
            WHERE id = :id AND chat_id = :chatId AND status = 'SENT'
            """, nativeQuery = true)
    int markDelivered(@Param("id") UUID id,
                      @Param("chatId") UUID chatId);
    @Query(value = """
            SELECT latest.*
            FROM chats c
            CROSS JOIN LATERAL (
                SELECT m.* FROM messages m
                WHERE m.chat_id = c.id
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 1
            ) latest
            WHERE c.id IN (:chatIds)
            """, nativeQuery = true)
    List<Message> findLatestForChats(@Param("chatIds") List<UUID> chatIds);
}
