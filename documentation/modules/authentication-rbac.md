# Module Design — Authentication and Role-Based Access Control

| Document property | Value |
|---|---|
| Deliverable | 5 — First Development Module (design phase) |
| Module | Authentication, Users, and Role-Based Access Control |
| Status | ✅ Approved 2026-07-11; implemented in `services/marketplace-api` (see [AI Documentation Notes.md](../../AI%20Documentation%20Notes.md)). TOTP MFA + phone/social channels remain deferred follow-ups. |
| Owning Java features | `authentication`, `users`, `customers`, `sellers` (registration seam only), plus the shared `security` kernel |
| Depends on | [System architecture](../architecture/system-architecture.md), [MySQL design](../database/mysql-design.md), [Project structure](../architecture/project-structure.md), [MVP roadmap](../roadmap/mvp-roadmap.md) |
| Design method | The twelve-step module procedure in master-prompt Section 27 |

> **Scope note.** This document defines the module *design* only: goal, requirements, tables, endpoints, validation, business rules, security rules, error cases, and the implementation/test/verification plan. It does not contain application code. Per the baseline approval gate ([README](../README.md)), migrations and code are written only after this design is accepted. Steps 9–10 (code, tests) are therefore expressed as an implementation and test plan, not as finished source.

---

## 1. Goal

Deliver the identity foundation every other module depends on: a single, server-authoritative way to prove who a caller is (**authentication**) and to decide what that caller may do (**authorization**). Spring Security in the Java backend is the sole identity authority; the three Next.js applications are clients that render screens and forward credentials, never deciding access themselves.

The module must let the marketplace:

- Register customers and sellers, verify their email, and sign them in and out securely.
- Hash passwords with an approved adaptive algorithm and never store or log a recoverable secret.
- Issue short-lived access tokens and rotating refresh tokens, detect refresh-token reuse, and revoke sessions.
- Enforce role and permission checks on every protected endpoint with deny-by-default semantics.
- Require MFA for privileged roles and support MFA recovery.
- Apply rate limiting, account lockout, device/login history, and suspicious-login detection.
- Emit an immutable audit trail for every security-relevant action.

This maps to roadmap **Gate B** (Sprint 1–2): *"identity security review and role-isolation suite pass."*

### 1.1 In scope (MVP / P0)

Email + password registration and login for customers and sellers; email verification; refresh-token rotation with reuse detection; logout and session revocation; password reset; TOTP MFA enrollment/verification with mandatory enforcement for privileged roles; MFA recovery codes; rate limiting and lockout; device and login history; suspicious-login alerts; role and permission administration; the RBAC enforcement mechanism used by all other modules; audit logging.

### 1.2 Out of scope (deferred, P2 — see roadmap §2)

Phone registration and SMS verification; social / federated login; WebAuthn/passkeys. The schema reserves seams for these (`user_federated_identities`, `user_mfa_methods` supporting non-TOTP factors, normalized phone on `users`) but no flow is built for them in the MVP. They ship only after provider, privacy, account-linking, and recovery rules are approved.

---

## 2. Requirements

### 2.1 Functional requirements

| # | Requirement |
|---|---|
| FR-1 | A guest can register as a customer with email, password, and display name; the account starts unverified and cannot check out until email is verified. |
| FR-2 | A user can register (or an existing user can apply) as a seller; seller onboarding/approval is a separate module, but the seller *account principal* and the `SELLER` role assignment originate here. |
| FR-3 | A user verifies email via a single-use, expiring, hashed token delivered by email. |
| FR-4 | A user logs in with email + password and receives an access token (short-lived) and a refresh token (rotating), both in `Secure`, `HttpOnly` cookies. |
| FR-5 | Each refresh rotates the token and invalidates its predecessor; reuse of an invalidated token revokes the whole family and raises a security event. |
| FR-6 | A user logs out, revoking the current session; a user or admin can revoke one or all sessions. |
| FR-7 | A user requests a password reset via a single-use, expiring, hashed token; a successful reset revokes all existing sessions. |
| FR-8 | A user enrolls a TOTP MFA method (QR provisioning + verification) and receives one-time recovery codes shown exactly once. |
| FR-9 | Privileged roles (`ADMIN`, `FINANCE`, `SUPPORT`, `CONTENT`) cannot complete login to privileged surfaces without an active MFA method; step-up MFA is required for high-risk actions. |
| FR-10 | Every protected endpoint authorizes by role + permission + resource ownership/state, denying by default. |
| FR-11 | Repeated failed logins trigger throttling and time-boxed lockout; the durable lock lives in MySQL, not only Redis. |
| FR-12 | Login attempts (success and failure) and device/user-agent/IP metadata are recorded as history; anomalous logins raise an alert. |
| FR-13 | An administrator can create/assign/revoke roles and permissions; changes are time-stamped, attributed, and audited. |
| FR-14 | Every security-relevant action writes an append-only `audit_logs` entry with redacted before/after context. |

