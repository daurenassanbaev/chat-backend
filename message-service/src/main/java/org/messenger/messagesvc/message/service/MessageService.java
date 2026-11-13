package org.messenger.messagesvc.message.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.caching.constants.RedisKeys;
import org.messenger.messagesvc.caching.service.RedisService;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.entity.Message;
import org.messenger.messagesvc.message.entity.MessageStatus;
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
    private final RedisService redisService;

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

        try {
            String key = RedisKeys.LAST_MSG_KEY.formatted(message.getChatId());

            MessageDto dto = MessageMapper.toMessageDto(message);

            redisService.add2ZSet(key, dto, dto.createdAt().toEpochMilli());

            redisService.trimZSet(key, 100);

        } catch (Exception ex) {
            log.warn("Redis unavailable", ex);
        }
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
