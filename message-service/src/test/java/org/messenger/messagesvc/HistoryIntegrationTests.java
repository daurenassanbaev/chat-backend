package org.messenger.messagesvc;

import org.chat.shared.kafka.event.MessageSentEvent;
import org.junit.jupiter.api.Test;
import org.messenger.messagesvc.chat.repository.ChatMemberRepository;
import org.messenger.messagesvc.chat.service.ChatService;
import org.messenger.messagesvc.message.repository.MessageRepository;
import org.messenger.messagesvc.message.service.MessageService;
import org.messenger.testsupport.PostgresKafkaRedisIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.messenger.messagesvc.chat.repository.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "outbox.initial-delay-ms=3600000")
class HistoryIntegrationTests extends PostgresKafkaRedisIntegrationTest {
    @Autowired ChatService chats;
    @Autowired MessageService messages;
    @Autowired ChatMemberRepository members;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void chatsRequireTokenAndProvisionItsSubject() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(get("/chats").header("user-id", id)).andExpect(status().isUnauthorized());
        assertThat(users.existsById(id)).isFalse();
        mvc.perform(get("/chats").with(jwt().jwt(token -> token.subject(id.toString()))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(users.existsById(id)).isTrue();
    }

    @Test
    void anotherMembersHeaderCannotGrantHistoryAccess() throws Exception {
        var owner = UUID.randomUUID();
        var chat = chats.createOrGetDirectChat(owner, UUID.randomUUID());
        mvc.perform(get("/chats/{id}/messages", chat.chatId()).header("user-id", owner)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthAndBrowserPreflightArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(options("/chats").header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
    }

    @Test
    void concurrentDirectChatCreationHasOneChatAndTwoMembers() throws Exception {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); return chats.createOrGetDirectChat(a, b); });
            var second = workers.submit(() -> { start.await(); return chats.createOrGetDirectChat(b, a); });
            start.countDown();
            var chat = first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS).chatId()).isEqualTo(chat.chatId());
            assertThat(members.findAllByIdChatIdIn(List.of(chat.chatId()))).hasSize(2);
        }
    }

    @Test
    void historyAndPreviewsHaveStableOrderForEqualTimestamps() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var chat = chats.createOrGetDirectChat(a, b);
        var first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        // Use unique prefix so repeated local runs don't collide with other tests.
        first = new UUID(a.getMostSignificantBits(), 1);
        second = new UUID(a.getMostSignificantBits(), 2);
        Instant now = Instant.now();
        messages.save(new MessageSentEvent(first, chat.chatId(), a, List.of(b), "first", now));
        messages.save(new MessageSentEvent(second, chat.chatId(), a, List.of(b), "second", now));
        assertThat(chats.getMessages(a, chat.chatId(), 0, 1).getContent().getFirst().id()).isEqualTo(second);
        assertThat(chats.getMessages(a, chat.chatId(), 1, 1).getContent().getFirst().id()).isEqualTo(first);
        assertThat(chats.getChats(a).getFirst().lastMessageId()).isEqualTo(second);
    }

    @Autowired MessageRepository messageRepository;

    @Test
    void latestMessagesAreLimitedToRequestedChatsAndEmptyChatsRemainVisible() {
        var user = UUID.randomUUID();
        var peer = UUID.randomUUID();
        var first = chats.createOrGetDirectChat(user, peer);
        var second = chats.createOrGetDirectChat(user, UUID.randomUUID());
        var empty = chats.createOrGetDirectChat(user, UUID.randomUUID());
        var unrelated = chats.createOrGetDirectChat(peer, UUID.randomUUID());
        Instant now = Instant.parse("2026-11-17T10:00:00Z");
        UUID oldId = UUID.randomUUID();
        UUID latestId = UUID.randomUUID();
        messages.save(new MessageSentEvent(oldId, first.chatId(), user, List.of(), "old", now));
        messages.save(new MessageSentEvent(latestId, first.chatId(), user, List.of(), "latest", now.plusSeconds(1)));
        messages.save(new MessageSentEvent(UUID.randomUUID(), second.chatId(), user, List.of(), "second", now));
        messages.save(new MessageSentEvent(UUID.randomUUID(), unrelated.chatId(), peer, List.of(), "unrelated", now));

        // IN semantics are preserved for duplicate IDs, missing chats, and chats with no messages.
        assertThat(messageRepository.findLatestForChats(List.of(first.chatId(), first.chatId(),
                empty.chatId(), UUID.randomUUID()))).extracting(message -> message.getId())
                .containsExactly(latestId);
        var previews = chats.getChats(user);
        assertThat(previews).hasSize(3);
        assertThat(previews).filteredOn(preview -> preview.chatId().equals(first.chatId()))
                .extracting(preview -> preview.lastMessageId()).containsExactly(latestId);
        assertThat(previews).filteredOn(preview -> preview.chatId().equals(empty.chatId()))
                .allSatisfy(preview -> assertThat(preview.lastMessageId()).isNull());
    }

    @Test
    void invalidPaginationReturnsHttp400() throws Exception {
        var a = UUID.randomUUID();
        var chat = chats.createOrGetDirectChat(a, UUID.randomUUID());
        for (String params : List.of("page=-1&size=30", "page=0&size=0", "page=0&size=101")) {
            mvc.perform(get("/chats/" + chat.chatId() + "/messages?" + params)
                            .with(jwt().jwt(token -> token.subject(a.toString()))))
                    .andExpect(status().isBadRequest());
        }
    }
}