### 2.2 Non-functional requirements

- **Security first:** Argon2id (preferred) or BCrypt password hashing with centrally managed parameters; no secret, token, or PII in logs, traces, error reports, or client bundles.
- **Performance:** authenticated authorization decision adds < 10 ms server-side p95; login endpoint p95 < 300 ms excluding deliberate hashing cost; token refresh p95 < 150 ms.
- **Availability:** identity endpoints are stateless and horizontally scalable; Redis accelerates but never owns durable identity truth.
- **Auditability:** all privileged and security actions are attributable and tamper-evident.
- **Testability:** deny/allow RBAC matrix, token-reuse, lockout, and cross-tenant isolation are automated (roadmap Milestone "Identity security baseline", Sprint 2).

---

## 3. Database tables

All tables use InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`, UTC `DATETIME(6)`, unsigned `BIGINT` surrogate `id`, and follow the conventions and constraints already specified in [mysql-design.md §6.1](../database/mysql-design.md). This module *owns and writes* the following (no new tables are introduced beyond the baseline catalog; this section states the concrete columns and the migration slice for the auth vertical):

| Table | Ownership in this module | Key columns this module writes |
|---|---|---|
| `users` | Owner | `public_id`, `email_normalized`, `phone_normalized?`, `password_hash`, `password_algo`, `email_verified_at?`, `phone_verified_at?`, `status`, `locked_until?`, `failed_login_count`, `security_version`, `created_at`, `updated_at`, `version`, `deleted_at?` |
| `roles` | Owner (reference data via repeatable/reference seed) | `code`, `name`, `is_active` |
| `permissions` | Owner (reference data) | `code`, `module`, `description`, `is_active` |
| `role_permissions` | Owner | `(role_id, permission_id)` |
| `user_roles` | Owner | `user_id`, `role_id`, `active_marker`, `granted_by_user_id`, `granted_at`, `expires_at?`, `revoked_by_user_id?`, `revoked_at?` |
| `customer_profiles` | Owner (registration slice) | `user_id`, `public_id`, `display_name`, `locale`, `timezone` |
| `user_sessions` | Owner | `public_id`, `user_id`, `token_hash`, `device_label`, `ip_hash?`, `rotation_family_id`, `replaced_by_session_id?`, `issued_at`, `expires_at`, `revoked_at?`, `revoke_reason?` |
| `user_login_events` | Owner (append-only) | `user_id?`, `outcome`, `reason_code`, `ip_hash?`, `user_agent_hash?`, `device_label?`, `occurred_at` |
| `user_mfa_methods` | Owner | `public_id`, `user_id`, `type`, `secret_encrypted?`, `status`, `verified_at?`, `last_used_at?` |
| `user_recovery_codes` | Owner (append-only issue + consume) | `user_id`, `mfa_method_id?`, `code_hash`, `batch_id`, `expires_at?`, `used_at?` |
| `user_federated_identities` | Owner (reserved; unused in MVP) | schema only |
| `email_verification_tokens` *(supporting)* | Owner | `user_id`, `token_hash`, `purpose` (`EMAIL_VERIFY`/`PASSWORD_RESET`), `expires_at`, `consumed_at?` |
| `audit_logs` | Writer | `event_uuid`, `actor_user_id?`, `action`, `subject_type`, `subject_id`, `correlation_id`, `before_redacted?`, `after_redacted?`, `occurred_at` |

Notes and additions to the baseline:

- **`email_verification_tokens`** is a supporting table added here to hold single-use email-verification and password-reset token hashes with a `purpose` discriminator and `expires_at`; only the hash is stored, never the raw token. (This complements `user_recovery_codes`, which are MFA-specific.)
- **`security_version`** on `users` is an integer bumped on password change / global revocation so any still-valid access token can be rejected without waiting for expiry.
- **At-most-one-active-per-scope** rules (`user_roles`, one default customer address, one active TOTP method) use the documented non-null `active_marker` pattern, never nullable-uniqueness accidents (mysql-design §4.5).
- **Idempotency:** registration and password-reset commands claim an `idempotency_records` row (mysql-design §6.12) so a retried submission returns the original result instead of a duplicate account.

### 3.1 Seed reference data (roles & permissions)

Roles (`roles.code`): `CUSTOMER`, `SELLER`, `ADMIN`, `SUPPORT`, `FINANCE`, `CONTENT`. Permissions use `module.action` codes, e.g. `users.read`, `users.role.assign`, `sellers.approve`, `catalog.moderate`, `orders.read.any`, `refunds.approve`, `payouts.approve`, `audit.read`. Role→permission mapping ships as a **repeatable** Flyway seed (`R__seed_roles_permissions.sql`) so the RBAC matrix evolves by migration, reviewed like code. Concrete rows are defined in the forthcoming `documentation/security/rbac-matrix.md`.

---

## 4. API endpoints

Base path `/api/v1`. All responses use the shared error envelope (§8). State-changing endpoints require CSRF protection when cookie-authenticated (origin check + Spring Security CSRF token). Public auth endpoints are additionally rate-limited by IP and, where applicable, by account.

| Method & path | Auth | Purpose | Idempotency |
|---|---|---|---|
| `POST /auth/register/customer` | Public | Create unverified customer, assign `CUSTOMER`, send verification email | Idempotency-Key |
| `POST /auth/register/seller` | Auth or Public | Create/attach seller principal, assign `SELLER`, start onboarding | Idempotency-Key |
| `POST /auth/email/verify` | Public (token) | Consume verification token, set `email_verified_at` | Single-use token |
| `POST /auth/email/verify/resend` | Public | Re-issue verification email (rate-limited, always 202) | — |
| `POST /auth/login` | Public | Verify credentials, evaluate MFA, issue cookies | — |
| `POST /auth/login/mfa` | Partial (MFA challenge) | Complete TOTP/recovery challenge, issue full session | — |
| `POST /auth/token/refresh` | Refresh cookie | Rotate refresh token, issue new access token | Rotation family |
| `POST /auth/logout` | Auth | Revoke current session | — |
| `POST /auth/sessions/revoke-all` | Auth | Revoke all sessions, bump `security_version` | — |
| `GET /auth/sessions` | Auth | List active sessions / device history | — |
| `POST /auth/password/forgot` | Public | Issue reset token by email (always 202) | — |
| `POST /auth/password/reset` | Public (token) | Consume reset token, set new hash, revoke sessions | Single-use token |
| `POST /auth/password/change` | Auth (+ step-up) | Change password for signed-in user | — |
| `POST /auth/mfa/totp/enroll` | Auth | Begin TOTP enrollment, return provisioning secret/QANDA | — |
| `POST /auth/mfa/totp/activate` | Auth | Verify first TOTP code, activate method, issue recovery codes | — |
| `POST /auth/mfa/recovery/regenerate` | Auth (+ step-up) | Invalidate old batch, issue new recovery codes | — |
| `DELETE /auth/mfa/{methodPublicId}` | Auth (+ step-up) | Remove an MFA method (blocked if it would leave a privileged role without MFA) | — |
| `GET /users/me` | Auth | Current principal, roles, permissions, verification/MFA state | — |
| `GET /users/{publicId}` | `users.read` | Admin user lookup | — |
| `POST /users/{publicId}/roles` | `users.role.assign` | Assign a role (time-bounded) | — |
| `DELETE /users/{publicId}/roles/{roleCode}` | `users.role.assign` | Revoke a role | — |
| `POST /users/{publicId}/lock` / `/unlock` | `users.manage` | Administrative lock/unlock | — |
| `GET /roles`, `GET /permissions` | `users.role.assign` | Read RBAC vocabulary | — |

OpenAPI definitions live in `contracts/openapi/` (`marketplace-public-v1.yaml` for customer/auth, `administration-v1.yaml` for user/role admin), per project-structure §6. The Java service is the behavioral owner; the TypeScript `api-client` is generated from these.

---

## 5. Validation rules

Validated at the DTO boundary (Bean Validation on Java; Zod on the clients) and again in the application service for business invariants.

- **Email:** RFC-compatible syntax, ≤ 254 chars, normalized to lowercase + trimmed; stored normalized for uniqueness, original case retained for display only.
- **Password:** ≥ 12 chars, ≤ 128; must not equal email/display name; screened against a common-password/breached list; never logged, never echoed.
- **Display name:** 2–60 chars, Unicode letters/marks/spaces/limited punctuation; stripped of control characters.
- **Phone (reserved):** E.164 normalized when present; unused in MVP flows.
- **Tokens (verify/reset/refresh/recovery):** opaque high-entropy (≥ 256-bit) random values; compared by constant-time hash lookup; expiry mandatory.
- **TOTP code:** 6 digits, validated within a ±1 step window; each code single-use within its step.
- **Role/permission codes:** must exist and be active; role assignment `expires_at` (if present) must be in the future.
- **Idempotency-Key:** required on register and reset; bound to a canonical request hash — same key + different body is a `409 CONFLICT`.
- **Pagination:** keyset (`created_at,id`) for session and user lists; bounded page size.

---

## 6. Business rules

1. **Single identity authority.** Only the Java backend mints, rotates, or revokes tokens and evaluates authorization. NextAuth/BFF, if used, only stores cookies server-side and forwards requests.
2. **Registration.** A new customer starts `PENDING_VERIFICATION`; `CUSTOMER` role assigned atomically with the `users` + `customer_profiles` rows and an outbox `user.registered.v1` event that triggers the verification email. Duplicate email → deterministic conflict, no enumeration in the response.
3. **Verification.** Consuming a valid `EMAIL_VERIFY` token sets `email_verified_at`, transitions status to `ACTIVE`, and is idempotent (already-verified → success without side effects). Unverified customers may browse but not check out (enforced by `checkout` module via `users.me` state).
4. **Login & MFA.** Password verified with constant-time comparison against the stored hash. If the user holds a privileged role or has an active MFA method, login returns an `MFA_REQUIRED` challenge; a full session is issued only after `POST /auth/login/mfa`. Privileged roles without an active MFA method cannot obtain a privileged session (they are directed to enroll).
5. **Refresh rotation.** Each refresh issues a new token in the same `rotation_family_id`, sets `replaced_by_session_id`, and marks the old session used. Presenting a used/revoked token revokes the entire family, bumps `security_version`, writes an audit event, and returns `401`.
6. **Logout / revocation.** Logout revokes the current session; revoke-all revokes every session and bumps `security_version` so outstanding access tokens fail validation immediately.
7. **Password reset.** Always responds `202` regardless of whether the email exists (no enumeration). A successful reset writes a new hash, bumps `security_version`, revokes all sessions, and consumes the token single-use.
8. **Lockout.** After N failed attempts within a window, set `locked_until`; the durable lock is in MySQL. Redis counters accelerate detection but a Redis flush never unlocks an account early.
9. **Role administration.** Assignments are time-bounded and attributed (`granted_by_user_id`); revocations are immutable history rows (`revoked_at`/`revoked_by_user_id`), never hard deletes. Separation-of-duties: `FINANCE` and `SUPPORT` capability sets are disjoint where policy requires.
10. **Auditability.** Registration, verification, login success/failure, MFA changes, password changes, session revocation, role/permission changes, and administrative lock/unlock each append an `audit_logs` row in the same transaction as the state change.

State families (from mysql-design §8.1): `users.status ∈ {PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DISABLED, DELETED}`; `user_sessions` is issued → (rotated) → revoked/expired; `user_mfa_methods` is `PENDING → ACTIVE → (REVOKED)`.

---

## 7. Security rules

- **Hashing:** Argon2id with centrally configured memory/iterations/parallelism (BCrypt cost ≥ 12 as an approved fallback). Parameters are configuration, upgradeable via re-hash on next successful login.
- **Tokens & cookies:** access token short-lived (≈ 5–15 min), refresh token rotating (≈ 7–30 days idle-capped); both in `Secure`, `HttpOnly`, `SameSite=Lax` cookies scoped as narrowly as deployment allows. Only refresh-token **hashes** are stored, with user/device/expiry/family/revocation.
- **CSRF:** cookie-authenticated state-changing requests require an origin check plus the Spring Security CSRF token; `SameSite` is defense in depth only.
- **Rate limiting & lockout:** per-IP and per-account throttles on login, verify-resend, forgot-password, and MFA challenge; time-boxed lockout with durable MySQL backstop.
- **Enumeration resistance:** registration, verification-resend, forgot-password, and login return uniform responses/timings that do not reveal account existence.
- **MFA:** TOTP secrets encrypted with an external KMS key, never returned after activation; recovery codes hashed, shown once, single-use; privileged roles require an active method; sensitive actions require step-up.
- **Authorization:** deny-by-default; every endpoint checks role + permission and, for resource access, ownership/tenant/state in the application service — never UI visibility. Seller-staff receive store-scoped permissions only.
- **Least privilege & data handling:** the Java runtime uses its own MySQL account (no DDL); IP/user-agent are hashed or minimized and retention-limited; no secret, token, password, or raw PII appears in logs, traces, Sentry, fixtures, or client bundles.
- **Audit integrity:** `audit_logs` is append-only, access-controlled, and alerts on attempted mutation/purge outside the retention workflow.

The full guest/customer/seller/seller-staff/admin/support/finance/content allow-and-deny matrix is delivered as `documentation/security/rbac-matrix.md` and enforced by the Sprint-2 authorization test suite (roadmap §7).

---

## 8. Error cases

Uniform error envelope: `{ "error": { "code", "message", "correlationId", "details?[] } }`. Messages are safe for clients; specifics go to logs keyed by `correlationId`.

| Scenario | HTTP | `error.code` | Notes |
|---|---|---|---|
| Malformed/invalid input | 400 | `VALIDATION_FAILED` | `details[]` per field; no secret echoed |
| Missing/invalid credentials on login | 401 | `INVALID_CREDENTIALS` | Uniform message; no enumeration |
| MFA required | 401 | `MFA_REQUIRED` | Returns challenge id, not a session |
| Invalid/expired MFA code | 401 | `MFA_INVALID` | Counts toward throttle |
| Account locked | 423 | `ACCOUNT_LOCKED` | Includes retry-after; durable lock |
| Email not verified where required | 403 | `EMAIL_NOT_VERIFIED` | Browsing allowed; checkout blocked |
| Insufficient permission | 403 | `FORBIDDEN` | Deny-by-default; audited |
| Refresh token reused/revoked | 401 | `SESSION_REVOKED` | Revokes family, raises security event |
| Verification/reset token invalid or expired | 400 | `TOKEN_INVALID` | Single-use; already-consumed → same code |
| Duplicate email on register | 409 | `EMAIL_TAKEN` *(non-enumerating variant)* | Prefer generic success + email "already registered" notice; see §6.2 |
| Idempotency key reused with different body | 409 | `IDEMPOTENCY_CONFLICT` | Same key+body returns original result |
| Removing last MFA for privileged role | 409 | `MFA_REQUIRED_FOR_ROLE` | Blocked |
| Rate limit exceeded | 429 | `RATE_LIMITED` | Retry-after header |
| Unexpected server error | 500 | `INTERNAL_ERROR` | No stack/PII to client |

A global `@RestControllerAdvice` maps exceptions to this envelope; no raw stack trace or provider payload ever reaches the client.

---

## 9. Implementation plan (code — gated on approval)

Concrete source is written only after approval. Planned shape (project-structure §4), feature `authentication` with the standard `api / application / domain / infrastructure` layers, collaborating with `users`, `customers`, and `sellers` via their `api` facades:

- **`api/`** — `AuthController`, `UserAdminController`; request/response DTOs; Bean Validation annotations.
- **`application/`** — commands (`RegisterCustomerCommand`, `VerifyEmailCommand`, `LoginCommand`, `CompleteMfaCommand`, `RefreshTokenCommand`, `RevokeSessionCommand`, `RequestPasswordResetCommand`, `ResetPasswordCommand`, `EnrollTotpCommand`, `AssignRoleCommand`), queries (`GetCurrentPrincipalQuery`, `ListSessionsQuery`), and `port` interfaces (`PasswordHasher`, `TokenService`, `MfaService`, `EmailNotificationPort`, `AuditPort`).
- **`domain/`** — `User`, `Role`, `Permission`, `Session`, `MfaMethod` aggregates; transition policies; domain events (`user.registered.v1`, `user.email_verified.v1`, `session.revoked.v1`, `user.role_changed.v1`).
- **`infrastructure/`** — Spring Data JPA adapters (entities private to the feature), Argon2/BCrypt hasher, TOTP (RFC 6238) implementation, Redis rate-limit/lockout accelerator, outbox publisher, Spring Security filter chain (cookie/CSRF/authorization), `@RestControllerAdvice`.
- **Migrations** — Flyway `versioned/identity/` slice: `V1_1_001__create_users.sql` … through sessions, mfa, recovery codes, verification tokens, roles/permissions/user_roles; plus `R__seed_roles_permissions.sql`. Names follow project-structure §7 (`pk_/fk_/uk_/idx_`).
- **Frontend** — `apps/customer-web` and `apps/seller-center` `authentication` feature (login/register/verify/reset/MFA screens) using React Hook Form + Zod + the generated `api-client`; `admin-dashboard` login enforces MFA. Route guards are UX only; Java authorizes.
- **Docker** — Compose services for `marketplace-api`, MySQL, Redis, RabbitMQ, and a mail sandbox (e.g. MailHog) so verification/reset emails are inspectable locally.

---

## 10. Test plan (Gate B evidence)

Automated across layers (roadmap §7, Milestone "Identity security baseline"):

- **Unit** — password hashing/verify + parameter upgrade; TOTP window; token generation/entropy; validation rules; transition policies.
- **Integration (Testcontainers, real MySQL 8)** — registration + verification happy path and idempotent replay; login with/without MFA; refresh rotation; **refresh-reuse revokes family**; logout/revoke-all invalidates access via `security_version`; password reset revokes sessions; durable lockout survives Redis flush; role assign/revoke history.
- **Authorization matrix** — full guest/customer/seller/seller-staff/admin/support/finance/content allow-and-deny suite, including **cross-tenant denial** (seller A cannot touch seller B) and deny-by-default on unmapped endpoints.
- **Security** — enumeration-resistance timing on register/forgot/login; CSRF rejection of forged origins; rate-limit/lockout behavior; no secret/token/PII in logs, fixtures, client bundles (grep + log scrub tests); CI check that the Java DB account cannot run DDL.
- **Contract** — OpenAPI examples validate; generated client compiles against `apps/`.
- **E2E (Playwright)** — customer register→verify→login→logout; admin login enforces MFA; password reset flow.

Coverage target for this critical module: ≥ 90% branch (per roadmap Gate E), with the reuse/lockout/authorization paths mandatory.

---

## 11. Setup and run

After approval, from the repo root:

1. Copy `.env.example` → `.env`; set dev-only secrets (JWT/cookie keys, Argon2 params, mail sandbox).
2. `docker compose up -d mysql redis rabbitmq mailhog` — start dependencies.
3. Flyway runs the `identity` migration slice as a controlled step (`./mvnw -pl services/marketplace-api flyway:migrate`), then the app boots (`./mvnw -pl services/marketplace-api spring-boot:run`).
4. `pnpm --filter customer-web dev` (and `seller-center`, `admin-dashboard`) — start the web apps against the API.
5. Regenerate the API client from `contracts/openapi/` when endpoints change.

## 12. How to verify

- **Manual:** register a customer → check MailHog for the verification email → verify → log in → hit `GET /users/me` and confirm roles/permissions/verification state → log out and confirm the session is revoked. Enroll TOTP on an admin account and confirm the `admin-dashboard` requires the code.
- **Automated:** `./mvnw -pl services/marketplace-api verify` (unit + Testcontainers + authorization matrix) and `pnpm --filter customer-web test:e2e` for the identity journeys — the same suites gate CI and Gate B.
- **Security spot checks:** confirm forgot-password returns `202` for unknown emails; confirm a reused refresh token returns `401 SESSION_REVOKED` and revokes the family; confirm a Redis flush does not clear an active `locked_until`.

---

## 13. Open approval items for this module

1. Confirm Argon2id parameters (or approved BCrypt cost) and the KMS key source for MFA secrets.
2. Confirm access/refresh token lifetimes and cookie scope for the three apps' deployment topology.
3. Approve the non-enumerating registration response style (generic success vs. `409 EMAIL_TAKEN`).
4. Approve the concrete role→permission matrix in `documentation/security/rbac-matrix.md` (next deliverable).
5. Confirm lockout thresholds/windows and login-anomaly signals with security.
6. Confirm which privileged actions require step-up MFA vs. dual approval.

Once these are settled and the design is approved, the next artifacts are the **RBAC matrix document**, then the Flyway `identity` migration slice and the module implementation with its automated tests.
