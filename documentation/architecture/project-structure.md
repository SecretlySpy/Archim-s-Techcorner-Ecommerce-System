# Project Structure

**Deliverable:** 3 — Project Structure  
**Status:** Proposed; approval is required before application scaffolding begins  
**Architecture:** Frontend monorepo plus a Java modular monolith and independently deployable Python services

## 1. Purpose

This document defines the target repository layout for Archim's TechCorner. The layout keeps the first release understandable for a small team while preserving clear seams for future extraction. A directory appearing here establishes ownership and dependency direction; it does not require every placeholder to be created on day one.

The structure follows these rules:

- Java owns every write to transactional marketplace data.
- MySQL is the system of record; Redis, OpenSearch, and analytics stores are derived or temporary.
- The Java backend starts as one deployable modular monolith, not a collection of marketplace microservices.
- Each Next.js application can be deployed independently, while reusable presentation and API code lives in workspace packages.
- Python services communicate through versioned HTTP or event contracts and have no write access to transactional tables.
- Database migrations have one canonical location and run before an application version is promoted.
- Infrastructure, contracts, and documentation are reviewed like application code.

## 2. Repository at a glance

```text
archims-techcorner/
├── .github/
│   ├── CODEOWNERS
│   ├── dependabot.yml
│   └── workflows/
├── apps/
│   ├── customer-web/
│   ├── seller-center/
│   └── admin-dashboard/
├── packages/
│   ├── ui-components/
│   ├── api-client/
│   ├── shared-types/
│   ├── eslint-config/
│   └── typescript-config/
├── services/
│   ├── marketplace-api/
│   ├── recommendation-api/
│   ├── fraud-detection/
│   ├── analytics-worker/
│   └── image-processor/
├── contracts/
│   ├── openapi/
│   ├── asyncapi/
│   └── json-schema/
├── database/
│   ├── migrations/
│   ├── seed-data/
│   ├── local-development/
│   └── backup-scripts/
├── infrastructure/
│   ├── docker/
│   ├── mysql/
│   ├── redis/
│   ├── opensearch/
│   ├── rabbitmq/
│   ├── object-storage/
│   ├── monitoring/
│   └── deployment/
├── tests/
│   ├── system-e2e/
│   ├── performance/
│   ├── security/
│   ├── recovery/
│   ├── deployment/
│   └── fixtures/
├── documentation/
│   ├── architecture/
│   ├── api/
│   ├── database/
│   ├── security/
│   ├── business-rules/
│   ├── workflows/
│   ├── testing/
│   ├── operations/
│   ├── roadmap/
│   └── decisions/
├── scripts/
├── .editorconfig
├── .env.example
├── .gitignore
├── compose.yaml
├── package.json
├── pnpm-lock.yaml
├── pnpm-workspace.yaml
└── README.md
```

### Root ownership and deployment units

| Path | Responsibility | Deployable? |
|---|---|---:|
| `apps/` | Browser-facing Next.js applications | Each application independently |
| `packages/` | TypeScript-only workspace libraries and shared tooling | No |
| `services/marketplace-api/` | Critical marketplace use cases and transactional writes | One Java application |
| Other `services/` entries | Isolated Python APIs or workers | Each service independently |
| `contracts/` | Language-neutral synchronous and asynchronous interface definitions | No |
| `database/` | Flyway history, controlled seed data, local database setup, and recovery tooling | Migrations are a release artifact |
| `infrastructure/` | Local runtime, service configuration, monitoring, and environment deployment definitions | Environment-specific |
| `tests/` | Cross-application black-box, load, security, recovery, and release-rehearsal suites | No; executed against deployed environments |
| `documentation/` | Architecture, decisions, runbooks, and product rules | No |

`pnpm` workspaces are the default for the TypeScript monorepo. Maven is the default Java build tool. Each Python service owns a `pyproject.toml` and lock file so it can be built and released without coupling its dependency lifecycle to another service.

## 3. Frontend monorepo

All three applications use the same internal shape. Route files stay thin: they assemble a screen and call feature-level components; they do not contain marketplace business rules.

