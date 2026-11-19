package org.messenger.messagesvc.chat.repository;

import org.messenger.messagesvc.chat.entity.ChatMember;
import org.messenger.messagesvc.chat.entity.ChatMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

    List<ChatMember> findAllByIdChatIdAndIdUserIdNot(UUID chatId, UUID senderId);

    List<ChatMember> findAllByIdUserId(UUID userId);

    interface Peer {
        UUID getChatId();
        UUID getUserId();
        String getUsername();
    }

    @Query("""
            select m.id.chatId as chatId, u.id as userId,
                   case when u.usernameVerified then u.username else null end as username
            from ChatMember m join User u on u.id = m.id.userId
            where m.id.chatId in :chatIds and u.id <> :caller
            order by u.id
            """)
    List<Peer> findPeers(UUID caller, List<UUID> chatIds);

    List<ChatMember> findAllByIdChatIdIn(List<UUID> chatIds);
}
