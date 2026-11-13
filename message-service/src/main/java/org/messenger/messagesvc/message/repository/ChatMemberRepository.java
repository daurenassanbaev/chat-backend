package org.messenger.messagesvc.message.repository;

import org.messenger.messagesvc.message.entity.ChatMember;
import org.messenger.messagesvc.message.entity.ChatMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

    List<ChatMember> findAllByIdChatIdAndIdUserIdNot(UUID chatId, UUID senderId);

    List<ChatMember> findAllByIdUserId(UUID userId);

    List<ChatMember> findAllByIdChatIdIn(List<UUID> chatIds);
}
