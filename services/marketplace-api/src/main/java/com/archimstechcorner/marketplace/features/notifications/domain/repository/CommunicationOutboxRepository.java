package com.archimstechcorner.marketplace.features.notifications.domain.repository;

import com.archimstechcorner.marketplace.features.notifications.domain.model.CommunicationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunicationOutboxRepository extends JpaRepository<CommunicationOutbox, Long> {
    List<CommunicationOutbox> findByStatusOrderByCreatedAtAsc(String status);
    List<CommunicationOutbox> findByRecipientOrderByCreatedAtDesc(String recipient);
}
