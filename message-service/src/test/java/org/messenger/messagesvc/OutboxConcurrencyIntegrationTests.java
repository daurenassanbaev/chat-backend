package org.messenger.messagesvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chat.shared.kafka.event.MessageSavedEvent;
import org.chat.shared.kafka.constants.KafkaTopics;
import org.junit.jupiter.api.Test;
import org.messenger.messagesvc.outbox.entity.OutboxEvent;
import org.messenger.messagesvc.outbox.repository.OutboxRepository;
import org.messenger.messagesvc.outbox.service.OutboxService;
import org.messenger.messagesvc.message.kafka.KafkaProducerService;
import org.messenger.testsupport.PostgresKafkaRedisIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {"outbox.batch-size=2", "outbox.initial-delay-ms=3600000"})
class OutboxConcurrencyIntegrationTests extends PostgresKafkaRedisIntegrationTest {
    @Autowired OutboxRepository repository;
    @Autowired OutboxService service;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean KafkaProducerService producer;

    private OutboxEvent event(int index) throws Exception {
        var id = UUID.randomUUID();
        var event = new OutboxEvent();
        event.setId(id);
        event.setTopic(KafkaTopics.CHAT_PROCESSED);
        event.setSent(false);
        event.setCreatedAt(LocalDateTime.now().minusMinutes(1).plusSeconds(index));
        event.setPayload(mapper.writeValueAsString(new MessageSavedEvent(id, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "outbox " + index, Instant.now())));
        return repository.saveAndFlush(event);
    }

    @Test
    void twoTransactionsPublishEachRowOnce() throws Exception {
        repository.deleteAllInBatch();
        var events = List.of(event(0), event(1), event(2), event(3));
        var bothWorkersInsideSend = new CountDownLatch(2);
        var calls = new ConcurrentHashMap<UUID, Integer>();
        doAnswer(call -> {
            MessageSavedEvent value = call.getArgument(0);
            calls.merge(value.messageId(), 1, Integer::sum);
            bothWorkersInsideSend.countDown();
            if (!bothWorkersInsideSend.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Second worker could not skip locked rows");
            }
            return call.callRealMethod();
        }).when(producer).sendMessage(any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(service::processBatch);
            var second = executor.submit(service::processBatch);
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(calls).hasSize(4);
        assertThat(calls.values()).containsOnly(1);
        assertThat(repository.findAllById(events.stream().map(OutboxEvent::getId).toList()))
                .allMatch(e -> Boolean.TRUE.equals(e.getSent()));
    }

    @Test
    void failureKeepsSuccessfulPrefixAndLeavesFailedRowForRetry() throws Exception {
        repository.deleteAllInBatch();
        var first = event(0);
        var failed = event(1);
        doAnswer(call -> {
            MessageSavedEvent value = call.getArgument(0);
            if (value.messageId().equals(failed.getId())) {
                return CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable"));
            }
            return call.callRealMethod();
        }).when(producer).sendMessage(any());
        service.processBatch();
        assertThat(repository.findById(first.getId()).orElseThrow().getSent()).isTrue();
        assertThat(repository.findById(failed.getId()).orElseThrow().getSent()).isFalse();
    }

    @Test
    void cleanupIsBoundedAndPreservesUnsentAndRecentRows() throws Exception {
        repository.deleteAllInBatch();
        var old = event(0);
        old.setSent(true);
        old.setCreatedAt(LocalDateTime.now().minusDays(8));
        repository.saveAndFlush(old);
        var unsent = event(1);
        unsent.setCreatedAt(LocalDateTime.now().minusDays(8));
        repository.saveAndFlush(unsent);
        var recent = event(2);
        recent.setSent(true);
        repository.saveAndFlush(recent);
        assertThat(service.cleanupSentBatch()).isEqualTo(1);
        assertThat(repository.existsById(old.getId())).isFalse();
        assertThat(repository.existsById(unsent.getId())).isTrue();
        assertThat(repository.existsById(recent.getId())).isTrue();
    }
}
