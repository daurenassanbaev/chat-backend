package org.messenger.messagesvc.message.repository;

import org.messenger.messagesvc.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findAllByChatIdInOrderByCreatedAtDesc(List<UUID> chatIds);

    Page<Message> findByChatIdOrderByCreatedAtDescIdDesc(UUID chatId, Pageable pageable);

    long countByChatId(UUID chatId);

    Optional<Message> findByIdAndChatId(UUID id, UUID chatId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE messages SET status = 'DELIVERED'
            WHERE id = :id AND chat_id = :chatId AND status = 'SENT'
            """, nativeQuery = true)
    int markDelivered(@org.springframework.data.repository.query.Param("id") UUID id,
                      @org.springframework.data.repository.query.Param("chatId") UUID chatId);
    @org.springframework.data.jpa.repository.Query(value = """
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
    List<Message> findLatestForChats(@org.springframework.data.repository.query.Param("chatIds") List<UUID> chatIds);
}
