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

    Page<Message> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);

    long countByChatId(UUID chatId);

    Optional<Message> findByIdAndChatId(UUID id, UUID chatId);
}
