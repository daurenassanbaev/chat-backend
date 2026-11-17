package org.messenger.messagesvc.message.service;

import lombok.RequiredArgsConstructor;
import org.chat.shared.kafka.event.MessageDeliveredEvent;
import org.messenger.messagesvc.message.entity.ChatMemberId;
import org.messenger.messagesvc.message.entity.MessageStatus;
import org.messenger.messagesvc.message.repository.ChatMemberRepository;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageStatusService {

    private final MessageRepository messageRepository;
    private final ChatMemberRepository chatMemberRepository;

    @Transactional
    public void markDelivered(MessageDeliveredEvent event) {
        var message = messageRepository.findByIdAndChatId(event.messageId(), event.chatId())
                .orElseThrow(() -> new IllegalStateException(
                        "Delivered event references an unknown message: " + event.messageId()));

        if (!chatMemberRepository.existsById(new ChatMemberId(event.chatId(), event.recipientId()))) {
            throw new IllegalArgumentException("Delivery recipient is not a member of the chat");
        }

        message.setStatus(MessageStatus.DELIVERED);
    }
}
