package org.messenger.messagesvc.message.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_from_to", columnList = "from_user_id, to_user_id"),
                @Index(name = "idx_created_at", columnList = "created_at")
        })
@Getter
@Setter
public class Message {
    @Id
    private UUID id;

    @Column(name = "from_user_id")
    private String fromUserId;

    @Column(name = "to_user_id")
    private String toUserId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant created_at;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageStatus status;
}
