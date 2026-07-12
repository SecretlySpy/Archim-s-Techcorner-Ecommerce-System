-- =============================================================================
-- Identity core: principals, RBAC vocabulary, and customer profile.
-- Owning Java feature: users / customers (see documentation/modules/authentication-rbac.md §3).
-- Conventions (project-structure §7): InnoDB, utf8mb4, DATETIME(6) UTC,
--   BIGINT UNSIGNED surrogate id + non-sequential public_id, pk_/fk_/uk_/idx_ names.
-- =============================================================================

-- Login principals. No role booleans here — roles live in user_roles (mysql-design §6.1).
CREATE TABLE users (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           BINARY(16)      NOT NULL,                       -- non-sequential external reference (UUID bytes)
    email_normalized    VARCHAR(254)    NOT NULL,                       -- lowercased/trimmed; uniqueness key
    email_display       VARCHAR(254)    NOT NULL,                       -- original-case for display only
    phone_normalized    VARCHAR(20)     NULL,                           -- E.164; reserved (MVP is email-only)
    password_hash       VARCHAR(255)    NOT NULL,                       -- one-way adaptive hash; never cleartext
    password_algo       VARCHAR(20)     NOT NULL DEFAULT 'bcrypt',      -- allows transparent hash upgrades
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at   DATETIME(6)     NULL,
    phone_verified_at   DATETIME(6)     NULL,
    locked_until        DATETIME(6)     NULL,                           -- durable lockout backstop (not Redis)
    failed_login_count  INT             NOT NULL DEFAULT 0,
    security_version    INT             NOT NULL DEFAULT 0,             -- bumped to invalidate outstanding access tokens
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,             -- optimistic lock
    deleted_at          DATETIME(6)     NULL,                           -- soft delete (operationally restorable)
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users__public_id UNIQUE (public_id),
    CONSTRAINT uk_users__email UNIQUE (email_normalized),
    CONSTRAINT uk_users__phone UNIQUE (phone_normalized),
    CONSTRAINT ck_users__status CHECK (status IN
        ('PENDING_VERIFICATION','ACTIVE','LOCKED','SUSPENDED','DISABLED','DELETED')),
    CONSTRAINT ck_users__failed_login CHECK (failed_login_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_users__status_created ON users (status, created_at, id);
CREATE INDEX idx_users__locked_until   ON users (locked_until);

-- Platform roles (reference data; canonical rows seeded by R__seed_roles_permissions.sql).
CREATE TABLE roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)     NOT NULL,        -- CUSTOMER, SELLER, ADMIN, SUPPORT, FINANCE, CONTENT
    name        VARCHAR(80)     NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles__code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Atomic capabilities using the module.action convention (rbac-matrix §5).
CREATE TABLE permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(80)     NOT NULL,        -- e.g. users.role.assign
    module      VARCHAR(40)     NOT NULL,        -- e.g. users
    description VARCHAR(255)    NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions__code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_permissions__module ON permissions (module, code);

-- Many-to-many role -> permission map.
CREATE TABLE role_permissions (
    role_id         BIGINT UNSIGNED NOT NULL,
    permission_id   BIGINT UNSIGNED NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions__role       FOREIGN KEY (role_id)       REFERENCES roles (id),
    CONSTRAINT fk_role_permissions__permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_role_permissions__permission ON role_permissions (permission_id, role_id);

-- Time-bounded, attributed platform-role assignment.
-- active_marker enforces at-most-one ACTIVE (user,role): active rows = 1, revoked history rows = NULL
-- (MySQL permits multiple NULLs in a unique index) — the non-null-marker pattern from mysql-design §4.5.
CREATE TABLE user_roles (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    role_id             BIGINT UNSIGNED NOT NULL,
    active_marker       TINYINT UNSIGNED NULL,               -- 1 = active, NULL = revoked/historical
    granted_by_user_id  BIGINT UNSIGNED NULL,
    granted_at          DATETIME(6)     NOT NULL,
    expires_at          DATETIME(6)     NULL,
    revoked_by_user_id  BIGINT UNSIGNED NULL,
    revoked_at          DATETIME(6)     NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT uk_user_roles__active UNIQUE (user_id, role_id, active_marker),
    CONSTRAINT fk_user_roles__user     FOREIGN KEY (user_id)            REFERENCES users (id),
    CONSTRAINT fk_user_roles__role     FOREIGN KEY (role_id)            REFERENCES roles (id),
    CONSTRAINT fk_user_roles__grantor  FOREIGN KEY (granted_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles__revoker  FOREIGN KEY (revoked_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_user_roles__marker   CHECK (active_marker IS NULL OR active_marker = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_roles__role_active ON user_roles (role_id, active_marker, user_id);

-- Customer display/profile data (1:1 with a user that holds the CUSTOMER role).
CREATE TABLE customer_profiles (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id     BINARY(16)      NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    display_name  VARCHAR(60)     NOT NULL,
    locale        VARCHAR(10)     NOT NULL DEFAULT 'en',
    timezone      VARCHAR(40)     NOT NULL DEFAULT 'UTC',
    created_at    DATETIME(6)     NOT NULL,
    updated_at    DATETIME(6)     NOT NULL,
    version       BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_profiles PRIMARY KEY (id),
    CONSTRAINT uk_customer_profiles__public_id UNIQUE (public_id),
    CONSTRAINT uk_customer_profiles__user UNIQUE (user_id),
    CONSTRAINT fk_customer_profiles__user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
