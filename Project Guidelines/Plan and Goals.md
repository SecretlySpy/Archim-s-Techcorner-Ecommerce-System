# Plan and Goals

**Reconciled 2026-10-04 from live audit (PHASE 1).** Replaces starter placeholders with inspected facts. Follows master plan + AGENTS delivery protocol. Older roadmap preserved for context; active work uses this + strict dep order.

## Outcome and scope

- **Users and problem:** Multi-vendor tech marketplace. Customers discover/compare/buy from multiple sellers; sellers onboard/manage catalog/inventory/fulfill; admins moderate.
- **Measurable success:** Per master plan milestones (FOUNDATION: reconciled state+trace; MVP CORE: discover->checkout->paid multi-seller order; etc.). Beta criteria in documentation/roadmap/mvp-roadmap.md.
- **In scope (current):** Preserve Next14/React18/TS/Java21/SB3/MySQL/Flyway/Redis/pnpm. Extend existing (auth/seller/catalog/inv/cart) per PHASE 2-6. Shared pkgs first.
- **Out of scope:** Microservices, major version upgrades w/o ADR+approval, search as source of truth.
- **Constraints:** See master prompt (security/tx invariants win; Figma visuals unless infeasible; additive Flyway only; no MySQL locks across providers; deny-by-default in Java).
- **Assumptions:** Figma private (use inferred + code parity); existing modules reusable/extensible.

## Current reconciled state (from audit)

| Component | Status | Key paths |
|----|----|----|
| Auth/RBAC | Implemented | services/.../authentication/* , users/* ; migrations identity/* ; apps/customer-web auth pages + api |
| Seller Onboarding/Store | Implemented | sellers/* ; /api/v1/sellers/onboard , /me ; customer-web /seller/onboard + dashboard |
| Catalog (basic) | Partial | catalog/* ; feed + seller CRUD + moderation ; /products/[id] , seller products |
| Inventory | Implemented | inventory/* ; low-stock + adjust |
| Cart/Wishlist | Implemented | cart/* ; /cart , /wishlist |
| Search basic | Partial | q + category filter in feed (no facets/autocomplete) |
| Checkout/Orders/Pay/etc | Missing | Cart stub only; no backend |
| Packages (shared) | Missing | packages/ empty |
| Apps (seller/admin) | Missing | Only customer-web |

See updated README.md for full table. Migrations stop at cart. Tests minimal.

## Requirements and milestones

Follow master plan STRICT DEPENDENCY ORDER (T01-T32). Use master PHASES/MILESTONES (FOUNDATION, MVP CORE, POLISH, BETA, RELEASE).

| ID | Functional or nonfunctional requirement | Acceptance and failure check | Owner | Status | Evidence |
|----|----|----|----|----|----|
| FR-01 (auth) | ... | ... | ... | Implemented | AuthIntegrationTest + code |
| ... (see master plan) | ... | ... | ... | per classification | audit 2026-10-04 |

## Delivery plan

1. Complete T01-T03 (audit, reconcile, matrix) — current.
2. Then T04+ per order: shared tokens -> ui -> media/spec -> search/filter -> ... 
3. For every task: inspect, reuse/extend, smallest slice, add tests, run, fix, update docs, record evidence.
4. Stop only on protected actions; escalate material arch/product.

## Open questions and decisions

| Question or ADR | Options and trade-offs | Decision owner | Trigger/date | State |
|----|----|----|----|----|
| Figma access | Private; infer vs wait | N/A (tool 403) | 2026-10-04 | Use code+docs for now |
| Packages structure timing | Establish early vs lazy | per plan: first when delivering dependents | 2026-10 | In progress T04 |
| (add as discovered) | ... | ... | ... | ... |