```text
apps/customer-web/
├── public/
├── src/
│   ├── app/
│   │   ├── (public)/
│   │   ├── (authenticated)/
│   │   ├── api/
│   │   ├── error.tsx
│   │   ├── layout.tsx
│   │   └── not-found.tsx
│   ├── features/
│   │   ├── authentication/
│   │   ├── catalog/
│   │   ├── cart/
│   │   ├── checkout/
│   │   └── orders/
│   ├── components/
│   ├── hooks/
│   ├── lib/
│   ├── providers/
│   ├── styles/
│   └── test/
├── tests/
│   ├── integration/
│   └── e2e/
├── middleware.ts
├── next.config.ts
├── package.json
├── tailwind.config.ts
└── tsconfig.json
```

Apply the same skeleton to `seller-center` and `admin-dashboard`, but give each application only its own features:

- `customer-web`: discovery, product detail, cart, checkout, customer orders, reviews, and account management.
- `seller-center`: onboarding, store, catalog, inventory, seller orders, promotions, and financial summaries.
- `admin-dashboard`: seller approval, moderation, taxonomy, order support, finance controls, content, and audit views.

### Frontend feature shape

Use subdirectories only when a feature needs them; empty ceremony is discouraged.

```text
src/features/checkout/
├── components/
├── hooks/
├── schemas/
├── server/
├── queries/
├── mutations/
├── types.ts
└── index.ts
```

The folders have distinct jobs:

- `components/` contains feature-specific UI; generic primitives belong in `ui-components`.
- `schemas/` contains Zod schemas for form and boundary validation.
- `server/` contains server-only orchestration and must never be imported by client components.
- `queries/` and `mutations/` wrap the generated API client with TanStack Query keys and cache policy.
- `index.ts` is the feature's public interface. Other features must not deep-import its internals.

### Shared TypeScript packages

| Package | May contain | Must not contain |
|---|---|---|
| `ui-components` | Accessible primitives, tokens, shared layouts, Storybook stories | API calls, authentication state, marketplace workflows |
| `api-client` | OpenAPI-generated request/response types, transport, auth/error interceptors | Hand-maintained copies of backend DTOs, page state |
| `shared-types` | UI-only value types and stable cross-application presentation contracts | JPA/domain models, secrets, server-only code |
| `eslint-config` | Shared lint and import-boundary rules | Application logic |
| `typescript-config` | Strict base TypeScript configurations | Runtime dependencies |

The application dependency direction is:

```text
apps ──> api-client ──> generated OpenAPI types
  └────> ui-components
  └────> shared-types

ui-components ──> shared-types (only when needed)
```

Workspace packages never import from `apps/`. One application never imports from another application. Browser code receives only the permissions and data it needs; frontend route guards improve user experience but never replace Java authorization checks.

## 4. Java modular-monolith structure

The marketplace backend is one Spring Boot service, one build, and one runtime process for the MVP. It is organized by business feature rather than by a repository-wide `controller/service/repository` split.

```text
services/marketplace-api/
├── src/
│   ├── main/
│   │   ├── java/com/archimstechcorner/marketplace/
│   │   │   ├── MarketplaceApplication.java
│   │   │   ├── bootstrap/
│   │   │   ├── shared/
│   │   │   │   ├── error/
│   │   │   │   ├── events/
│   │   │   │   ├── money/
│   │   │   │   ├── security/
│   │   │   │   └── time/
│   │   │   └── features/
│   │   │       ├── authentication/
│   │   │       ├── users/
│   │   │       ├── customers/
│   │   │       ├── sellers/
│   │   │       ├── sellerstaff/
│   │   │       ├── stores/
│   │   │       ├── catalog/
│   │   │       ├── categories/
│   │   │       ├── brands/
│   │   │       ├── products/
│   │   │       ├── productvariants/
│   │   │       ├── productspecifications/
│   │   │       ├── pricing/
│   │   │       ├── inventory/
│   │   │       ├── warehouses/
│   │   │       ├── cart/
│   │   │       ├── checkout/
│   │   │       ├── orders/
│   │   │       ├── sellerorders/
│   │   │       ├── payments/
│   │   │       ├── shipping/
│   │   │       ├── returns/
│   │   │       ├── refunds/
│   │   │       ├── reviews/
│   │   │       ├── productquestions/
│   │   │       ├── promotions/
│   │   │       ├── vouchers/
│   │   │       ├── notifications/
│   │   │       ├── sellerbalances/
│   │   │       ├── sellerpayouts/
│   │   │       └── auditlogs/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/com/archimstechcorner/marketplace/
│       │   ├── architecture/
│       │   ├── features/
│       │   └── support/
│       └── resources/
├── pom.xml
└── README.md
```

