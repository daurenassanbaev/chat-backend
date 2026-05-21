package org.messenger.messagesvc.message.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.message.dto.ChatPreviewDto;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.entity.Message;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;

    public List<ChatPreviewDto> getChats(String userId) {
        List<Message> all = messageRepository.findAllByUserId(userId);

        Map<String, Message> latestPerChat = new LinkedHashMap<>();
        for (Message m : all) {
            String peerId = m.getFromUserId().equals(userId) ? m.getToUserId() : m.getFromUserId();
            latestPerChat.putIfAbsent(peerId, m);
        }

        return latestPerChat.values().stream()
                .map(m -> toChatPreview(userId, m))
                .toList();
    }

    public Page<MessageDto> getMessages(String userId, String peerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findMessagesBetween(userId, peerId, pageable)
                .map(this::toMessageDto);
    }

    private ChatPreviewDto toChatPreview(String userId, Message m) {
        String peerId = m.getFromUserId().equals(userId) ? m.getToUserId() : m.getFromUserId();
        return new ChatPreviewDto(peerId, m.getId(), m.getContent(), m.getCreated_at());
    }

    private MessageDto toMessageDto(Message m) {
        return new MessageDto(
                m.getId(),
                m.getFromUserId(),
                m.getToUserId(),
                m.getContent(),
                m.getCreated_at(),
                m.getStatus()
        );
    }

}