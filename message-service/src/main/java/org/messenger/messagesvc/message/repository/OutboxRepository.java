package org.messenger.messagesvc.message.repository;

import org.messenger.messagesvc.message.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findBySentFalse();
}