Each material feature uses the following internal layers:

```text
features/inventory/
├── api/
│   ├── request/
│   └── response/
├── application/
│   ├── command/
│   ├── query/
│   └── port/
├── domain/
│   ├── event/
│   ├── model/
│   ├── policy/
│   └── repository/
└── infrastructure/
    ├── messaging/
    ├── persistence/
    └── web/
```

- `api` defines the feature's supported Java-facing facade and DTOs.
- `application` coordinates use cases and transaction boundaries.
- `domain` contains invariants and business concepts without web or persistence concerns.
- `infrastructure` adapts JPA, RabbitMQ, external providers, and HTTP delivery to application ports.

Small reference-data features may start with fewer folders, but their dependencies must still point inward. JPA entities and Spring Data repositories remain private to the owning feature. REST responses use DTOs; entities are never serialized directly.

### Java dependency boundaries

1. A feature may use another feature only through that feature's `api` facade or a published domain event.
2. A feature must not import another feature's `domain` or `infrastructure` packages, tables through a repository, or JPA entities.
3. `shared` is a deliberately small kernel for truly universal technical concepts. It must not become a home for feature logic.
4. Controllers call application use cases. They do not call repositories or external providers directly.
5. Domain code does not depend on Spring MVC, Spring Data, RabbitMQ, or provider SDKs.
6. Cross-feature workflows use an application orchestrator. Checkout, for example, coordinates published pricing, inventory, order, and payment interfaces without taking ownership of their data.
7. In-process events handle same-transaction collaboration; durable outbox events handle RabbitMQ publication after commit.
8. Package-boundary tests (for example, ArchUnit rules) run in CI and fail forbidden imports.

The intended high-level dependency flow is:

```text
authentication ──> users
customers ───────> users
sellers ─────────> users
sellerstaff ─────> sellers
stores ──────────> sellers

products ────────> categories, brands, stores
productvariants ─> products
pricing ─────────> productvariants
inventory ───────> productvariants, warehouses

cart ────────────> productvariants, pricing
checkout ────────> cart, pricing, inventory, orders, payments
sellerorders ────> orders, stores
shipping ────────> sellerorders
returns ─────────> sellerorders, shipping
refunds ─────────> payments, orders; returns only when a refund resolves a return
sellerbalances ──> sellerorders, refunds
sellerpayouts ───> sellerbalances
```

These arrows mean “uses a public interface,” not shared ownership. Cycles are not allowed. When a cycle appears, move orchestration up to an application workflow or use an event instead of weakening module boundaries.

## 5. Python services

Python is split by runtime and operational purpose. The services share contract conventions, not database models or an importable mega-library.

```text
services/recommendation-api/
├── src/recommendation_api/
│   ├── api/
│   ├── application/
│   ├── domain/
│   ├── infrastructure/
│   ├── contracts/
│   └── observability/
├── tests/
│   ├── unit/
│   ├── integration/
│   └── contract/
├── pyproject.toml
├── uv.lock
└── README.md

services/analytics-worker/
├── src/analytics_worker/
│   ├── tasks/
│   ├── application/
│   ├── data/
│   ├── contracts/
│   └── observability/
├── tests/
├── pyproject.toml
├── uv.lock
└── README.md
```

Use the API shape for synchronous FastAPI services (`recommendation-api` and, when applicable, `fraud-detection`) and the worker shape for Celery services (`analytics-worker` and `image-processor`). `fraud-detection` may host both an API adapter and a Celery task adapter, but they share one application layer.

Python boundaries are non-negotiable:

- HTTP calls use the versioned OpenAPI contract; events use AsyncAPI and JSON Schema definitions from `contracts/`.
- SQLAlchemy connections use a read-only MySQL account and read only approved analytics views/tables.
- No Python service writes orders, totals, payments, refunds, inventory reservations, seller balances, or payouts.
- Recommendations, risk scores, image metadata, and cleanup proposals return through Java APIs or events for validation before transactional changes.
- Every event consumer is idempotent, records processing outcomes, and routes poison messages to a dead-letter queue.
- Service-specific models stay inside that service. Copying a small stable value object is preferable to creating a tightly coupled shared Python package.

## 6. Contracts

