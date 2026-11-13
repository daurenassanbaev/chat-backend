package org.messenger.messagesvc.message.service;

import lombok.RequiredArgsConstructor;
import org.chat.shared.kafka.event.MessageSentEvent;
import org.messenger.messagesvc.caching.constants.RedisKeys;
import org.messenger.messagesvc.caching.service.RedisService;
import org.messenger.messagesvc.message.dto.ChatPreviewDto;
import org.messenger.messagesvc.message.dto.DirectChatDto;
import org.messenger.messagesvc.message.dto.MessageDto;
import org.messenger.messagesvc.message.entity.Chat;
import org.messenger.messagesvc.message.entity.ChatMember;
import org.messenger.messagesvc.message.entity.ChatMemberId;
import org.messenger.messagesvc.message.entity.ChatRole;
import org.messenger.messagesvc.message.entity.ChatType;
import org.messenger.messagesvc.message.entity.Message;
import org.messenger.messagesvc.message.repository.ChatMemberRepository;
import org.messenger.messagesvc.message.repository.ChatRepository;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int CACHED_MESSAGES_LIMIT = 100;

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final RedisService redisService;
    private final UserService userService;

    @Transactional
    public DirectChatDto createOrGetDirectChat(UUID userId, UUID recipientId) {
        if (userId.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A direct chat requires another user");
        }

        UUID chatId = directChatId(userId, recipientId);
        if (!chatRepository.existsById(chatId)) {
            createChat(chatId, userId, List.of(recipientId), Instant.now());
        }

        return new DirectChatDto(chatId, recipientId);
    }

    @Transactional
    public void ensureChatForMessage(MessageSentEvent event) {
        if (chatRepository.existsById(event.chatId())) {
            assertMember(event.chatId(), event.senderId());
            return;
        }

        List<UUID> recipientIds = normalizedRecipients(event.senderId(), event.recipientIds());
        if (recipientIds.isEmpty()) {
            throw new IllegalArgumentException("recipientIds are required when creating a chat from a message");
        }

        createChat(event.chatId(), event.senderId(), recipientIds, event.createdAt());
    }

    @Transactional(readOnly = true)
    public List<ChatPreviewDto> getChats(UUID userId) {
        List<ChatMember> memberships = chatMemberRepository.findAllByIdUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<UUID> chatIds = memberships.stream()
                .map(member -> member.getId().getChatId())
                .toList();
        Map<UUID, UUID> peerIds = findPeerIds(userId, chatIds);
        Map<UUID, Message> latestMessages = latestMessages(chatIds);

        return chatIds.stream()
                .map(chatId -> toChatPreview(chatId, peerIds.get(chatId), latestMessages.get(chatId)))
                .sorted(Comparator.comparing(ChatPreviewDto::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MessageDto> getMessages(UUID userId, UUID chatId, int page, int size) {
        assertMember(chatId, userId);
        String key = RedisKeys.LAST_MSG_KEY.formatted(chatId);
        PageRequest pageable = PageRequest.of(page, size);

        if (pageable.getOffset() + size > CACHED_MESSAGES_LIMIT) {
            return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable)
                    .map(MessageDto::from);
        }

        if (Boolean.TRUE.equals(redisService.exists(key))) {
            Set<MessageDto> cached = redisService.getFromZSet(key, pageable.getOffset(),
                    pageable.getOffset() + size - 1);
            return new PageImpl<>(
                    cached == null ? List.of() : new ArrayList<>(cached),
                    pageable,
                    messageRepository.countByChatId(chatId)
            );
        }

        Page<MessageDto> fromDatabase = messageRepository
                .findByChatIdOrderByCreatedAtDesc(chatId, PageRequest.of(0, CACHED_MESSAGES_LIMIT))
                .map(MessageDto::from);

        fromDatabase.forEach(message -> redisService.add2ZSet(key, message, message.createdAt().toEpochMilli()));
        redisService.trimZSet(key, CACHED_MESSAGES_LIMIT);

        return new PageImpl<>(
                fromDatabase.getContent().stream().limit(size).toList(),
                pageable,
                fromDatabase.getTotalElements()
        );
    }

    private void createChat(UUID chatId, UUID creatorId, List<UUID> recipientIds, Instant createdAt) {
        userService.ensureMockUsersExist(withCreator(creatorId, recipientIds));

        Chat chat = new Chat();
        chat.setId(chatId);
        chat.setType(recipientIds.size() == 1 ? ChatType.DIRECT : ChatType.GROUP);
        chat.setCreatedBy(creatorId);
        chat.setCreatedAt(createdAt == null ? Instant.now() : createdAt);
        chatRepository.saveAndFlush(chat);

        List<ChatMember> members = new ArrayList<>();
        members.add(newMember(chatId, creatorId, ChatRole.OWNER, chat.getCreatedAt()));
        recipientIds.forEach(recipientId ->
                members.add(newMember(chatId, recipientId, ChatRole.MEMBER, chat.getCreatedAt())));
        chatMemberRepository.saveAllAndFlush(members);
    }

    private void assertMember(UUID chatId, UUID userId) {
        if (!chatMemberRepository.existsById(new ChatMemberId(chatId, userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a chat member");
        }
    }

    private Map<UUID, UUID> findPeerIds(UUID userId, List<UUID> chatIds) {
        return chatMemberRepository.findAllByIdChatIdIn(chatIds).stream()
                .filter(member -> !member.getId().getUserId().equals(userId))
                .collect(Collectors.toMap(
                        member -> member.getId().getChatId(),
                        member -> member.getId().getUserId(),
                        (first, ignored) -> first
                ));
    }

    private Map<UUID, Message> latestMessages(List<UUID> chatIds) {
        Map<UUID, Message> result = new LinkedHashMap<>();
        for (Message message : messageRepository.findAllByChatIdInOrderByCreatedAtDesc(chatIds)) {
            result.putIfAbsent(message.getChatId(), message);
        }
        return result;
    }

    private ChatPreviewDto toChatPreview(UUID chatId, UUID peerId, Message lastMessage) {
        return new ChatPreviewDto(
                chatId,
                peerId,
                lastMessage == null ? null : lastMessage.getId(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt()
        );
    }

    private List<UUID> normalizedRecipients(UUID senderId, List<UUID> recipients) {
        if (recipients == null) {
            return List.of();
        }

        return recipients.stream()
                .filter(recipientId -> recipientId != null && !recipientId.equals(senderId))
                .distinct()
                .toList();
    }

    private Collection<UUID> withCreator(UUID creatorId, List<UUID> recipientIds) {
        List<UUID> users = new ArrayList<>(recipientIds);
        users.add(creatorId);
        return users;
    }

    private ChatMember newMember(UUID chatId, UUID userId, ChatRole role, Instant joinedAt) {
        ChatMember member = new ChatMember();
        member.setId(new ChatMemberId(chatId, userId));
        member.setRole(role);
        member.setJoinedAt(joinedAt);
        return member;
    }

    private UUID directChatId(UUID firstUserId, UUID secondUserId) {
        String first = firstUserId.toString();
        String second = secondUserId.toString();
        String key = first.compareTo(second) <= 0 ? first + ':' + second : second + ':' + first;
        return UUID.nameUUIDFromBytes(("direct:" + key).getBytes(StandardCharsets.UTF_8));
    }
}
