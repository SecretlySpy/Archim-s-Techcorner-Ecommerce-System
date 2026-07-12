-- =============================================================================
-- Append-only security/business audit trail (mysql-design §6.12, §8.4).
-- Answers "who attempted or performed what privileged action, from which context".
-- Never updated in normal operation; access-controlled and integrity-monitored.
-- =============================================================================
CREATE TABLE audit_logs (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_uuid      BINARY(16)      NOT NULL,                -- globally unique event id
    actor_user_id   BIGINT UNSIGNED NULL,                    -- null for anonymous/system actions
    action          VARCHAR(60)     NOT NULL,                -- e.g. USER_REGISTERED, LOGIN_SUCCEEDED, ROLE_ASSIGNED
    subject_type    VARCHAR(40)     NOT NULL,                -- e.g. USER, ROLE, SESSION
    subject_id      VARCHAR(64)     NULL,                    -- public id / code of the subject
    correlation_id  VARCHAR(64)     NULL,
    detail          VARCHAR(1000)   NULL,                    -- redacted, non-sensitive summary (no secrets/PII)
    occurred_at     DATETIME(6)     NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT uk_audit_logs__event_uuid UNIQUE (event_uuid),
    CONSTRAINT fk_audit_logs__actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_logs__subject ON audit_logs (subject_type, subject_id, occurred_at, id);
CREATE INDEX idx_audit_logs__actor   ON audit_logs (actor_user_id, occurred_at);
CREATE INDEX idx_audit_logs__action  ON audit_logs (action, occurred_at);