```text
contracts/
├── openapi/
│   ├── marketplace-public-v1.yaml
│   ├── seller-v1.yaml
│   ├── administration-v1.yaml
│   ├── recommendation-internal-v1.yaml
│   └── fraud-internal-v1.yaml
├── asyncapi/
│   └── marketplace-events-v1.yaml
└── json-schema/
    └── events/
        ├── product-approved-v1.json
        ├── inventory-changed-v1.json
        └── order-placed-v1.json
```

The Java service is the behavioral owner of marketplace contracts. Checked-in definitions make changes reviewable and allow consumers to test without importing Java classes. Generated artifacts go to build directories and are not edited manually. Breaking changes require a new major contract version and a documented migration window; additive fields remain optional until all consumers are upgraded.

Event names use past tense (`product.approved.v1`), include an event ID, type, version, occurrence time, correlation ID, producer, and payload. Events carry only the data consumers need and never include secrets or unnecessary personal data.

## 7. Database structure and migrations

```text
database/
├── migrations/
│   ├── versioned/
│   │   ├── identity/
│   │   ├── seller/
│   │   ├── catalog/
│   │   ├── commerce/
│   │   └── operations/
│   └── repeatable/
├── seed-data/
│   ├── reference/
│   ├── local/
│   └── test/
├── local-development/
│   ├── users/
│   └── initialization/
└── backup-scripts/
    ├── backup/
    ├── restore/
    └── verification/
```

`database/migrations/` is the sole source of Flyway migrations. The Maven build includes this directory in the Java release artifact; it must not maintain a second copy under service resources.

Migration policy:

- Versioned files use `V<major>_<minor>_<sequence>__<imperative_description>.sql`, for example `V1_2_003__add_inventory_reservation_expiry.sql`.
- Repeatable files use `R__<description>.sql` only for recreatable views or routines, not mutable reference data.
- Released migrations are immutable. A new forward migration corrects a defect.
- One migration has one owning Java feature and a focused purpose; unrelated schema changes are separate.
- Destructive or long-running changes use an expand/migrate/contract rollout with a documented rollback and data verification plan.
- Production seed data contains controlled reference values only. Demo users, orders, and credentials are confined to local/test data.
- Migration, application, analytics, and backup database users are separate and least-privileged.
- CI applies the complete migration chain to an empty MySQL 8 database and upgrades a sanitized prior-version fixture.

Table and column names are plural `snake_case`; primary keys end in `_id`; foreign-key columns match the referenced key name. Constraint and index names follow `pk_<table>`, `fk_<child>__<parent>`, `uk_<table>__<columns>`, and `idx_<table>__<columns>`. Money uses `DECIMAL(15,2)` plus an ISO currency code and never uses floating-point types.

## 8. Infrastructure structure

```text
infrastructure/
├── docker/
│   ├── marketplace-api/
│   ├── web/
│   └── python/
├── mysql/
│   ├── config/
│   └── healthchecks/
├── redis/
│   └── config/
├── opensearch/
│   ├── config/
│   └── index-templates/
├── rabbitmq/
│   ├── definitions/
│   └── policies/
├── object-storage/
│   └── policies/
├── monitoring/
│   ├── prometheus/
│   ├── grafana/
│   │   ├── dashboards/
│   │   └── provisioning/
│   └── alerts/
└── deployment/
    ├── environments/
    │   ├── development/
    │   ├── staging/
    │   └── production/
    └── modules/
```

`compose.yaml` at the repository root provides a consistent local environment and refers to configuration under `infrastructure/`. Deployment definitions separate reusable modules from environment values. Secrets are injected by the target platform: only redacted examples and secret names belong in Git. Production MySQL, Redis, RabbitMQ, OpenSearch, and object storage are not exposed directly to the public internet.

Each deployable component has a multi-stage container build, a non-root runtime user, a health check, pinned base-image version, resource limits, and an independent image tag. Monitoring definitions use stable service-level metric names rather than environment-specific hostnames.

## 9. Documentation structure

| Directory | Contents |
|---|---|
| `architecture/` | Context, component, data-flow, deployment, security-boundary, and project-structure views |
| `api/` | API conventions, endpoint guides, event catalog, idempotency rules |
| `database/` | ERD, table catalog, ownership map, migration and locking strategy |
| `security/` | Threat model, RBAC matrix, secure-development checklist, incident guidance |
| `business-rules/` | Pricing, inventory, multi-seller orders, payments, shipping, returns, payouts |
| `workflows/` | Customer, seller, administrator, finance, and support journeys |
| `testing/` | Test strategy, environments, fixtures, performance model, release evidence |
| `operations/` | Setup, deployment, backup/restore, monitoring, on-call, and recovery runbooks |
| `roadmap/` | MVP plan, release scope, milestones, and beta exit criteria |
| `decisions/` | Numbered Architecture Decision Records (ADRs) |

