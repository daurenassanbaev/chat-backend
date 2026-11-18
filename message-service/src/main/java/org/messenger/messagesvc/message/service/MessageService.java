package org.messenger.messagesvc.message.service;

import org.messenger.messagesvc.chat.service.ChatService;
import org.messenger.messagesvc.chat.service.ChatMemberService;
import org.messenger.messagesvc.outbox.service.OutboxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.history.dto.MessageDto;
import org.messenger.messagesvc.message.entity.Message;
import org.messenger.messagesvc.status.entity.MessageStatus;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.messenger.messagesvc.message.util.mapper.MessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatService chatService;
    private final ChatMemberService chatMemberService;
    private final OutboxService outboxService;

    @Transactional
    public void save(MessageSentEvent eventDto) {
        if (messageRepository.existsById(eventDto.messageId())) {
            log.info("Message {} already processed", eventDto.messageId());
            return;
        }

        chatService.ensureChatForMessage(eventDto);

        Message message = new Message();

        fillMessageEntity(eventDto, message);

        messageRepository.save(message);

        outboxService.saveMessageSavedEvents(
                eventDto,
                chatMemberService.findRecipients(message.getChatId(), message.getSenderId())
        );

    }

    private void fillMessageEntity(MessageSentEvent eventDto, Message message) {
        message.setId(eventDto.messageId());
        message.setContent(eventDto.content());
        message.setChatId(eventDto.chatId());
        message.setSenderId(eventDto.senderId());
        message.setCreatedAt(eventDto.createdAt());
        message.setStatus(MessageStatus.SENT);
    }
}
