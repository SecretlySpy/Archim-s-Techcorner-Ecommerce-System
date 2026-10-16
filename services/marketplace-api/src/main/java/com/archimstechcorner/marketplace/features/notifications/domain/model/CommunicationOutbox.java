package com.archimstechcorner.marketplace.features.notifications.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "communication_outbox")
public class CommunicationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected CommunicationOutbox() {}

    public static CommunicationOutbox create(String eventType, String recipient, String subject, String payload) {
        CommunicationOutbox outbox = new CommunicationOutbox();
        outbox.eventType = eventType;
        outbox.recipient = recipient;
        outbox.subject = subject;
        outbox.payload = payload;
        outbox.status = "PENDING";
        outbox.retryCount = 0;
        outbox.createdAt = Instant.now();
        return outbox;
    }

    public void markSent() {
        this.status = "SENT";
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        this.status = "FAILED";
        this.retryCount++;
        this.processedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
