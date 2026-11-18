package org.messenger.messagesvc.chat.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.chat.dto.ChatRecipientDto;
import org.messenger.messagesvc.chat.repository.ChatMemberRepository;
import org.messenger.messagesvc.chat.mapper.ChatMemberMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMemberService {

    private final ChatMemberRepository chatMemberRepository;

    public List<ChatRecipientDto> findRecipients(UUID chatId, UUID senderId) {
        return chatMemberRepository.findAllByIdChatIdAndIdUserIdNot(chatId, senderId)
                .stream()
                .map(ChatMemberMapper::toChatRecipientDto)
                .toList();
    }
}
