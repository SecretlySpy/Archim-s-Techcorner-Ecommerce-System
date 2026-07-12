# Archim's TechCorner — Design Baseline

**Status:** ✅ Approved — 2026-07-11  
**Prepared:** 2026-07-11  
**Implementation status:** Authentication & RBAC module implemented (`services/marketplace-api`, `database/migrations`, `apps/customer-web`)

This directory contains the four planning deliverables plus the first module's design:

1. [System architecture](architecture/system-architecture.md)
2. [MySQL database design](database/mysql-design.md)
3. [Project structure](architecture/project-structure.md)
4. [MVP roadmap](roadmap/mvp-roadmap.md)
5. [Authentication & RBAC module design](modules/authentication-rbac.md) + [RBAC matrix](security/rbac-matrix.md)

## Scope of this baseline

The baseline defines system boundaries, data ownership, transactional invariants, repository organization, delivery order, test gates, and beta criteria.

The first implementation module — Authentication and Role-Based Access Control — has been **approved and implemented**: Flyway `identity` migrations, the Spring Boot module (`services/marketplace-api`), the RBAC seed, automated tests, Docker Compose runtime, and the Next.js customer login/register interface. See [AI Documentation Notes.md](../AI%20Documentation%20Notes.md) for the machine-readable implementation map.

## Decisions (approved 2026-07-11)

| ID | Proposed decision | Reason |
|---|---|---|
| BASE-01 | Begin with one feature-modular Spring Boot application. | Preserves transactional simplicity while keeping extraction boundaries visible. |
| BASE-02 | Make the Java backend the only writer of transactional marketplace data; MySQL is the source of truth. | Prevents split-brain state across Java, Python, Redis, and OpenSearch. |
| BASE-03 | Use three independently deployable Next.js applications backed by shared UI, API-client, and type packages. | Separates customer, seller, and administrative release/security surfaces without duplicating foundations. |
| BASE-04 | Use short reservation and finalization transactions around external payment authorization. Never keep MySQL row locks open during a provider network call. | Avoids lock exhaustion, deadlocks, and checkout collapse during provider latency while retaining rollback through expiry and compensation. |
| BASE-05 | Use a transactional outbox and idempotent consumers for RabbitMQ publishing. | Prevents database commits from becoming inconsistent with search, notification, and analytics events. |
| BASE-06 | Make Spring Security the identity authority and store rotated refresh-token credentials in `Secure`, `HttpOnly` cookies. | Avoids a second identity source in Next.js and limits browser token exposure. |
| BASE-07 | Use `BIGINT` internal primary keys and separate non-sequential public references where identifiers cross trust boundaries. | Keeps joins and indexing beginner-friendly without exposing predictable business references. |
| BASE-08 | Store monetary amounts as `DECIMAL(15,2)` plus ISO 4217 currency codes; keep each order in one settlement currency for the MVP. | Ensures deterministic arithmetic and avoids an incomplete multi-currency ledger. |
| BASE-09 | Treat Redis, OpenSearch, and analytics projections as rebuildable derived state. | Keeps recovery order and operational ownership unambiguous. |
| BASE-10 | Deploy production workloads as containers across private application/data subnets; expose only the edge load balancer/CDN. | Establishes least-privilege network boundaries and keeps MySQL non-public. |

## Approval record

**Approved on 2026-07-11.** The accountable project owner accepted the full design baseline, unblocking implementation:

- All baseline decisions **BASE-01 … BASE-10** — accepted.
- **DB-08 / ADR-005** two-short-transaction checkout (no MySQL locks held across payment authorization) — accepted.
- Business roles and permission boundaries (the [RBAC matrix](security/rbac-matrix.md)) — accepted.
- The parent-order/seller-order split, reservation/compensation behavior, table ownership, sprint sequence, and beta criteria — accepted.

Open items deferred as tracked follow-ups (not blockers): the module-level decisions in [auth §13](modules/authentication-rbac.md) and [rbac §12](security/rbac-matrix.md) — e.g. Argon2id parameters, refund/payout thresholds, and MFA step-up scope — plus the schema-level items in [mysql-design §17](database/mysql-design.md).

With approval granted, the **Authentication & RBAC module has been implemented** (see the repository root [README](../README.md) and [AI Documentation Notes.md](../AI%20Documentation%20Notes.md)). The next modules follow the roadmap sequence: seller/store onboarding, then catalog.
