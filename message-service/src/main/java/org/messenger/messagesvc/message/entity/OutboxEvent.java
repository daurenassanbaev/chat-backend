package org.messenger.messagesvc.message.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent {

    @Id
    private UUID id;

    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private Boolean sent;

    private LocalDateTime createdAt;
}