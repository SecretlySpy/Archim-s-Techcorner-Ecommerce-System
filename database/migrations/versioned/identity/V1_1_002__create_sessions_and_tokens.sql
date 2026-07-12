-- =============================================================================
-- Sessions, login history, and single-use email tokens.
-- Owning Java feature: authentication (see documentation/modules/authentication-rbac.md).
-- Only hashes of secrets are stored — never the raw refresh/verification token.
-- =============================================================================

-- Refresh-session family. One row per issued refresh token; rotation links successors.
CREATE TABLE user_sessions (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id               BINARY(16)      NOT NULL,
    user_id                 BIGINT UNSIGNED NOT NULL,
    token_hash              CHAR(64)        NOT NULL,          -- SHA-256 hex of the opaque refresh token
    rotation_family_id      BINARY(16)      NOT NULL,          -- stable across a rotation chain; reuse revokes the family
    replaced_by_session_id  BIGINT UNSIGNED NULL,              -- set when this token is rotated out
    device_label            VARCHAR(120)    NULL,
    ip_hash                 CHAR(64)        NULL,              -- hashed, retention-limited (no raw IP)
    user_agent_hash         CHAR(64)        NULL,
    issued_at               DATETIME(6)     NOT NULL,
    expires_at              DATETIME(6)     NOT NULL,
    last_used_at            DATETIME(6)     NULL,
    revoked_at              DATETIME(6)     NULL,
    revoke_reason           VARCHAR(40)     NULL,              -- LOGOUT, ROTATED, REUSE_DETECTED, PASSWORD_RESET, ADMIN
    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT uk_user_sessions__public_id UNIQUE (public_id),
    CONSTRAINT uk_user_sessions__token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_sessions__user     FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_sessions__replaced FOREIGN KEY (replaced_by_session_id) REFERENCES user_sessions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_sessions__user_state ON user_sessions (user_id, revoked_at, expires_at);
CREATE INDEX idx_user_sessions__expires    ON user_sessions (expires_at);
CREATE INDEX idx_user_sessions__family     ON user_sessions (rotation_family_id);

-- Immutable successful/failed login + device history.
CREATE TABLE user_login_events (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NULL,                   -- null when the email did not resolve to a user
    outcome           VARCHAR(20)     NOT NULL,               -- SUCCESS, FAILED, LOCKED, MFA_REQUIRED
    reason_code       VARCHAR(40)     NULL,
    email_attempted   VARCHAR(254)    NULL,                   -- normalized; used for anti-enumeration analysis
    ip_hash           CHAR(64)        NULL,
    user_agent_hash   CHAR(64)        NULL,
    device_label      VARCHAR(120)    NULL,
    occurred_at       DATETIME(6)     NOT NULL,
    CONSTRAINT pk_user_login_events PRIMARY KEY (id),
    CONSTRAINT fk_user_login_events__user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_login_events__outcome CHECK (outcome IN ('SUCCESS','FAILED','LOCKED','MFA_REQUIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_login_events__user_time ON user_login_events (user_id, occurred_at, id);
CREATE INDEX idx_user_login_events__outcome   ON user_login_events (outcome, occurred_at);

-- Single-use, expiring email verification / password reset tokens (hash only).
CREATE TABLE email_verification_tokens (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    token_hash    CHAR(64)        NOT NULL,                   -- SHA-256 hex of the opaque token
    purpose       VARCHAR(20)     NOT NULL,                   -- EMAIL_VERIFY, PASSWORD_RESET
    expires_at    DATETIME(6)     NOT NULL,
    consumed_at   DATETIME(6)     NULL,
    created_at    DATETIME(6)     NOT NULL,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_tokens__hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens__user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_email_verification_tokens__purpose CHECK (purpose IN ('EMAIL_VERIFY','PASSWORD_RESET'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_email_verification_tokens__user ON email_verification_tokens (user_id, purpose, expires_at);
