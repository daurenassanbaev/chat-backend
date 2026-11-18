package org.messenger.messagesvc.status.service;

import lombok.RequiredArgsConstructor;
import org.chat.shared.kafka.event.MessageReceivedEvent;
import org.messenger.messagesvc.chat.entity.ChatMemberId;
import org.messenger.messagesvc.status.entity.MessageStatus;
import org.messenger.messagesvc.chat.repository.ChatMemberRepository;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageStatusService {

    private final MessageRepository messageRepository;
    private final ChatMemberRepository chatMemberRepository;

    @Transactional
    public void markDelivered(MessageReceivedEvent event) {
        if (!chatMemberRepository.existsById(new ChatMemberId(event.chatId(), event.recipientId()))) {
            throw new IllegalArgumentException("Delivery recipient is not a member of the chat");
        }
        messageRepository.markDelivered(event.messageId(), event.chatId());
    }
}
