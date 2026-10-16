package com.archimstechcorner.marketplace.features.notifications.application;

import com.archimstechcorner.marketplace.features.notifications.domain.model.CommunicationOutbox;
import com.archimstechcorner.marketplace.features.notifications.domain.repository.CommunicationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcherService.class);

    private final CommunicationOutboxRepository outboxRepository;

    public OutboxDispatcherService(CommunicationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public int dispatchPendingNotifications() {
        List<CommunicationOutbox> pending = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        if (pending.isEmpty()) {
            return 0;
        }

        int dispatchedCount = 0;
        for (CommunicationOutbox item : pending) {
            try {
                // Simulate / execute transactional email sending
                log.info("Dispatching communication outbox [{}] type='{}' to='{}' subject='{}'",
                        item.getId(), item.getEventType(), item.getRecipient(), item.getSubject());

                item.markSent();
                outboxRepository.save(item);
                dispatchedCount++;
            } catch (Exception e) {
                log.error("Failed to dispatch communication outbox item {}", item.getId(), e);
                item.markFailed();
                outboxRepository.save(item);
            }
        }

        return dispatchedCount;
    }
}
