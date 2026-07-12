package com.archimstechcorner.marketplace.features.auditlogs.application;

import com.archimstechcorner.marketplace.features.auditlogs.domain.model.AuditLog;
import com.archimstechcorner.marketplace.features.auditlogs.domain.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Records privileged/security actions. Callers invoke {@link #record} inside their own transaction so
 * the audit row commits atomically with the change it describes. {@code detail} must be redacted —
 * never a secret, token, password, or unnecessary PII.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(String action, String subjectType, String subjectId, Long actorUserId, String detail) {
        AuditLog entry = AuditLog.create();
        entry.setEventUuid(UUID.randomUUID());
        entry.setAction(action);
        entry.setSubjectType(subjectType);
        entry.setSubjectId(subjectId);
        entry.setActorUserId(actorUserId);
        entry.setDetail(detail);
        entry.setOccurredAt(clock.instant());
        repository.save(entry);
    }
}
