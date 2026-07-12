package com.archimstechcorner.marketplace.features.auditlogs.domain.repository;

import com.archimstechcorner.marketplace.features.auditlogs.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the append-only audit trail. Kept private to the auditlogs feature. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
