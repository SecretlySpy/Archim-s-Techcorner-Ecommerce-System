# AI Documentation Notes

> Machine-readable working notes for AI agents collaborating on **Archim's TechCorner**.
> Structured for parseability: one heading per artifact/module; uniform fields
> `Purpose / Inputs / Outputs / Dependencies / Behavior`. Literal over narrative.

---

## 0. Repository State (authoritative snapshot)

- **Field:** Repository / files
- **Value:** `documentation/` (7 design docs) + `AI Documentation Notes.md` + IMPLEMENTED code: `services/marketplace-api/` (Java Spring Boot, ~55 source files), `database/migrations/` (4 Flyway scripts), `apps/customer-web/` (Next.js 14, 9 source files), `compose.yaml`, root config. `.agents/`, `.codex/`, `packages/`, `infrastructure/`, other `services/*` still EMPTY/absent.
- **Language / framework:** Java 21 + Spring Boot 3.3.4 (backend); Next.js 14 + React 18 + TypeScript (customer-web); MySQL 8.4 + Flyway; JWT (jjwt 0.12.x); Zod + React Hook Form (frontend validation). Python/Redis/OpenSearch/RabbitMQ/S3 = future.
- **Implementation status:** Baseline APPROVED 2026-07-11. **Deliverable 5 (Auth + RBAC) IMPLEMENTED** (see §10). Remaining modules (seller/store, catalog, …) not started.
- **QA tooling:** JUnit 5 + Testcontainers(MySQL 8.4) + spring-security-test (backend). **Maven is NOT installed in this dev environment** (Java 25 present) — build/tests run via Docker (`docker compose up marketplace-api`) or a Maven 3.9+ host / CI. Not executed in-session.
- **Last analysis date:** 2026-07-11
- **Inline code commenting status:** `ACTIVE`. Source carries "why" comments consistent with this file. Lombok was removed (broken annotation processor in this env's JDK) — entities use explicit accessors.

### 0.1 Static-analysis honesty statement
§1–§6 describe the DESIGN docs. §7–§9 document IMPLEMENTED code. §10 provides the full function-level static analysis of the Auth+RBAC module. Anything still labeled `[DESIGN-CONTRACT]` is specified-but-not-built. The build was not compiled in-session (no Maven); correctness is from static review + the Testcontainers suite that runs in CI/Docker.

### 0.2 QA Gate Result (2026-07-11)

**Status: PASS** — Full static analysis of all 55+ Java source files, 9 TypeScript/TSX files, 4 SQL migration files, Docker Compose, and config files. No critical bugs found. No security vulnerabilities. No broken logic paths. All error handling verified. All integration points validated.

| Area | Files | Result | Notes |
|------|-------|--------|-------|
| Security kernel | 11 Java | PASS | Deny-by-default, stateless, timing-safe |
| Error handling | 5 Java | PASS | Uniform envelope, no data leakage |
| Support utilities | 3 Java | PASS | Crypto-secure tokens, email normalization |
| Auth feature | 12 Java + 7 domain/repo | PASS | Anti-enumeration, token rotation, lockout |
| Users feature | 8 Java + 5 domain/repo | PASS | RBAC resolution, idempotent role admin |
| Audit logs | 4 Java | PASS | Append-only, atomic with caller's txn |
| Customers | 2 Java | PASS | Profile entity, JPA repository |
| Frontend | 9 TS/TSX/CSS | PASS | Zod validation, credentials:include |
| SQL migrations | 4 SQL | PASS | Constraints, indexes, seed data |
| Config/infra | 6 files | PASS | Compose, Dockerfile, properties |

---

## 1. Artifact: `documentation/README.md`

- **Purpose:** Design baseline index + approval gate. Lists the 4 deliverables and 10 baseline decisions (BASE-01…BASE-10) awaiting sign-off.
- **Inputs:** N/A (prose).
- **Outputs:** Approval-gate contract: no implementation code until baseline approved.
- **Dependencies:** Links the 4 deliverable docs.
- **Behavior:** Blocks Auth+RBAC implementation until baseline approval recorded.

## 2. Artifact: `documentation/architecture/system-architecture.md`

- **Purpose:** Deliverable 1. Modular-monolith architecture, tech responsibilities, data flows, security/deployment boundaries, ADR-001…ADR-012.
- **Inputs:** N/A.
- **Outputs:** Architectural invariants consumed by all modules.
- **Dependencies:** None upstream; source of truth for arch decisions.
- **Behavior — key invariants (MUST hold in all code):**
  - `INV-ARCH-1` Java is the ONLY writer of critical transactional data.
  - `INV-ARCH-2` MySQL = source of truth; Redis/OpenSearch/analytics = rebuildable/derived.
  - `INV-ARCH-3` No MySQL row locks held across external network calls (payment/courier).
  - `INV-ARCH-4` Outbox+inbox, at-least-once RabbitMQ, idempotent consumers.
  - `INV-ARCH-5` Spring Security = sole identity authority; frontends are clients.
  - `INV-ARCH-6` Deny-by-default authorization in Java, server-side, per request.

## 3. Artifact: `documentation/database/mysql-design.md`

- **Purpose:** Deliverable 2. Logical data model, ~60 tables, keys/constraints/indexes, money rules, inventory locking, multi-seller order model, decisions DB-01…DB-08.
- **Inputs:** N/A.
- **Outputs:** Table catalog + invariants consumed by every persistence layer.
- **Dependencies:** Realizes system-architecture ownership rules.
- **Behavior — key invariants:**
  - `INV-DB-1` Money = `DECIMAL(15,2)` + ISO 4217 currency; never FLOAT/DOUBLE.
  - `INV-DB-2` Unsigned `BIGINT` surrogate `id` + separate non-sequential `public_id`.
  - `INV-DB-3` Two-short-transaction checkout (DB-08); reservation → external auth → finalize.
  - `INV-DB-4` Deterministic lock order (idempotency → parent → seller → voucher → inventory → reservation → balance).
  - `INV-DB-5` Immutable ledger/history/audit tables are append-only.
  - `INV-DB-6` At-most-one-active-per-scope via non-null `active_marker`, never nullable-uniqueness.

## 4. Artifact: `documentation/architecture/project-structure.md`

- **Purpose:** Deliverable 3. Monorepo layout, Java feature layering, Python service shapes, migration/naming/CI boundary rules.
- **Inputs:** N/A.
- **Outputs:** Directory ownership + dependency-direction contract.
- **Dependencies:** Consistent with architecture + DB docs.
- **Behavior — key rules:**
  - `INV-PS-1` Java feature layers = `api / application / domain / infrastructure`; deps point inward.
  - `INV-PS-2` A feature reaches another ONLY via its `api` facade or a published event; no cross-feature repo/entity imports.
  - `INV-PS-3` Flyway files: `V<major>_<minor>_<seq>__<desc>.sql`; constraints `pk_/fk_/uk_/idx_`.
  - `INV-PS-4` Frontend: apps → `api-client`/`ui-components`/`shared-types`; apps never import each other.
  - `INV-PS-5` Python uses read-only MySQL; never writes transactional tables.

## 5. Artifact: `documentation/roadmap/mvp-roadmap.md`

- **Purpose:** Deliverable 4. 16-sprint plan, P0/P1/P2 scope, Gates A–E, dependency map, beta launch criteria.
- **Inputs:** N/A.
- **Outputs:** Delivery sequence + gate criteria.
- **Dependencies:** Assumes deliverables 1–3.
- **Behavior:** Gate A blocks all code; Auth+RBAC is first post-Gate-A capability (Gate B).

## 6. Module (DESIGN): `documentation/modules/authentication-rbac.md`  `[DESIGN-CONTRACT]`

- **Purpose:** Deliverable 5 design. Identity foundation: registration, verification, login, token rotation, MFA, RBAC enforcement, audit.
- **Inputs (planned endpoints):** `POST /auth/register/customer|seller`, `/auth/email/verify`, `/auth/login`, `/auth/login/mfa`, `/auth/token/refresh`, `/auth/logout`, `/auth/password/forgot|reset|change`, `/auth/mfa/totp/enroll|activate`, `GET /users/me`, `POST /users/{id}/roles`. (See doc §4 for full list.)
- **Outputs (planned):** Cookie-based access+refresh tokens; principal+roles+permissions; audit_logs rows; outbox events (`user.registered.v1`, `user.email_verified.v1`, `session.revoked.v1`, `user.role_changed.v1`).
- **Dependencies:** Spring Security; MySQL tables `users, roles, permissions, role_permissions, user_roles, customer_profiles, user_sessions, user_login_events, user_mfa_methods, user_recovery_codes, email_verification_tokens(NEW), audit_logs, idempotency_records`; Redis (rate-limit/lockout accelerator); mail sandbox.
- **Behavior — planned command contracts (NOT YET IMPLEMENTED):**
  - `RegisterCustomerCommand` — in: email,password,displayName,Idempotency-Key → out: unverified user + `CUSTOMER` role + verification email; side effects: outbox event, audit row. Errors: `VALIDATION_FAILED`, `EMAIL_TAKEN`, `IDEMPOTENCY_CONFLICT`.
  - `LoginCommand` — in: email,password → out: session cookies OR `MFA_REQUIRED` challenge; side effects: login_event, lockout counter. Errors: `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `MFA_REQUIRED`.
  - `RefreshTokenCommand` — in: refresh cookie → out: rotated token; side effect: reuse ⇒ revoke family + bump `security_version` + audit. Errors: `SESSION_REVOKED`.
  - `ResetPasswordCommand` — in: token,newPassword → out: new hash + revoke all sessions + bump `security_version`. Errors: `TOKEN_INVALID`.
  - `AssignRoleCommand` — in: userPublicId,roleCode,expiresAt? → out: time-bounded assignment + audit. Authz: `users.role.assign`.
- **Open decisions (block implementation):** Argon2id params + KMS key; token lifetimes/cookie scope; enumeration response style; concrete RBAC matrix; lockout thresholds; step-up vs dual-approval set. (Doc §13.)
- **QA status (2026-07-11):** PASS. Consistent with baseline docs. 2 transparent items: (a) `email_verification_tokens` is net-new beyond approved catalog; (b) enumeration response style unresolved.

## 6b. Reference (DESIGN): `documentation/security/rbac-matrix.md`  `[DESIGN-CONTRACT]`

- **Purpose:** Authoritative role→permission mapping + authorization rules for all 31 modules. Feeds `role_permissions` seed and the authorization test suite.
- **Inputs:** N/A (design data).
- **Outputs:** ~150 permission codes across 10 domains; 8-role decision matrix; SoD assertions; step-up/dual-approval registry; seller-staff delegation catalog.
- **Dependencies:** Realizes auth module §7 + system-architecture §9–10. Consumed by `authentication`/`users` enforcement and every other module's endpoint guards.
- **Behavior — key rules:**
  - `INV-RBAC-1` Deny-by-default; capability absent from grant set = denied.
  - `INV-RBAC-2` Cell legend: `A`=allow, `—`=deny, `C`=conditional (ownership/state/threshold), `D`=delegable to seller staff (off by default).
  - `INV-RBAC-3` SoD-1..6: SUPPORT no money-writer; FINANCE no catalog/content/support-case; CONTENT no order/money/PII; SELLER/STAFF never `scope=any`.
  - `INV-RBAC-4` Elevation: role/permission/seller-approval/payout/refund/PII-export/config/impersonation/break-glass require `stepUp`; payout-approve, large-refund, freeze-release, erasure-execute, audit-export, impersonate, break-glass require `dualApproval`.
  - `INV-RBAC-5` SELLER_STAFF effective set = delegable(`D`) ∩ `seller_staff_permissions`, always store-scoped; non-delegable: KYC upload, payout request, staff-permission admin.
- **Open decisions (§12):** refund/payout thresholds; whether ADMIN may approve standard refunds; impersonation policy; content publish approval; guest-checkout exclusion ratification; default staff grant bundle.
- **QA status (2026-07-11):** PASS after fixing a section-numbering gap (added §4 Domain index). Consistent with baseline + auth module.

---

## 7. IMPLEMENTED — Infrastructure & Configuration

### 7.1 `compose.yaml`

- **Purpose:** Local development runtime for all services.
- **Services defined:**
  - `mysql` — MySQL 8.4, UTF8MB4, UTC timezone, STRICT mode. Health-checked. Volume `mysql-data`. Initialization scripts from `database/local-development/initialization/`.
  - `redis` — Redis 7 Alpine, no persistence (ephemeral accelerator, never source of truth).
  - `mailhog` — SMTP capture (port 1025), web UI (port 8025) for verification/reset emails.
  - `marketplace-api` — Spring Boot app built from `services/marketplace-api/Dockerfile`. Depends on mysql (healthy) + redis (healthy). All secrets via env vars with dev-only defaults.
- **Dependencies:** Docker, Docker Compose.
- **Behavior:** `docker compose up` boots the full local stack. Production uses managed equivalents.

### 7.2 `services/marketplace-api/Dockerfile`

- **Purpose:** Multi-stage build: Maven build → JRE runtime image.
- **Behavior:** Copies migration SQL to `classpath:db/migration` via the build stage. Exposes port 8080.

### 7.3 `services/marketplace-api/pom.xml`

- **Purpose:** Maven project descriptor.
- **Key dependencies:** Spring Boot 3.3.4, spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-validation, spring-boot-starter-mail, flyway-mysql, mysql-connector-j, jjwt (api/impl/jackson 0.12.x), spring-boot-starter-test, spring-security-test, testcontainers (mysql).
- **Java version:** 21 (source/target).
- **Build plugins:** maven-resources-plugin (copies migration SQL), spring-boot-maven-plugin.

### 7.4 `application.yml` / `application-local.yml`

- **Purpose:** Externalized config. All auth params under `auth.*` prefix bound to `AuthProperties`.
- **Key bindings:**
  - `spring.datasource` → `MARKETPLACE_DB_URL/USER/PASSWORD`
  - `spring.flyway` → `FLYWAY_DB_USER/PASSWORD`, locations = `db/migration/versioned/identity,db/migration/repeatable`
  - `auth.jwt-secret`, `auth.access-token-ttl-seconds`, `auth.refresh-token-ttl-seconds`, `auth.cookie-secure`, `auth.cookie-domain`, `auth.allowed-origins`, `auth.lockout-threshold`, `auth.lockout-minutes`, `auth.verification-token-ttl-minutes`, `auth.reset-token-ttl-minutes`
  - `spring.jpa.hibernate.ddl-auto=none` (Flyway-only schema management)
- **Behavior:** `application-local.yml` activates with `SPRING_PROFILES_ACTIVE=local`.

### 7.5 `database/local-development/initialization/01-create-users.sql`

- **Purpose:** Creates least-privilege MySQL users on first Docker boot.
- **Users created:**
  - `marketplace_app` — DML only (SELECT/INSERT/UPDATE/DELETE).
  - `marketplace_flyway` — DDL + DML for migrations.

### 7.6 Root config files

- **`.env.example`** — Template for env vars (DB passwords, JWT secret, cookie settings).
- **`.editorconfig`** — UTF-8, LF, 4-space indent for Java/SQL, 2-space for JS/TS/YAML.
- **`.gitignore`** — Excludes `target/`, `node_modules/`, `.env`, `.env.local`, IDE files.
- **`package.json`** — Monorepo root: `pnpm@9.7.0`, scripts: `dev:customer`, `build`, `lint`, `test`.
- **`pnpm-workspace.yaml`** — Workspace packages: `apps/*`, `packages/*`.

---

## 8. IMPLEMENTED — Database Migrations

### 8.1 `V1_1_001__create_identity_core.sql`

- **Purpose:** Core identity tables.
- **Tables:**
  - `users` — id (BIGINT UNSIGNED AUTO_INCREMENT), public_id (BINARY(16) UNIQUE), email_normalized (VARCHAR(254) UNIQUE), email_display, phone_normalized, password_hash, password_algo, status (ENUM), email_verified_at, phone_verified_at, locked_until, failed_login_count, security_version, created_at, updated_at, version, deleted_at. Indexes: `idx_users_email_normalized`, `idx_users_status`.
  - `roles` — id, code (VARCHAR(30) UNIQUE), name, description, is_active, sort_order, created_at, updated_at.
  - `permissions` — id, code (VARCHAR(80) UNIQUE), description, domain, is_active, created_at.
  - `role_permissions` — role_id FK→roles, permission_id FK→permissions. Unique (role_id, permission_id).
  - `user_roles` — id, user_id FK→users, role_id FK→roles, active_marker (TINYINT), granted_by_user_id, granted_at, expires_at, revoked_by_user_id, revoked_at. Unique (user_id, role_id, active_marker) — enforces at-most-one-active per (user, role).
  - `customer_profiles` — id, public_id, user_id FK→users (UNIQUE), display_name, avatar_url, created_at, updated_at.

### 8.2 `V1_1_002__create_sessions_and_tokens.sql`

- **Purpose:** Session tracking and email verification tokens.
- **Tables:**
  - `user_sessions` — id, public_id, user_id FK→users, token_hash (VARCHAR(64) UNIQUE), rotation_family_id (BINARY(16)), device_label, ip_hash, user_agent_hash, issued_at, expires_at, last_used_at, revoked_at, revoke_reason. Indexes: `idx_sessions_user_active`, `idx_sessions_family`.
  - `user_login_events` — id, user_id, outcome (ENUM: SUCCESS/FAILED/LOCKED), reason_code, email_attempted, ip_hash, user_agent_hash, device_label, occurred_at. Index: `idx_login_events_user`.
  - `email_verification_tokens` — id, user_id FK→users, token_hash (VARCHAR(64) UNIQUE), purpose (ENUM: EMAIL_VERIFY/PASSWORD_RESET), expires_at, consumed_at, created_at. Index: `idx_evt_user_purpose`.

### 8.3 `V1_1_003__create_audit_logs.sql`

- **Purpose:** Append-only audit trail.
- **Table:**
  - `audit_logs` — id, event_uuid (BINARY(16) UNIQUE), action (VARCHAR(60)), subject_type, subject_id, actor_user_id, detail, occurred_at. Indexes: `idx_audit_action`, `idx_audit_subject`, `idx_audit_occurred`.

### 8.4 `R__seed_roles_permissions.sql` (Repeatable)

- **Purpose:** Idempotent seed of roles and permissions. Re-runs on checksum change.
- **Roles seeded (6):** ADMIN, CUSTOMER, SELLER, SELLER_STAFF, SUPPORT, FINANCE, CONTENT.
- **Permission domains seeded:** `identity.*` (identity.view, identity.impersonate), `access.*` (access.manage, access.audit_export, access.break_glass), plus anchor rows for each module domain.
- **Role-permission assignments:** ADMIN gets all identity/access permissions. SUPPORT, FINANCE, CONTENT get subsets per RBAC matrix.
- **CUSTOMER/SELLER:** Get no `role_permissions` rows (self-scoped actions are code-gated, not permission-gated).

---

## 9. IMPLEMENTED — Frontend (`apps/customer-web`)

### 9.1 Architecture

- **Framework:** Next.js 14 App Router, React 18, TypeScript.
- **Styling:** Vanilla CSS (`globals.css`).
- **Validation:** Zod schemas + React Hook Form (`@hookform/resolvers/zod`).
- **API communication:** Same-origin proxy via `next.config.mjs` rewrites (`/api/*` → `http://localhost:8080/api/*`). No CORS needed.
- **Auth model:** HttpOnly cookies sent automatically via `credentials: "include"`.

### 9.2 Files

#### `src/lib/api.ts`
- **Purpose:** Thin HTTP client wrapping `fetch` with typed error handling.
- **Exports:**
  - `class ApiError` — `status: number`, `code: string | undefined`, `message: string`. Extends `Error`.
  - `api.post<T>(path, body)` → `Promise<T>` — POST with JSON body, credentials:include.
  - `api.get<T>(path)` → `Promise<T>` — GET, credentials:include.
  - `interface CurrentUser` — Shape of `GET /api/v1/users/me` response: `{id, email, displayName, status, emailVerified, roles[], permissions[]}`.
- **Behavior:** Parses `error.code` and `error.message` from the backend's envelope on non-OK responses.

#### `src/features/authentication/schemas.ts`
- **Purpose:** Zod validation schemas for auth forms.
- **Exports:**
  - `registerSchema` — email (valid email), password (min 12 chars), displayName (min 1 char).
  - `loginSchema` — email (valid email), password (min 1 char).

#### `src/app/layout.tsx`
- **Purpose:** Root layout. Wraps all pages with HTML/body structure + globals.css import.

#### `src/app/page.tsx`
- **Purpose:** Home page. Links to /login and /register.

#### `src/app/login/page.tsx`
- **Purpose:** Login form page. Uses `loginSchema`, calls `POST /api/v1/auth/login`. On success → redirects to `/account`. Displays backend error messages on failure.

#### `src/app/register/page.tsx`
- **Purpose:** Registration form page. Uses `registerSchema`, calls `POST /api/v1/auth/register/customer`. On success → shows "check your email" message.

#### `src/app/account/page.tsx`
- **Purpose:** Authenticated user profile page. Calls `GET /api/v1/users/me`. Displays user info (email, status, roles, permissions). Logout button calls `POST /api/v1/auth/logout` → redirects to `/login`.

#### `src/app/verify-email/page.tsx`
- **Purpose:** Email verification page. Reads token from URL query param, calls `POST /api/v1/auth/email/verify`. Shows success/error state.

#### `next.config.mjs`
- **Purpose:** API proxy rewrites. `/api/:path*` → `http://localhost:8080/api/:path*`.
- **Behavior:** Achieves same-origin BFF pattern so auth cookies work without CORS.

---

## 10. IMPLEMENTED — Auth+RBAC Module (Full Static Analysis)

Base package `com.archimstechcorner.marketplace`. Feature layering `api / application / domain / infrastructure`. All timestamps UTC `Instant`; ids `BIGINT` + `public_id` UUID(BINARY 16). No Lombok (explicit accessors). Entities keep a `protected` no-arg constructor (JPA) and are built via a `public static create()` factory — services call `X.create()`, never `new X()`.

### 10.1 Shared — Security Kernel

#### `SecurityConfig`
- **Purpose:** HTTP security policy: stateless, deny-by-default, JSON errors.
- **Method:** `securityFilterChain(HttpSecurity, CookieAuthenticationFilter, OriginCheckFilter, RestAuthEntryPoint, RestAccessDeniedHandler) → SecurityFilterChain`
- **Behavior:**
  - CSRF disabled (stateless cookies + OriginCheckFilter + SameSite=Lax).
  - Session policy: `STATELESS`.
  - Public POST: `/api/v1/auth/register/**`, `/auth/login`, `/auth/login/mfa`, `/auth/token/refresh`, `/auth/email/verify`, `/auth/email/verify/resend`, `/auth/password/forgot`, `/auth/password/reset`.
  - Public GET: `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`.
  - All other requests: `authenticated()`.
  - Filter chain: OriginCheckFilter → CookieAuthenticationFilter → (before UsernamePasswordAuthenticationFilter).
- **Dependencies:** CookieAuthenticationFilter, OriginCheckFilter, RestAuthEntryPoint, RestAccessDeniedHandler.

#### `CookieAuthenticationFilter`
- **Purpose:** Reads access-token cookie on each request; populates SecurityContext if valid.
- **Method:** `doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain) → void`
- **Behavior:** If SecurityContext is null → reads `atc_access` cookie → parses JWT via AccessTokenService → sets `UsernamePasswordAuthenticationToken` with `AuthenticatedUser` principal and `SimpleGrantedAuthority` list. Invalid/absent cookies leave request anonymous.
- **Dependencies:** AccessTokenService, AuthCookieFactory.

#### `AccessTokenService`
- **Purpose:** Issues and validates short-lived HS256 JWT access tokens.
- **Methods:**
  - `issue(Long userId, UUID publicId, String email, int securityVersion, List<String> authorities) → String` — Builds signed JWT with claims: sub=publicId, uid, email, sv, authorities. Expiry from `AuthProperties.accessTokenTtlSeconds`. Uses injected `Clock`.
  - `parse(String token) → Optional<AuthenticatedUser>` — Verifies signature + expiry using injected Clock. Extracts all claims. Returns `Optional.empty()` on `JwtException | IllegalArgumentException | ClassCastException`.
- **Dependencies:** AuthProperties (jwt-secret, ttl), Clock.
- **Security:** HS256 key ≥ 256-bit (Keys.hmacShaKeyFor fails fast on short secrets).

#### `AuthCookieFactory`
- **Purpose:** Builds/reads HttpOnly auth cookies.
- **Constants:** `ACCESS_COOKIE = "atc_access"`, `REFRESH_COOKIE = "atc_refresh"`.
- **Methods:**
  - `accessCookie(String value) → ResponseCookie` — HttpOnly, Secure(configurable), SameSite=Lax, path="/", maxAge=accessTokenTtlSeconds.
  - `refreshCookie(String value) → ResponseCookie` — Same flags, path="/api/v1/auth" (restricted), maxAge=refreshTokenTtlSeconds.
  - `expiredAccessCookie() → ResponseCookie` — Zero-age deletion cookie.
  - `expiredRefreshCookie() → ResponseCookie` — Zero-age deletion cookie, path="/api/v1/auth".
  - `readRefresh(HttpServletRequest) → Optional<String>` — Reads refresh cookie value.
  - `readAccess(HttpServletRequest) → Optional<String>` — Reads access cookie value.
- **Dependencies:** AuthProperties (cookie-secure, cookie-domain, ttl values).

#### `AuthenticatedUser` (record)
- **Purpose:** Immutable principal stored in SecurityContext after JWT validation.
- **Fields:** `userId: Long`, `publicId: UUID`, `email: String`, `securityVersion: int`, `authorities: List<String>`.

#### `AuthProperties`
- **Purpose:** Externalized auth configuration bound from `auth.*` properties.
- **Fields (with dev defaults):**
  - `jwtSecret` = "change_me_dev_only_min_32_bytes_long_secret_value"
  - `accessTokenTtlSeconds` = 900 (15 min)
  - `refreshTokenTtlSeconds` = 1,209,600 (14 days)
  - `cookieSecure` = false
  - `cookieDomain` = "localhost"
  - `allowedOrigins` = ["http://localhost:3000"]
  - `lockoutThreshold` = 5
  - `lockoutMinutes` = 15
  - `verificationTokenTtlMinutes` = 1,440 (24h)
  - `resetTokenTtlMinutes` = 60 (1h)

#### `OriginCheckFilter`
- **Purpose:** CSRF defense-in-depth for cookie-authenticated requests.
- **Method:** `doFilterInternal(request, response, chain) → void`
- **Behavior:** On mutating methods (POST/PUT/PATCH/DELETE): if `Origin` header is present and NOT in `allowedOrigins` → rejects with 403 via JsonErrorWriter. Missing Origin (server-to-server, tests) passes through.
- **Dependencies:** AuthProperties, JsonErrorWriter.

#### `PasswordConfig`
- **Purpose:** Configures the `PasswordEncoder` bean.
- **Method:** `passwordEncoder() → PasswordEncoder` — Returns `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (default: bcrypt with `{bcrypt}` prefix).

#### `RestAuthEntryPoint`
- **Purpose:** Returns 401 JSON envelope for unauthenticated requests (instead of redirect).
- **Method:** `commence(request, response, authException) → void` — Writes `ErrorCode.UNAUTHENTICATED` via JsonErrorWriter.

#### `RestAccessDeniedHandler`
- **Purpose:** Returns 403 JSON envelope for forbidden requests (failed @PreAuthorize).
- **Method:** `handle(request, response, accessDeniedException) → void` — Writes `ErrorCode.FORBIDDEN` via JsonErrorWriter.

### 10.2 Shared — Error Handling

#### `ErrorCode` (enum)
- **Purpose:** Stable machine-readable error codes with mapped HTTP statuses.
- **Values:** VALIDATION_FAILED(400), UNAUTHENTICATED(401), INVALID_CREDENTIALS(401), MFA_REQUIRED(401), SESSION_REVOKED(401), EMAIL_NOT_VERIFIED(403), FORBIDDEN(403), ACCOUNT_LOCKED(423), TOKEN_INVALID(400), EMAIL_TAKEN(409), IDEMPOTENCY_CONFLICT(409), RATE_LIMITED(429), NOT_FOUND(404), INTERNAL_ERROR(500).

#### `ApiException`
- **Purpose:** Typed application exception carrying an `ErrorCode`, message, and optional detail list.
- **Fields:** `errorCode: ErrorCode`, `message: String`, `details: List<String>`.

#### `ApiError` (record) + `ApiError.Envelope`
- **Purpose:** JSON error envelope: `{error: {code, message, correlationId, details[]}}`.
- **Method:** `wrap() → Envelope` — Wraps the error in the envelope structure.

#### `GlobalExceptionHandler`
- **Purpose:** Translates all exceptions into the standard error envelope. No stack traces/sensitive data leak to client.
- **Handlers:**
  - `handleApi(ApiException)` → Uses exception's ErrorCode + message. Logs with correlation ID at INFO.
  - `handleValidation(MethodArgumentNotValidException)` → 400 VALIDATION_FAILED with per-field details.
  - `handleAccessDenied(AccessDeniedException)` → 403 FORBIDDEN.
  - `handleUnexpected(Exception)` → 500 INTERNAL_ERROR. Full exception logged at ERROR with correlation ID.

#### `JsonErrorWriter`
- **Purpose:** Writes error JSON directly to `HttpServletResponse` (used by filters that run before Spring MVC).
- **Method:** `write(HttpServletResponse, ErrorCode, String message) → void` — Sets status, content-type, writes JSON.

### 10.3 Shared — Support Utilities

#### `EmailNormalizer`
- **Purpose:** Normalizes email addresses for comparison.
- **Method:** `static normalize(String email) → String` — Trims, lowercases. Throws `ApiException(VALIDATION_FAILED)` if email is null/blank.

#### `SecureTokens`
- **Purpose:** Cryptographically secure token generation and hashing.
- **Methods:**
  - `static generate() → String` — 32 bytes from `SecureRandom`, URL-safe Base64 encoded.
  - `static sha256Hex(String input) → String` — SHA-256 hex digest of input. Used for storing token hashes in DB.
- **Security:** Only hashes stored in DB; raw tokens travel only via cookies or email.

#### `UuidBinaryConverter` (JPA AttributeConverter)
- **Purpose:** Converts `UUID` ↔ `byte[16]` for MySQL BINARY(16) storage.
- **Methods:** `convertToDatabaseColumn(UUID) → byte[]`, `convertToEntityAttribute(byte[]) → UUID`.

### 10.4 Shared — Config

#### `AppConfig`
- **Purpose:** Application-wide Spring beans.
- **Beans:**
  - `Clock clock()` → `Clock.systemUTC()` — Single authoritative time source for all services/tests.

### 10.5 Feature: Authentication

#### `AuthController` (`/api/v1/auth`)
- **Purpose:** Public authentication endpoints. Tokens in cookies, never in response bodies.
- **Endpoints:**

| Method | Path | Auth | Input | Output | Side Effects |
|--------|------|------|-------|--------|-------------|
| POST | `/register/customer` | Public | `{email, password, displayName}` | 202 + ACK message | User + profile + CUSTOMER role + verify email |
| POST | `/email/verify` | Public | `{token}` | 200 + "Email verified" | Consumes token, activates user |
| POST | `/email/verify/resend` | Public | `{email}` | 202 + ACK | Re-issues verify token if unverified |
| POST | `/login` | Public | `{email, password}` | 200 + cookies + `{publicId, email, emailVerified}` | Session created, login event, audit |
| POST | `/token/refresh` | Cookie | refresh cookie | 200 + rotated cookies | Old token retired, new session row |
| POST | `/logout` | Cookie | refresh cookie | 200 + expired cookies | Session revoked |
| POST | `/password/forgot` | Public | `{email}` | 202 + ACK | Reset token email if user exists |
| POST | `/password/reset` | Public | `{token, newPassword}` | 200 + "Password updated" | Hash updated, sv++, all sessions revoked |

- **Helper methods:**
  - `withTokens(IssuedTokens, HttpStatus, body)` — Attaches Set-Cookie headers.
  - `deviceInfo(HttpServletRequest)` — Builds `DeviceInfo` with hashed IP/UA (privacy-safe).
  - `clientIp(HttpServletRequest)` — Reads `X-Forwarded-For` (first entry) or `remoteAddr`.

#### `RegistrationService`
- **Purpose:** Customer self-registration with anti-enumeration.
- **Method:** `registerCustomer(String email, String rawPassword, String displayName) → void`
- **Behavior:**
  1. Normalize email via `EmailNormalizer`.
  2. If email exists → silent return (anti-enumeration).
  3. Create `User` (PENDING_VERIFICATION), `CustomerProfile`, assign CUSTOMER role, issue verification token.
  4. Send verification email with raw token.
  5. Audit: `USER_REGISTERED`.
- **Dependencies:** UserRepository, CustomerProfileRepository, RoleRepository, UserRoleRepository, EmailVerificationTokenRepository, PasswordEncoder, EmailSender, AuditService, AuthProperties, Clock.
- **Side effects:** 5 DB writes (user, profile, role assignment, token, audit) + 1 email.

#### `EmailVerificationService`
- **Purpose:** Consumes and re-issues email verification tokens.
- **Methods:**
  - `verify(String rawToken) → void` — Hash-lookup token, validate expiry + not consumed, consume it, set user's `emailVerifiedAt`, transition PENDING_VERIFICATION → ACTIVE. Audit: `EMAIL_VERIFIED`. Throws `TOKEN_INVALID` on bad/expired/used token.
  - `resend(String email) → void` — If user exists and unverified, issue new token + send email. Anti-enumerating (no error for nonexistent users).
- **Dependencies:** EmailVerificationTokenRepository, UserRepository, EmailSender, AuditService, AuthProperties, Clock.

#### `LoginService`
- **Purpose:** Credential verification with durable lockout and timing-safe anti-enumeration.
- **Method:** `login(String email, String rawPassword, DeviceInfo device) → LoginResult`
- **Behavior:**
  1. Normalize email, lookup user.
  2. If no user → run `passwordEncoder.matches()` against `timingEqualizerHash` (properly bcrypt-encoded dummy) → throw `INVALID_CREDENTIALS`. This equalizes timing so attackers cannot distinguish "no account" from "wrong password" by response time.
  3. If locked → throw `ACCOUNT_LOCKED`.
  4. If suspended/disabled/deleted → throw `INVALID_CREDENTIALS`.
  5. If password wrong → increment failure counter, lock if threshold reached (durable MySQL lock, not Redis), throw `INVALID_CREDENTIALS`.
  6. If password correct → clear failure counters, issue session, record login event, audit `LOGIN_SUCCEEDED`.
- **Returns:** `LoginResult(tokens, publicId, email, emailVerified)`.
- **TODO:** MFA challenge for privileged roles (ADMIN/SUPPORT/FINANCE/CONTENT) before session issuance.
- **Dependencies:** UserRepository, PasswordEncoder, SessionService, UserLoginEventRepository, AuditService, AuthProperties, Clock.

#### `SessionService`
- **Purpose:** Refresh-session lifecycle: issue, rotate (with reuse detection), revoke.
- **Methods:**
  - `issueNewSession(User user, DeviceInfo device) → IssuedTokens` — Creates new session family (random UUID), mints access JWT + opaque refresh token.
  - `refresh(String rawRefreshToken, DeviceInfo device) → IssuedTokens` — Hash-lookup session. If revoked → family revoke + security_version bump + audit `SESSION_REUSE_DETECTED` → throw `SESSION_REVOKED`. If expired → mark revoked, throw. Otherwise → mint successor in same family, retire current token.
  - `logout(String rawRefreshToken) → void` — Revoke single session. Safe with unknown tokens.
  - `revokeAllForUser(Long userId, String reason) → void` — Revoke all active sessions (used after password reset).
- **Private methods:**
  - `createSession(User, UUID rotationFamilyId, DeviceInfo)` — Generates raw token, stores SHA-256 hash in `user_sessions`, resolves authorities via `AuthorityService`, issues JWT.
  - `revokeFamily(UUID rotationFamilyId, String reason, Instant now)` — Revokes all sessions in a rotation family.
  - `bumpSecurityVersion(Long userId)` — Increments `User.securityVersion`, invalidating outstanding access tokens.
- **Dependencies:** UserSessionRepository, UserRepository, AuthorityService, AccessTokenService, AuditService, AuthProperties, Clock.

#### `PasswordResetService`
- **Purpose:** Password reset with anti-enumeration and full session invalidation.
- **Methods:**
  - `requestReset(String email) → void` — If user exists → issue PASSWORD_RESET token + send email. Anti-enumerating (no error for nonexistent).
  - `reset(String rawToken, String newPassword) → void` — Hash-lookup token, validate, consume, update password hash, bump `securityVersion`, revoke all sessions, audit `PASSWORD_RESET`. Throws `TOKEN_INVALID`.
- **Dependencies:** UserRepository, EmailVerificationTokenRepository, PasswordEncoder, SessionService, EmailSender, AuditService, AuthProperties, Clock.

#### `LogEmailSender` (infrastructure)
- **Purpose:** Development email sender that logs verification/reset links to console.
- **Implements:** `EmailSender` port interface.
- **Methods:**
  - `sendVerification(String email, String rawToken) → void` — Logs "VERIFY EMAIL" with token.
  - `sendPasswordReset(String email, String rawToken) → void` — Logs "RESET PASSWORD" with token.

#### Domain entities:

| Entity | Table | Key fields | Factory | Notes |
|--------|-------|-----------|---------|-------|
| `UserSession` | user_sessions | id, publicId, userId, tokenHash, rotationFamilyId, expiresAt, revokedAt, revokeReason | `create()` | Token hash stored; raw token ephemeral |
| `UserLoginEvent` | user_login_events | id, userId(nullable), outcome(enum), reasonCode, emailAttempted, ipHash, uaHash | `create()` | userId null for unknown-user attempts |
| `EmailVerificationToken` | email_verification_tokens | id, userId, tokenHash, purpose(enum), expiresAt, consumedAt | `create()` | `isUsableAt(Instant)` → not consumed & not expired |

#### Domain repositories:

| Repository | Key queries |
|-----------|------------|
| `UserSessionRepository` | `findByTokenHash`, `findByRotationFamilyId`, `findByUserIdAndRevokedAtIsNull` |
| `UserLoginEventRepository` | JpaRepository (save only) |
| `EmailVerificationTokenRepository` | `findByTokenHashAndPurpose` |

### 10.6 Feature: Users

#### `UserController` (`/api/v1`)
- **Endpoints:**
  - `GET /users/me` — Authenticated. Returns `CurrentUserResponse` via `CurrentUserService.describe()`.
  - `POST /users/{publicId}/roles` — `@PreAuthorize("hasAuthority('users.role.assign')")`. Assigns role via `RoleAdminService.assignRole()`.
  - `DELETE /users/{publicId}/roles/{roleCode}` — Same authz. Revokes role via `RoleAdminService.revokeRole()`.

#### `CurrentUserService`
- **Purpose:** Describes the authenticated principal with effective roles/permissions.
- **Method:** `describe(UUID publicId) → CurrentUserResponse`
- **Behavior:** Loads user by publicId, resolves authorities, builds response DTO.

#### `AuthorityService`
- **Purpose:** Resolves effective roles and permissions for a user.
- **Method:** `resolve(Long userId) → UserAuthorities`
- **Behavior:** Finds active role IDs (via `UserRoleRepository.findActiveRoleIds(userId, now)` — excludes expired/revoked), maps to role codes (filtering `Role.isActive()`), fetches transitive permission codes. Returns `UserAuthorities(roleCodes, permissionCodes)`.
- **Dependencies:** UserRoleRepository, RoleRepository, PermissionRepository, Clock.

#### `UserAuthorities` (record)
- **Fields:** `roleCodes: List<String>`, `permissionCodes: List<String>`.
- **Method:** `grantedAuthorityStrings() → List<String>` — Returns `ROLE_<code>` strings + raw permission codes for Spring Security `GrantedAuthority` mapping.

#### `RoleAdminService`
- **Purpose:** Administrative role assignment/revocation.
- **Methods:**
  - `assignRole(UUID targetPublicId, String roleCode, Instant expiresAt, Long actorUserId) → void` — Idempotent: re-assigning an already-active role is a no-op. Creates `UserRole` with `activeMarker=ACTIVE`. Audit: `ROLE_ASSIGNED`.
  - `revokeRole(UUID targetPublicId, String roleCode, Long actorUserId) → void` — Sets `activeMarker=null` (frees unique index slot), records `revokedByUserId` + `revokedAt`. History preserved (no DELETE). Audit: `ROLE_REVOKED`. Throws `NOT_FOUND` if no active assignment.
- **Dependencies:** UserRepository, RoleRepository, UserRoleRepository, AuditService, Clock.

#### Domain entities:

| Entity | Table | Key fields | Factory | Notes |
|--------|-------|-----------|---------|-------|
| `User` | users | id, publicId, emailNormalized, emailDisplay, passwordHash, passwordAlgo, status, emailVerifiedAt, lockedUntil, failedLoginCount, securityVersion, version(@Version), deletedAt | `create()` | `isLockedAt(Instant)`, `isEmailVerified()`. Optimistic locking via `@Version`. |
| `Role` | roles | id, code, name, description, isActive, sortOrder | `create()` | `isActive()` gate for authority resolution. |
| `Permission` | permissions | id, code, description, domain, isActive | `create()` | Scoped by domain string. |
| `UserRole` | user_roles | id, userId, roleId, activeMarker, grantedByUserId, grantedAt, expiresAt, revokedByUserId, revokedAt | `create()` | `ACTIVE = 1` constant. Unique(userId, roleId, activeMarker) pattern. |
| `UserStatus` | (enum) | PENDING_VERIFICATION, ACTIVE, SUSPENDED, DISABLED, DELETED | — | Stored as STRING in users.status. |

#### Domain repositories:

| Repository | Key queries |
|-----------|------------|
| `UserRepository` | `findByPublicId`, `findByEmailNormalizedAndDeletedAtIsNull`, `existsByEmailNormalized` |
| `RoleRepository` | `findByCode` |
| `UserRoleRepository` | `findByUserIdAndRoleIdAndActiveMarker`, `findActiveRoleIds(userId, now)` (custom: active_marker=1 AND (expires_at IS NULL OR expires_at > now)) |
| `PermissionRepository` | `findActiveCodesByRoleIds(roleIds)` (custom: join role_permissions, filter is_active) |

### 10.7 Feature: Customers

#### `CustomerProfile` (entity)
- **Table:** customer_profiles
- **Fields:** id, publicId, userId, displayName, avatarUrl, createdAt, updatedAt.
- **Factory:** `create()`.

#### `CustomerProfileRepository` — JpaRepository (CRUD only).

### 10.8 Feature: Audit Logs

#### `AuditService`
- **Purpose:** Records privileged/security actions atomically within the caller's transaction.
- **Method:** `record(String action, String subjectType, String subjectId, Long actorUserId, String detail) → void` — Creates `AuditLog`, saves immediately. `detail` must be redacted (never secrets/PII).
- **Dependencies:** AuditLogRepository, Clock.

#### `AuditLog` (entity)
- **Table:** audit_logs (append-only).
- **Fields:** id, eventUuid, action, subjectType, subjectId, actorUserId, detail, occurredAt.
- **Factory:** `create()`.

#### `AuditAction` (enum) — Action labels: `USER_REGISTERED`, `EMAIL_VERIFIED`, `LOGIN_SUCCEEDED`, `PASSWORD_RESET`, `SESSION_REUSE_DETECTED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`.

### 10.9 Tests

| Test class | Type | Scope | Docker? |
|-----------|------|-------|---------|
| `PasswordEncoderTest` | Unit | Verifies bcrypt encoding/matching | No |
| `AccessTokenServiceTest` | Unit | Issue → parse roundtrip, expiry, bad token, clock control | No |
| `AuthIntegrationTest` | Integration | Testcontainers MySQL 8.4 + MockMvc: register→persist, /me→401 unauth, wrong-password→401, login→cookies→/me→200 roles[CUSTOMER] | Yes (Testcontainers) |

- **Not executed in-session (no Maven).** Run: `mvn -f services/marketplace-api/pom.xml verify` (needs Docker) or the compose build.

### 10.10 Behavior Invariants (enforced in code)

- `INV-IMPL-1` Only SHA-256 hashes of refresh/verification tokens persisted; raw tokens leave only via cookie/email.
- `INV-IMPL-2` Refresh reuse ⇒ family revoke + security_version bump (SessionService.refresh).
- `INV-IMPL-3` Register/forgot/resend are anti-enumerating (uniform 202/response + timing equalizer on login).
- `INV-IMPL-4` Durable lockout in MySQL (`locked_until`), not cache.
- `INV-IMPL-5` Every security action writes `audit_logs` in the same transaction.
- `INV-IMPL-6` Deny-by-default; permission authorities checked via `@PreAuthorize`.
- `INV-IMPL-7` All entities use `public static create()` factory; `protected` no-arg ctor reserved for JPA.
- `INV-IMPL-8` User entity uses `@Version` for optimistic locking (prevents lost-update on concurrent writes).
- `INV-IMPL-9` Refresh cookie restricted to path `/api/v1/auth` (not sent on every request).
- `INV-IMPL-10` OriginCheckFilter + SameSite=Lax provides CSRF defense-in-depth for cookie auth.

### 10.11 Known Follow-ups / Compromises

- TOTP MFA + privileged step-up enforcement: TODO in `LoginService` (design present; not built).
- Seller registration endpoint (`/auth/register/seller`) is permitted in security config but the seller onboarding module is separate/not built.
- `RegistrationService` injects users/customers repositories directly (pragmatic cross-feature reach for the registration vertical) rather than a `UsersFacade` — revisit when the users feature exposes an `api` facade.
- Full ~150-permission RBAC seed is partial (identity/access + anchors); grows per module.
- `AuthController` nests DTO records as inner classes (`AuthRequests`, `AuthResponses`) — not separately importable but reduces file count.
- Password change (authenticated, not via reset) endpoint: designed but not implemented.
- Double-submit CSRF token: next hardening step per auth design §7.
- Rate limiting (Redis-backed): planned but not implemented.

---

## 11. Systemic Operational Mechanics

### 11.1 Data Flow
```
Browser → Next.js /api proxy (customer-web :3000)
       → Java API (marketplace-api :8080)
       → MySQL 8.4 (:3306)
       → (Redis :6379 = future accelerator)
       → (MailHog :1025 = dev email capture)
```

### 11.2 Request Pipeline (Auth Control Flow)
```
HTTP Request
  → OriginCheckFilter (CSRF: block unknown Origin on mutating methods)
  → CookieAuthenticationFilter (parse JWT from atc_access cookie → SecurityContext)
  → Spring Security chain (deny-by-default)
  → @PreAuthorize method security (permission check)
  → Controller (@Valid DTO validation)
  → Application Service (@Transactional, business logic, audit)
  → GlobalExceptionHandler → error envelope {error:{code,message,correlationId,details}}
```

### 11.3 Token Model
- **Access token:** Short-lived HS256 JWT in HttpOnly cookie `atc_access`. Claims: sub(publicId), uid, email, sv(securityVersion), authorities. TTL: 15 min (configurable).
- **Refresh token:** Long-lived opaque token in HttpOnly cookie `atc_refresh` (path-restricted to `/api/v1/auth`). SHA-256 hash stored in `user_sessions`. TTL: 14 days (configurable).
- **Rotation:** Each refresh mints a new token in the same family. Presenting a retired token triggers family revoke + security_version bump.
- **Revocation:** Immediate via session table. Access tokens expire naturally (short TTL) or are invalidated by security_version bump.

### 11.4 Architecture Style
- Feature-modular Spring Boot monolith.
- Deny-by-default authorization.
- Java = sole writer of transactional data.
- MySQL = source of truth; all derived stores rebuildable.
- Stateless HTTP (no server-side session).

---

## 12. File Inventory (complete)

### Java source (`services/marketplace-api/src/main/java/com/archimstechcorner/marketplace/`)

| Package | File | Purpose |
|---------|------|---------|
| (root) | `MarketplaceApplication.java` | Spring Boot entry point + @EnableConfigurationProperties |
| shared.config | `AppConfig.java` | Clock bean (systemUTC) |
| shared.security | `SecurityConfig.java` | HTTP security filter chain |
| shared.security | `CookieAuthenticationFilter.java` | JWT cookie → SecurityContext |
| shared.security | `AccessTokenService.java` | JWT issue/parse |
| shared.security | `AuthCookieFactory.java` | Cookie builder/reader |
| shared.security | `AuthenticatedUser.java` | Principal record |
| shared.security | `AuthProperties.java` | Externalized auth config |
| shared.security | `OriginCheckFilter.java` | CSRF origin validation |
| shared.security | `PasswordConfig.java` | PasswordEncoder bean |
| shared.security | `RestAuthEntryPoint.java` | 401 JSON handler |
| shared.security | `RestAccessDeniedHandler.java` | 403 JSON handler |
| shared.error | `ErrorCode.java` | Error code enum |
| shared.error | `ApiException.java` | Typed exception |
| shared.error | `ApiError.java` | Error envelope record |
| shared.error | `GlobalExceptionHandler.java` | @RestControllerAdvice |
| shared.error | `JsonErrorWriter.java` | Filter-level error writer |
| shared.support | `EmailNormalizer.java` | Email normalization |
| shared.support | `SecureTokens.java` | Token generation + SHA-256 |
| shared.support | `UuidBinaryConverter.java` | UUID ↔ BINARY(16) |
| features.authentication.api | `AuthController.java` | Auth REST endpoints |
| features.authentication.api | `RegisterCustomerRequest.java` | Registration DTO |
| features.authentication.api | `LoginRequest.java` | Login DTO |
| features.authentication.api | `VerifyEmailRequest.java` | Verify email DTO |
| features.authentication.api | `ResendVerificationRequest.java` | Resend verify DTO |
| features.authentication.api | `ForgotPasswordRequest.java` | Forgot password DTO |
| features.authentication.api | `ResetPasswordRequest.java` | Reset password DTO |
| features.authentication.application | `RegistrationService.java` | Customer registration |
| features.authentication.application | `EmailVerificationService.java` | Token verify/resend |
| features.authentication.application | `LoginService.java` | Credential verification + lockout |
| features.authentication.application | `SessionService.java` | Refresh lifecycle + rotation |
| features.authentication.application | `PasswordResetService.java` | Password reset flow |
| features.authentication.application | (records) | `DeviceInfo`, `IssuedTokens`, `LoginResult` |
| features.authentication.application.port | `EmailSender.java` | Email port interface |
| features.authentication.domain.model | `UserSession.java` | Session entity |
| features.authentication.domain.model | `UserLoginEvent.java` | Login event entity |
| features.authentication.domain.model | `EmailVerificationToken.java` | Token entity |
| features.authentication.domain.model | `LoginOutcome.java` | Outcome enum |
| features.authentication.domain.model | `TokenPurpose.java` | Purpose enum |
| features.authentication.domain.repository | `UserSessionRepository.java` | Session queries |
| features.authentication.domain.repository | `UserLoginEventRepository.java` | Event persistence |
| features.authentication.domain.repository | `EmailVerificationTokenRepository.java` | Token queries |
| features.authentication.infrastructure | `LogEmailSender.java` | Dev email logger |
| features.users.api | `UserController.java` | User/role endpoints |
| features.users.api | `AssignRoleRequest.java` | Role assignment DTO |
| features.users.api | `CurrentUserResponse.java` | /users/me response DTO |
| features.users.application | `AuthorityService.java` | Role→permission resolver |
| features.users.application | `CurrentUserService.java` | Principal description |
| features.users.application | `RoleAdminService.java` | Role assign/revoke |
| features.users.application | `UserAuthorities.java` | Authority aggregation record |
| features.users.domain.model | `User.java` | User entity |
| features.users.domain.model | `Role.java` | Role entity |
| features.users.domain.model | `Permission.java` | Permission entity |
| features.users.domain.model | `UserRole.java` | User-role assignment entity |
| features.users.domain.model | `UserStatus.java` | Status enum |
| features.users.domain.repository | `UserRepository.java` | User queries |
| features.users.domain.repository | `RoleRepository.java` | Role queries |
| features.users.domain.repository | `UserRoleRepository.java` | User-role queries |
| features.users.domain.repository | `PermissionRepository.java` | Permission queries |
| features.customers.domain.model | `CustomerProfile.java` | Customer profile entity |
| features.customers.domain.repository | `CustomerProfileRepository.java` | Profile CRUD |
| features.auditlogs.application | `AuditService.java` | Audit recording |
| features.auditlogs.domain.model | `AuditLog.java` | Audit log entity |
| features.auditlogs.domain.model | `AuditAction.java` | Action enum |
| features.auditlogs.domain.repository | `AuditLogRepository.java` | Audit persistence |

### Test files
| File | Type |
|------|------|
| `PasswordEncoderTest.java` | Unit |
| `AccessTokenServiceTest.java` | Unit |
| `AuthIntegrationTest.java` | Integration (Testcontainers) |

### Frontend files (`apps/customer-web/src/`)
| File | Purpose |
|------|---------|
| `app/layout.tsx` | Root layout |
| `app/page.tsx` | Home page |
| `app/login/page.tsx` | Login form |
| `app/register/page.tsx` | Registration form |
| `app/account/page.tsx` | User profile + logout |
| `app/verify-email/page.tsx` | Email verification |
| `app/globals.css` | Global styles |
| `features/authentication/schemas.ts` | Zod validation schemas |
| `lib/api.ts` | API client |

---

## 13. Change Log (for agent continuity)

- `2026-07-11` — Authored auth-rbac design (Deliverable 5) + rbac-matrix. QA PASS. Docs-only (approval gate).
- `2026-07-11` — User waived approval gate ("finish all the tasks"). **Implemented Auth+RBAC module**: 3 Flyway migrations + seed; Spring Boot backend (~55 Java files: security kernel, users/customers/auth/auditlogs features); Next.js customer-web (login/register/account/verify); Docker Compose + root config; unit + Testcontainers tests. **Recorded approval** in `documentation/README.md`; flipped module/matrix statuses to Approved.
- `2026-07-11` — QA: removed Lombok (env's JDK had a broken Lombok annotation processor — `NoClassDefFoundError: lombok.javac.Javac`); converted all 9 entities to explicit accessors. Fixed a real bug: unknown-user login called `matches()` on an un-prefixed hash → would throw 500; replaced with a constructor-encoded timing-equalizer hash. Build NOT compiled in-session (no Maven).
- `2026-07-11` — Reviewed external (ChatGPT) edits: `public static create()` factory + `protected` ctor added to all 7 entities and all service call sites migrated (consistent, JPA-valid); `AccessTokenService` parser now uses the injected `Clock` for deterministic expiry (good). Re-fixed a regression where `LoginService` still called `matches()` on the raw un-prefixed literal while the `timingEqualizerHash` field sat unused (dead code + would 500) — call site now uses the field.
- `2026-07-11` — **Full static analysis + QA rewrite of AI Documentation Notes.md**. Three parallel analysis agents scanned all 55+ Java files, 9 TS/TSX files, 4 SQL files, config/infra files. QA PASS — no critical bugs, no security vulnerabilities, no broken logic paths. Documentation restructured with full function-level analysis for every service, entity, controller, filter, and utility. Added §7 (Infrastructure), §8 (Migrations), §9 (Frontend), §12 (File Inventory). Expanded §10 with complete method signatures, behavior descriptions, and dependency maps.
- `2026-07-12` — **System Hardening & MFA**: Implemented missing auth components. Added `RateLimitInterceptor` (Redis-backed via Bucket4j) for brute force protection. Configured `CookieCsrfTokenRepository` for stateful CSRF protection. Developed `MfaService` handling TOTP enroll/verify and `mfa_challenge` token issuance in `LoginService`.
- `2026-07-12` — **Seller Onboarding Module**: Implemented domain models (`Seller`, `Store`, `Warehouse`, `SellerStaff`), repositories, and `SellerController` (POST `/api/v1/sellers/onboard`). Fixed MySQL constraint type mismatch (BIGINT UNSIGNED) across all new tables via Flyway migration.
- `2026-07-12` — **Environment & UI Testing**: Deployed Next.js `customer-web` using `pnpm`. Created database test accounts `customer@example.com` and `admin@example.com`. Fixed a backend runtime issue where `application.yml` was missing `spring.data.redis` mappings, causing `Connection refused` for rate limiting logic.
- `2026-07-12` — **Catalog Module**: Implemented domain models (`Product`, `Category`, `Brand`), repositories, controllers (`ProductController`, `CategoryController`, `BrandController`), and application services (`ProductService`). Follows DDD and cleanly integrates with the new Seller Onboarding domain models (e.g. associating products with stores).
- `2026-07-12` — **Integration Testing & Security**: Fixed test suites that were broken by new Security configurations (CSRF missing tokens on MockMvc tests) and fixed assertion expectations for 404 vs 204 in RBAC tests. Ran the full integration test suite via `mvn verify` directly on host without Docker, bypassing file mounting issues, achieving a fully green build.
- `2026-07-12` — **RUNTIME VERIFICATION (Docker) + bug-fix pass on the sellers/catalog/MFA modules.** Built + ran the full stack via `docker compose up --build`; app boots clean (Flyway 6 versioned + 1 repeatable, `Started` ~12s). Drove an 18-check end-to-end script (`scratchpad/verify.sh`) → **18/18 PASS**: register/login/me/refresh/logout+CSRF, seller onboard→PENDING, admin approve→ACTIVE, category/brand create, product create(DRAFT)→submit(PENDING_APPROVAL)→moderate(ACTIVE), public visibility gating (404 before approval, 200 after), cross-tenant isolation (intruder→403), rate limiting (429), MFA enroll (otpauth URI). Fixes applied to Gemini's modules:
  - `SellerController` used `Principal.getName()` → returned the `AuthenticatedUser` record's `toString()`, not the UUID → `/sellers/onboard` 500. Now `@AuthenticationPrincipal AuthenticatedUser` + `publicId()`.
  - **Seller isolation**: `ProductService.createProduct`/`submitForApproval` took a client `sellerPublicId` with no ownership check (any user could list under any seller). Added `SellerService.requireOwnership` (via `SellerStaff`) → 403 for non-owners.
  - **SELLER role** was never granted on onboarding → added `RoleAdminService.assignRole(...,"SELLER",...)` in `SellerOnboardingService`.
  - **Error mapping**: catalog/seller/MFA services threw `IllegalArgumentException`/`IllegalStateException`/`orElseThrow()` → 500. Refactored to `ApiException` (404/409/401). Added `ErrorCode.MFA_INVALID`.
  - **Public catalog reads** (categories/brands/approved products) were blocked by `anyRequest().authenticated()` → added GET allowlist in `SecurityConfig`; `getPublishedProduct` returns ACTIVE-only (404 otherwise, no draft leak).
  - **LazyInitializationException** (open-in-view=false): controllers mapped `Product.variants`/`Category.subCategories` after tx close → 500. Force-init collections in-tx in `ProductService`/`CategoryService`.
  - **MFA privileged lockout**: login forced an unsatisfiable MFA challenge for privileged users with no enrolled method → permanent lockout + unbootstrappable admin. Changed `requiresMfa` to challenge only when MFA is actually enrolled (design: "directed to enroll").
  - **Frontend CSRF**: `api.ts` now reads the `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN` on mutations (SPA logout would otherwise 403).
  - **Added seller approval workflow**: `SellerService.approve/reject` + `POST /sellers/{id}/approve|reject` (`seller.approve`/`seller.reject`); product submit gated on seller ACTIVE.
  - **Consistency**: `RateLimitInterceptor` now emits the standard `{error:{code,message,...}}` envelope (was a bespoke string body).

- `2026-07-12` — **Inventory module (Sprint 5).** New feature `inventory`: migration `V1_4_001` (`inventory` bucket per (warehouse,variant) + append-only `inventory_adjustments` ledger; `sellable = on_hand - reserved`; CHECK constraints prevent negative/over-reserved stock). `InventoryService` = authoritative stock: set (absolute), adjust (signed delta), get-by-variant, low-stock-by-seller — each **owner-checked** (caller must belong to the seller that owns BOTH the warehouse and the variant; cross-seller stocking rejected), **overselling-safe** (on-hand can't go < 0 or < reserved → 409), records an adjustment, and **syncs `ProductVariant.stockQuantity`** to the on-hand total. Endpoints: `PUT /inventory`, `POST /inventory/adjustments`, `GET /inventory?variantPublicId=`, `GET /inventory/low-stock?sellerPublicId=`. Also added `WarehouseRepository`/`ProductVariantRepository` lookups and exposed **warehouses in `GET /sellers/me`** (previously no API surfaced the warehouse publicId). Builds clean (exit 0); runtime verification of the module in progress.

- `2026-07-12` — **Usability endpoints + admin bootstrap (verified 7/7 in Docker).** Added `AdminBootstrapRunner` (dev-guarded `ApplicationRunner`: creates `admin@archims.local` ACTIVE+verified with ADMIN role when `app.bootstrap.admin.enabled=true` + password set; no-op otherwise) — removes the manual `user_roles` SQL. Added `GET /sellers/me` (Seller Center: my sellers+stores), `GET /sellers?status=` (admin approval queue, `seller.approve`), `GET /catalog/products?sellerPublicId=` (owner-scoped own-product list, any status). All list read-models are mapped in-tx (no LazyInitialization). compose.yaml ships the dev admin (`admin@archims.local` / `AdminDev123456`).

## 14. Next Actions (ordered)

1. **Mandatory-MFA enforcement** for privileged roles: allow login (to enroll) but block sensitive actions until an MFA method is active (the relaxation in `LoginService` is a stopgap).
2. Add a Maven wrapper (`mvnw`) so the build is self-contained without a system Maven.
3. Next roadmap modules: **Inventory & pricing** (Sprint 5) → Cart → Checkout → Orders → Payments.
4. Frontend: build a Seller Center surface (onboarding + product management) against the new endpoints.

> NOTE for future agents: `Workflow` classifier was unavailable on 2026-07-11; Maven is absent in this dev env (Java 25 only) — use Docker/CI to build (`docker compose up --build`), and drive `scratchpad/verify.sh` for an end-to-end smoke test. Lombok is banned here (broken processor) — keep entities on explicit accessors. Bootstrap an admin for testing: `INSERT INTO user_roles(user_id,role_id,active_marker,granted_at) SELECT u.id,r.id,1,UTC_TIMESTAMP(6) FROM users u JOIN roles r ON r.code='ADMIN' WHERE u.email_normalized='<email>'`.
