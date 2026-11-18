package org.messenger.messagesvc.chat.repository;

import org.messenger.messagesvc.chat.entity.ChatMember;
import org.messenger.messagesvc.chat.entity.ChatMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

    List<ChatMember> findAllByIdChatIdAndIdUserIdNot(UUID chatId, UUID senderId);

    List<ChatMember> findAllByIdUserId(UUID userId);

    List<ChatMember> findAllByIdChatIdIn(List<UUID> chatIds);
}
