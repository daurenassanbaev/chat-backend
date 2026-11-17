package org.messenger.messagesvc.message.poller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.messenger.messagesvc.message.service.OutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxService outboxService;

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:5000}",
            initialDelayString = "${outbox.initial-delay-ms:0}")
    public void publishEvents() {
        log.info("Outbox poller started");
        outboxService.processBatch();
        log.info("Outbox poller finished");
    }
    @Scheduled(fixedDelayString = "${outbox.cleanup-delay-ms:3600000}",
            initialDelayString = "${outbox.cleanup-initial-delay-ms:3600000}")
    public void cleanupSentEvents() {
        outboxService.cleanupSentBatch();
    }
}