Documentation names use lowercase kebab-case. ADRs use `NNNN-short-decision-title.md`, are never renumbered, and record context, decision, consequences, and status. Diagrams should have an editable text source (for example, Mermaid) so they can be reviewed in pull requests.

## 10. Naming conventions

| Area | Convention | Example |
|---|---|---|
| Repository directories | lowercase kebab-case | `seller-center` |
| TypeScript components/types | PascalCase | `OrderSummary`, `CheckoutResult` |
| TypeScript functions/hooks/files | camelCase; hooks start with `use` | `useCheckout.ts` |
| Next.js route segments | lowercase kebab-case | `order-history` |
| Java packages | lowercase, no separators | `sellerorders` |
| Java classes | PascalCase with purpose suffix | `CreateOrderCommand`, `OrderController` |
| Java methods/fields | camelCase | `reserveInventory` |
| Python packages/modules/functions | snake_case | `image_processor`, `resize_image` |
| Python classes | PascalCase | `FraudAssessment` |
| Database identifiers | plural snake_case | `inventory_reservations` |
| REST resources | plural lowercase kebab-case | `/api/v1/seller-orders` |
| RabbitMQ routing keys | lowercase dot-separated, past tense, versioned | `order.placed.v1` |
| Environment variables | uppercase snake case with component prefix | `MARKETPLACE_DB_URL` |

Names describe the business concept, not the framework. Avoid generic production names such as `Utils`, `Helper`, `Manager`, `Common`, and `Data` unless the narrower responsibility is obvious from context.

## 11. Boundary enforcement in CI

A pull request is mergeable only when applicable checks pass:

- TypeScript strict compilation, lint rules, workspace import-boundary checks, unit tests, and build for affected applications.
- Java compilation, unit/integration tests, module/ArchUnit checks, OpenAPI compatibility checks, and Testcontainers tests.
- Python type checking, linting, unit/contract tests, and a check proving transactional database credentials are not configured.
- Flyway clean-install and prior-version upgrade tests against MySQL 8.
- Contract linting and backward-compatibility checks for OpenAPI, AsyncAPI, and event schemas.
- Container and dependency vulnerability scans, secret scanning, and software-bill-of-materials generation.
- Documentation link checks and ADR presence for architectural changes.

## 12. System-level test ownership

Application-local tests prove a component in isolation; `tests/` owns behavior that crosses deployables or validates an environment as a whole.

| Path | Responsibility |
|---|---|
| `tests/system-e2e/` | Playwright customer, seller, administrator, support, and finance journeys across the deployed web applications and Java API |
| `tests/performance/` | Versioned workload profiles for homepage, search, cart, checkout, provider callbacks, and sustained/soak testing |
| `tests/security/` | Cross-role/tenant authorization matrix, DAST orchestration, callback replay, upload-abuse, and privacy regression suites |
| `tests/recovery/` | Backup/PITR restore verification, financial and inventory reconciliation, OpenSearch rebuild, and queue recovery exercises |
| `tests/deployment/` | Migration, smoke, rollback/roll-forward, failover, and environment-configuration rehearsals |
| `tests/fixtures/` | Synthetic, deterministic, privacy-safe cross-service datasets and provider simulators |

These suites interact through public or explicitly internal contracts; they do not import JPA entities, feature internals, or production secrets. QA owns the harness and release evidence, while the affected capability owner owns failing behavior. Environment-changing recovery and deployment tests run only in isolated, explicitly targeted environments.

## 13. Adding a new feature safely

Before creating a new folder or service, the feature owner records:

1. The business capability and owning application or Java feature.
2. The data it owns and whether it needs a migration.
3. Its public HTTP/event contracts and authorization rules.
4. Its allowed upstream dependencies and downstream consumers.
5. Its unit, integration, contract, security, and operational test needs.
6. Metrics, logs, alerting, privacy classification, and recovery behavior.

A new deployable service requires an approved ADR. A feature remains inside the Java modular monolith unless independent scaling, isolation, ownership, or release cadence provides measurable value that outweighs the distributed-system cost.
