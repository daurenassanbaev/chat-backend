package org.messenger.messagesvc.message.repository;

import org.messenger.messagesvc.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("""
    SELECT m FROM Message m
    WHERE m.fromUserId = :userId OR m.toUserId = :userId
    ORDER BY m.created_at DESC
    """)
    List<Message> findAllByUserId(@Param("userId") String userId);

    @Query("""
            SELECT m FROM Message m
            WHERE (m.fromUserId = :userId AND m.toUserId = :peerId)
               OR (m.fromUserId = :peerId AND m.toUserId = :userId)
            ORDER BY m.created_at DESC
            """)
    Page<Message> findMessagesBetween(
            @Param("userId") String userId,
            @Param("peerId") String peerId,
            Pageable pageable
    );
}
