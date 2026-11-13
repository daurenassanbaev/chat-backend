package org.messenger.messagesvc.message.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.message.dto.ChatRecipientDto;
import org.messenger.messagesvc.message.repository.ChatMemberRepository;
import org.messenger.messagesvc.message.util.mapper.ChatMemberMapper;
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
