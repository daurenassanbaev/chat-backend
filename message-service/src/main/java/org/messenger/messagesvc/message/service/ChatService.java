package org.messenger.messagesvc.message.service;

import lombok.RequiredArgsConstructor;
import org.messenger.messagesvc.caching.constants.RedisKeys;
import org.messenger.messagesvc.caching.service.RedisService;
import org.messenger.messagesvc.message.dto.ChatPreviewDto;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.entity.Message;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.messenger.messagesvc.message.util.mapper.ChatKeyGenerator;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final RedisService redisService;

    public List<ChatPreviewDto> getChats(String userId) {

        List<Message> all =
                messageRepository.findAllByUserId(
                        userId
                );

        Map<String, Message> latestPerChat =
                new LinkedHashMap<>();

        for (Message m : all) {

            String peerId =
                    m.getFromUserId()
                            .equals(userId)
                            ? m.getToUserId()
                            : m.getFromUserId();

            latestPerChat.putIfAbsent(
                    peerId,
                    m
            );
        }

        return latestPerChat.values()
                .stream()
                .map(
                        m -> toChatPreview(
                                userId,
                                m
                        )
                )
                .toList();
    }

    public Page<MessageDto> getMessages(
            String userId,
            String peerId,
            int page,
            int size
    ) {

        String key =
                ChatKeyGenerator.generate(
                        userId,
                        peerId
                );

        if(page * size >= 100){

            Pageable pageable =
                    PageRequest.of(
                            page,
                            size
                    );

            return messageRepository
                    .findMessagesBetween(
                            userId,
                            peerId,
                            pageable
                    )
                    .map(
                            this::toMessageDto
                    );
        }

        if(Boolean.TRUE.equals(
                redisService.exists(
                        key
                )
        )) {

            Set<MessageDto> cached =
                    redisService.getFromZSet(
                            key,
                            page * size,
                            page * size + size - 1
                    );

            return new PageImpl<>(
                    new ArrayList<>(cached)
            );
        }

        // cache miss

        Pageable cachePageable =
                PageRequest.of(
                        0,
                        100
                );

        Page<MessageDto> fromDb =
                messageRepository
                        .findMessagesBetween(
                                userId,
                                peerId,
                                cachePageable
                        )
                        .map(
                                this::toMessageDto
                        );

        for(MessageDto dto :
                fromDb.getContent()) {

            redisService.add2ZSet(
                    key,
                    dto,
                    dto.createdAt()
                            .toEpochMilli()
            );
        }

        redisService.trimZSet(
                key,
                100
        );

        List<MessageDto> result =
                fromDb.getContent()
                        .stream()
                        .limit(size)
                        .toList();

        return new PageImpl<>(
                result
        );
    }

    private ChatPreviewDto toChatPreview(
            String userId,
            Message m
    ) {

        String peerId =
                m.getFromUserId()
                        .equals(userId)
                        ? m.getToUserId()
                        : m.getFromUserId();

        return new ChatPreviewDto(
                peerId,
                m.getId(),
                m.getContent(),
                m.getCreated_at()
        );
    }

    private MessageDto toMessageDto(
            Message m
    ) {

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