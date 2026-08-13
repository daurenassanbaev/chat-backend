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

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishEvents() {
        log.info("Outbox poller started");
        outboxService.processAll();
        log.info("Outbox poller finished");
    }
}