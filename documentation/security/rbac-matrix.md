# RBAC Matrix — Roles, Permissions, and Authorization Rules

| Document property | Value |
|---|---|
| Deliverable | Supporting artifact for Deliverable 5 (Authentication & RBAC) |
| Status | ✅ Approved 2026-07-11; the identity/access + anchor set is seeded in `database/migrations/repeatable/R__seed_roles_permissions.sql`. Full ~150-permission matrix seeds incrementally as modules land. |
| Owner | Security + platform engineering |
| Consumes | [Auth & RBAC module design](../modules/authentication-rbac.md), [System architecture §9–10](../architecture/system-architecture.md), [MySQL design §6.1](../database/mysql-design.md) |
| Enforced by | Spring Security filter chain + per-endpoint permission checks + application-service ownership/state checks |

> This document is the authoritative source for the role→permission mapping referenced by the auth module (§3.1). It is **design data**, not code. On approval it is transcribed into a repeatable Flyway seed and into the authorization test suite (Gate B, roadmap §7). Because the workflow classifier was unavailable at authoring time, the adversarial verification (isolation, SoD, step-up, completeness) was applied inline; §12 lists the residual open decisions.

---

## 1. Purpose and scope

Define, exhaustively and unambiguously, **which role may perform which action, under what scope, and with what elevation** across every Java feature module. The matrix implements the architecture's non-negotiables:

- **Deny by default.** A capability absent from a role's grant set is denied. There is no implicit inheritance except the documented delegation of SELLER capabilities to SELLER_STAFF.
- **Java is the only writer.** Every mutating permission (`writer = java`) is executed by a Java application service inside a transaction with an audit record. UI route guards are UX only.
- **Least privilege + separation of duties.** SUPPORT never moves money; FINANCE never moderates catalog or owns support cases; CONTENT never touches orders, money, or PII.
- **Tenant isolation.** SELLER / SELLER_STAFF act only within their own account, stores, products, orders, and balances — never `scope = any`.

## 2. Roles

| Code | Persona | Stored where | Default MFA |
|---|---|---|---|
| `GUEST` | Unauthenticated visitor | No row (absence of session) | n/a |
| `CUSTOMER` | Registered buyer | `roles.code = CUSTOMER` via `user_roles` | Optional |
| `SELLER` | Owner of a seller account and its store(s) | `roles.code = SELLER` | Optional (recommended) |
| `SELLER_STAFF` | Employee acting within one seller's store(s) | `seller_staff` + `seller_staff_permissions` (store-scoped) | Optional |
| `ADMIN` | Marketplace administrator | `roles.code = ADMIN` | **Mandatory** |
| `SUPPORT` | Support agent | `roles.code = SUPPORT` | **Mandatory** |
| `FINANCE` | Finance administrator | `roles.code = FINANCE` | **Mandatory** |
| `CONTENT` | Content manager | `roles.code = CONTENT` | **Mandatory** |

`GUEST` and `SELLER_STAFF` are not rows in `roles`: `GUEST` is the unauthenticated state; `SELLER_STAFF` is a delegated subset of `SELLER` capabilities scoped to a store via `seller_staff_permissions`. All four staff/admin roles (`ADMIN/SUPPORT/FINANCE/CONTENT`) require an active MFA method before a privileged session is issued (auth module FR-9).

## 3. Enforcement model

1. **Authentication** establishes the principal (Spring Security, cookie session).
2. **Coarse gate:** the endpoint declares a required permission code; the filter denies if the principal's effective permission set lacks it.
3. **Scope check:** the application service evaluates `scope` against the target resource — `own` (principal owns it), `store` (principal's `seller_staff`/`seller_account` covers the store), `any` (platform-wide), `public` (no auth).
4. **State check:** transitions are validated against the aggregate's current status (e.g. `order.cancel.own` only in cancellable states).
5. **Elevation:** `stepUp = true` requires a fresh MFA assertion within the action window; `dualApproval = true` requires a second authorized approver recorded before the effect commits.
6. **Audit:** every `writer = java` action appends an `audit_logs` row in the same transaction.

### 3.1 Cell legend (matrix tables in §5)

| Symbol | Meaning |
|---|---|
| `A` | Allow — granted to the role by default |
| `—` | Deny |
| `C` | Conditional — allowed only when the row's **Conditions** note holds (ownership, state, threshold, case assignment) |
| `D` | Delegable — a SELLER capability the seller **may** grant to staff via `seller_staff_permissions`; **off by default** (Staff column only) |

Elevation columns: `Step` = step-up MFA required; `Dual` = dual approval required (`✔` = yes, blank = no).

### 3.2 Scope vocabulary

`public` (anyone) · `own` (the principal's own user-owned resource) · `store` (a store the principal's seller account/staff membership covers) · `any` (all tenants; admin/support/finance/content only).

---

## 4. Domain index

The catalog in §5 is grouped into ten domains, one per subsection, each mapping to the Java feature modules that own the write:

| § | Domain | Owning modules |
|---|---|---|
| 5.1 | Identity & Access | `authentication`, `users`, `customers` |
| 5.2 | Seller & Store | `sellers`, `sellerstaff`, `stores`, `warehouses` |
| 5.3 | Catalog | `categories`, `brands`, `products`, `productvariants`, `productspecifications` |
| 5.4 | Pricing & Promotions | `pricing`, `promotions`, `vouchers` |
| 5.5 | Inventory | `inventory`, `warehouses` |
| 5.6 | Cart, Checkout & Orders | `cart`, `checkout`, `orders`, `sellerorders` |
| 5.7 | Payments, Refunds, Balances & Payouts | `payments`, `refunds`, `sellerbalances`, `sellerpayouts` |
| 5.8 | Shipping, Returns & Disputes | `shipping`, `returns` (+ disputes) |
| 5.9 | Reviews, Questions & Notifications | `reviews`, `productquestions`, `notifications` |
| 5.10 | Platform, Audit & Content | `auditlogs`, content, config, reporting |

---

## 5. Permission catalog and matrix

Column order: **Guest · Cust · Sell · Staff · Admin · Supp · Fin · Cont · Step · Dual**.

### 5.1 Identity & Access (`authentication`, `users`, `customers`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `auth.register.customer` | public | A | — | — | — | — | — | — | — | | |
| `auth.register.seller` | own | A | A | — | — | — | — | — | — | | |
| `auth.login` / `auth.logout` / `auth.token.refresh` | own | A¹ | A | A | A | A | A | A | A | | |
| `auth.email.verify` / `auth.email.resend` | own | A | A | A | A | A | A | A | A | | |
| `auth.password.forgot` / `auth.password.reset` | own | A | A | A | A | A | A | A | A | | |
| `auth.password.change` | own | — | A | A | A | A | A | A | A | ✔ | |
| `auth.mfa.enroll` / `auth.mfa.activate` | own | — | A | A | A | A | A | A | A | | |
| `auth.mfa.recovery.regenerate` | own | — | A | A | A | A | A | A | A | ✔ | |
| `auth.mfa.remove` | own | — | C² | C² | C² | C² | C² | C² | C² | ✔ | |
| `sessions.read.own` / `sessions.revoke.own` | own | — | A | A | A | A | A | A | A | | |
| `profile.read.own` / `profile.update.own` | own | — | A | A | A | A | A | A | A | | |
| `addresses.read.own` / `addresses.manage.own` | own | — | A | A | A | A | A | A | A | | |
| `login_history.read.own` | own | — | A | A | A | A | A | A | A | | |
| `users.read.any` | any | — | — | — | — | A | C³ | — | — | | |
| `users.search` | any | — | — | — | — | A | A | — | — | | |
| `users.pii.read` | any | — | — | — | — | C⁴ | C³ | C⁵ | — | | |
| `users.pii.export` | any | — | — | — | — | A | — | C⁵ | — | ✔ | |
| `users.role.assign` / `users.role.revoke` | any | — | — | — | — | A | — | — | — | ✔ | |
| `users.lock` / `users.unlock` | any | — | — | — | — | A | — | — | — | ✔ | |
| `users.suspend` / `users.reinstate` | any | — | — | — | — | A | — | — | — | ✔ | |
| `users.impersonate` (login-as) | any | — | — | — | — | C⁶ | — | — | — | ✔ | ✔ |
| `users.erasure.request.own` | own | — | A | A | A | A | A | A | A | | |
| `users.erasure.execute` | any | — | — | — | — | A | — | — | — | ✔ | ✔ |
| `roles.read` / `permissions.read` | any | — | — | — | — | A | — | — | — | | |
| `login_history.read.any` | any | — | — | — | — | A | C³ | — | — | | |

Conditions: ¹Guest performs login/refresh only as the anonymous→authenticated transition. ²Blocked if it would leave a mandatory-MFA role with no active method (`MFA_REQUIRED_FOR_ROLE`). ³Only for a user tied to an assigned support case; audited. ⁴Audited; justification recorded. ⁵Only PII within a financial/tax/payout context. ⁶Time-limited, audited, read-mostly; high-risk actions still blocked during impersonation.

### 5.2 Seller & Store (`sellers`, `sellerstaff`, `stores`, `warehouses`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `seller.onboarding.submit` | own | — | A | A | — | — | — | — | — | | |
| `seller.document.upload` | own | — | — | A | — | — | — | — | — | | |
| `seller.application.read.own` | own | — | — | A | — | — | — | — | — | | |
| `seller.application.read.any` | any | — | — | — | — | A | C³ | — | — | | |
| `seller.approve` / `seller.reject` | any | — | — | — | — | A | — | — | — | ✔ | |
| `seller.suspend` / `seller.reinstate` | any | — | — | — | — | A | — | — | — | ✔ | |
| `seller.risk.set` | any | — | — | — | — | A | — | C⁷ | — | ✔ | |
| `seller.appeal.submit` | own | — | — | A | — | — | — | — | — | | |
| `seller.appeal.review` | any | — | — | — | — | A | — | — | — | | |
| `sellerstaff.read.store` | store | — | — | A | D | A | C³ | — | — | | |
| `sellerstaff.invite` / `sellerstaff.remove` | store | — | — | A | D | — | — | — | — | | |
| `sellerstaff.permission.grant` | store | — | — | A | — | — | — | — | — | | |
| `store.create` | own | — | — | A | — | — | — | — | — | | |
| `store.read.public` | public | A | A | A | A | A | A | A | A | | |
| `store.update` | store | — | — | A | D | — | — | — | — | | |
| `store.media.manage` | store | — | — | A | D | — | — | — | — | | |
| `store.moderate` | any | — | — | — | — | A | — | — | — | ✔ | |
| `store.follow` / `store.unfollow` | own | — | A | A | A | A | A | A | A | | |
| `store.followers.read.store` | store | — | — | A | D | A | — | — | — | | |
| `warehouse.create` / `warehouse.update` / `warehouse.read.store` | store | — | — | A | D | A⁸ | — | — | — | | |

Conditions: ⁷Only payout/settlement-risk dimension. ⁸Admin read-only for support/ops.

### 5.3 Catalog (`categories`, `brands`, `products`, `productvariants`, `productspecifications`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `category.read.public` / `brand.read.public` | public | A | A | A | A | A | A | A | A | | |
| `category.manage` (tree/create/update/reorder) | any | — | — | — | — | A | — | — | — | | |
| `category.display.manage` (page copy/featuring) | any | — | — | — | — | C | — | — | A | | |
| `brand.create` / `brand.update` / `brand.approve` | any | — | — | — | — | A | — | — | — | | |
| `brand.suggest` | own | — | — | A | D | — | — | — | — | | |
| `product.read.public` | public | A | A | A | A | A | A | A | A | | |
| `product.read.store` (incl. drafts) | store | — | — | A | D | A | C³ | — | — | | |
| `product.create` / `product.update` / `product.draft` / `product.duplicate` / `product.archive` | store | — | — | A | D | — | — | — | — | | |
| `product.bulk_upload` | store | — | — | A | D | — | — | — | — | | |
| `product.submit_for_approval` | store | — | — | A | D | — | — | — | — | | |
| `product.moderate` (approve/reject) | any | — | — | — | — | A | — | — | — | | |
| `product.remove` (takedown) | any | — | — | — | — | A | — | — | — | ✔ | |
| `product.prohibited.review` / `product.counterfeit.review` / `product.duplicate.review` | any | — | — | — | — | A | — | — | — | | |
| `product.quality_score.read` | store/any | — | — | A⁹ | D | A | — | — | — | | |
| `variant.create` / `variant.update` / `variant.archive` / `variant.sku.manage` | store | — | — | A | D | — | — | — | — | | |
| `specification.manage` | store | — | — | A | D | — | — | — | — | | |
| `product.media.manage` | store | — | — | A | D | — | — | — | — | | |

Conditions: ⁹Seller reads only own products' score.

### 5.4 Pricing & Promotions (`pricing`, `promotions`, `vouchers`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `pricing.read.public` | public | A | A | A | A | A | A | A | A | | |
| `pricing.set` / `pricing.schedule_sale` | store | — | — | A | D | — | — | — | — | | |
| `price_history.read.store` | store | — | — | A | D | A | — | — | — | | |
| `voucher.read.public` / `voucher.apply` | own | A¹⁰ | A | — | — | — | — | — | — | | |
| `voucher.seller.create` / `voucher.seller.update` / `voucher.seller.deactivate` | store | — | — | A | D | — | — | — | — | | |
| `voucher.platform.create` / `voucher.platform.update` / `voucher.platform.deactivate` | any | — | — | — | — | A | — | — | — | | |
| `voucher.usage.read.store` | store | — | — | A | D | A | C⁷ | — | — | | |
| `campaign.seller.create` / `campaign.seller.join` | store | — | — | A | D | — | — | — | — | | |
| `campaign.platform.manage` / `campaign.approve` | any | — | — | — | — | A | — | — | — | | |
| `flashsale.join` / `sponsored.create` | store | — | — | A | D | — | — | — | — | | |
| `flashsale.manage.platform` / `sponsored.manage.platform` | any | — | — | — | — | A | — | — | — | | |

Conditions: ¹⁰Guest may view public voucher terms; applying requires a customer session at checkout.

### 5.5 Inventory (`inventory`, `warehouses`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `inventory.read.store` / `inventory.lowstock.read` | store | — | — | A | D | A | C³ | — | — | | |
| `inventory.adjust` | store | — | — | A | D | C¹¹ | — | — | — | | |
| `inventory.restock.set` | store | — | — | A | D | — | — | — | — | | |
| `inventory.reservation.read.store` | store | — | — | A | D | A | C³ | — | — | | |
| `inventory.reservation.override` | any | — | — | — | — | A | — | — | — | ✔ | |
| `inventory.history.read.store` | store | — | — | A | D | A | — | — | — | | |

Conditions: ¹¹Admin adjustment only as an audited correction; normal stock edits belong to the seller.

### 5.6 Cart, Checkout & Orders (`cart`, `checkout`, `orders`, `sellerorders`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `cart.read.own` / `cart.update.own` | own | C¹² | A | A | A | A | A | A | A | | |
| `wishlist.manage.own` / `saveforlater.manage.own` | own | — | A | A | A | A | A | A | A | | |
| `checkout.submit` | own | — | C¹³ | C¹³ | C¹³ | — | — | — | — | | |
| `order.read.own` / `order.invoice.download.own` | own | — | A | A | A | A | A | A | A | | |
| `order.cancel.own` | own | — | C¹⁴ | — | — | — | — | — | — | | |
| `order.confirm_delivery.own` | own | — | C¹⁴ | — | — | — | — | — | — | | |
| `order.read.any` / `order.search` | any | — | — | — | — | A | A | C¹⁵ | — | | |
| `sellerorder.read.store` | store | — | — | A | D | A | C³ | — | — | | |
| `sellerorder.accept` / `sellerorder.reject` | store | — | — | A | D | — | — | — | — | | |
| `sellerorder.status.update` (packing/ready) | store | — | — | A | D | — | — | — | — | | |
| `sellerorder.cancel` | store | — | — | C¹⁴ | D | — | — | — | — | | |
| `order.override` (force state) | any | — | — | — | — | A | — | — | — | ✔ | |
| `order.note.internal.add` | any | — | — | — | — | A | A | — | — | | |
| `order.escalate` | any | — | — | — | — | A | A | — | — | | |

Conditions: ¹²Guest cart via signed guest token only. ¹³Requires verified email + in-stock revalidation. ¹⁴Only in an eligible state per the order state machine. ¹⁵Finance sees the financial view (totals/payment), not fulfillment detail/PII.

### 5.7 Payments, Refunds, Balances & Payouts (`payments`, `refunds`, `sellerbalances`, `sellerpayouts`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `payment.read.own` | own | — | A | — | — | — | — | — | — | | |
| `payment.retry.own` / `payment.cancel.own` | own | — | C¹⁴ | — | — | — | — | — | — | | |
| `payment.read.any` | any | — | — | — | — | C¹⁶ | C³ | A | — | | |
| `payment.reconcile` | any | — | — | — | — | — | — | A | — | | |
| `refund.request.own` | own | — | A | — | — | — | — | — | — | | |
| `refund.approve.standard` | any | — | — | — | — | C¹⁷ | — | A | — | ✔ | |
| `refund.approve.large` | any | — | — | — | — | — | — | A | — | ✔ | ✔ |
| `refund.reject` | any | — | — | — | — | C¹⁷ | — | A | — | ✔ | |
| `refund.read.any` | any | — | — | C¹⁸ | — | A | C³ | A | — | | |
| `commission.rule.manage` | any | — | — | — | — | A | A | — | — | ✔ | |
| `seller_balance.read.own` | own | — | — | A | D¹⁹ | — | — | — | — | | |
| `seller_balance.read.any` / `ledger.read.any` | any | — | — | — | — | C¹⁶ | — | A | — | | |
| `payout.request.own` | own | — | — | A | — | — | — | — | — | | |
| `payout.read.own` / `financial.statement.download.own` / `tax.summary.read.own` | own | — | — | A | D¹⁹ | — | — | — | — | | |
| `payout.approve` / `payout.reject` | any | — | — | — | — | — | — | A | — | ✔ | ✔²⁰ |
| `payout.freeze` | any | — | — | — | — | A | — | A | — | ✔ | |
| `payout.freeze.release` | any | — | — | — | — | — | — | A | — | ✔ | ✔ |
| `payout.read.any` | any | — | — | — | — | C¹⁶ | — | A | — | | |
| `financial.report.read` | any | — | — | — | — | C¹⁶ | — | A | — | | |

Conditions: ¹⁶Admin sees masked status only, not full financial control. ¹⁷Admin may action a *standard* refund with step-up; large refunds and all payouts are FINANCE-only (SoD). ¹⁸Seller sees refunds affecting own seller orders. ¹⁹Financial summaries are delegable but off by default; sensitive by policy. ²⁰Dual approval required above the payout threshold.

### 5.8 Shipping, Returns & Disputes (`shipping`, `returns`, `disputes`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `shipment.read.store` | store | — | — | A | D | A | C³ | — | — | | |
| `shipment.create` / `label.generate` / `shipment.courier.book` | store | — | — | A | D | — | — | — | — | | |
| `shipment.evidence.upload` | store | — | — | A | D | — | — | — | — | | |
| `shipment.tracking.read.own` | own | — | A | — | — | — | — | — | — | | |
| `shipment.read.any` | any | — | — | — | — | A | A | — | — | | |
| `courier.config.manage` | any | — | — | — | — | A | — | — | — | ✔ | |
| `return.request.own` / `return.evidence.upload.own` | own | — | A | — | — | — | — | — | — | | |
| `return.read.store` | store | — | — | A | D | A | C³ | — | — | | |
| `return.respond` (approve/reject/dispute) | store | — | — | A | D | — | — | — | — | | |
| `return.inspect` | store | — | — | A | D | C²¹ | — | — | — | | |
| `replacement.request.own` | own | — | A | — | — | — | — | — | — | | |
| `replacement.approve` | store | — | — | A | D | — | — | — | — | | |
| `dispute.open.own` | own | — | A | — | — | — | — | — | — | | |
| `dispute.open.store` | store | — | — | A | D | — | — | — | — | | |
| `dispute.read.any` | any | — | — | — | — | A | A | — | — | | |
| `dispute.arbitrate` | any | — | — | — | — | A | — | — | — | ✔ | |

Conditions: ²¹Admin inspection only within arbitration.

### 5.9 Reviews, Questions & Notifications (`reviews`, `productquestions`, `notifications`)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `review.read.public` / `question.read.public` | public | A | A | A | A | A | A | A | A | | |
| `review.create.own` / `review.update.own` / `review.delete.own` | own | — | C²² | C²² | C²² | — | — | — | — | | |
| `review.report` / `question.report` | own | — | A | A | A | A | A | A | A | | |
| `review.respond.store` | store | — | — | A | D | — | — | — | — | | |
| `review.moderate` / `question.moderate` | any | — | — | — | — | A | C²³ | — | — | | |
| `question.ask.own` | own | — | A | A | A | A | A | A | A | | |
| `question.answer.store` | store | — | — | A | D | — | — | — | — | | |
| `notification.read.own` / `notification.preference.manage.own` | own | — | A | A | A | A | A | A | A | | |
| `notification.template.manage` | any | — | — | — | — | A | — | — | C²⁴ | | |
| `contact.seller` / `contact.support` | own | — | A | A | A | — | — | — | — | | |

Conditions: ²²Verified-purchase reviewer only; editable within the review window. ²³Support may hide/flag pending admin review. ²⁴Content manages marketing/template copy; system owns transactional templates.

### 5.10 Platform, Audit & Content (`auditlogs`, content, config, reporting)

| Permission | Scope | G | Cu | Se | St | Ad | Su | Fi | Co | Step | Dual |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `dashboard.marketplace.read` (GMV/overview) | any | — | — | — | — | A | — | C²⁵ | — | | |
| `dashboard.finance.read` | any | — | — | — | — | C²⁵ | — | A | — | | |
| `fraud.alert.read` | any | — | — | — | — | A | — | C⁷ | — | | |
| `fraud.alert.action` | any | — | — | — | — | A | — | — | — | ✔ | |
| `system.health.read` | any | — | — | — | — | A | — | — | — | | |
| `audit.read` | any | — | — | — | — | A | C³ | C²⁵ | — | | |
| `audit.export` | any | — | — | — | — | A | — | — | — | ✔ | ✔ |
| `report.generate` | any/store | — | — | C²⁶ | D | A | C³ | A | — | | |
| `report.download.own` | own | — | — | A | D | A | A | A | A | | |
| `content.banner.manage` / `content.navigation.manage` / `content.landing.manage` / `content.category_page.manage` | any | — | — | — | — | C²⁷ | — | — | A | | |
| `content.blog.manage` / `content.buyingguide.manage` / `content.faq.manage` / `content.helpcenter.manage` | any | — | — | — | — | C²⁷ | — | — | A | | |
| `content.publish` | any | — | — | — | — | C²⁷ | — | — | A | | |
| `content.policy.edit` | any | — | — | — | — | C | — | — | A | | |
| `content.policy.publish` | any | — | — | — | — | A | — | — | — | ✔ | |
| `config.feature_flags.manage` / `config.marketplace_settings.manage` | any | — | — | — | — | A | — | — | — | ✔ | |
| `config.tax.manage` | any | — | — | — | — | A | — | C⁷ | — | ✔ | |
| `config.apikeys.manage` / `config.webhooks.manage` | any | — | — | — | — | A | — | — | — | ✔ | |
| `ops.breakglass` | any | — | — | — | — | C²⁸ | — | — | — | ✔ | ✔ |

Conditions: ²⁵Cross-view read of the counterpart's dashboard is limited/aggregate only. ²⁶Seller generates own-store reports only. ²⁷Admin may act on content in an emergency, audited; routine content is CONTENT's. ²⁸Break-glass is time-boxed, dual-approved, fully audited.

---

## 6. Seller-staff delegation model

- SELLER_STAFF holds **no platform role**. Its effective permission set is the intersection of (a) capabilities marked `D` (delegable) here and (b) the specific grants in `seller_staff_permissions`, always scoped to the seller's store(s).
- A seller cannot delegate a capability they do not themselves hold, cannot delegate `sellerstaff.permission.grant` (no privilege escalation), and cannot grant `any`-scoped permissions.
- Removing a staff member revokes all grants immediately; the audit trail retains an immutable actor label.
- KYC document upload, payout request, and staff-permission administration are **non-delegable** by default (seller-owner only) because they carry identity/financial risk.

## 7. Separation-of-duties assertions (test obligations)

| Assertion | Guarantee |
|---|---|
| SoD-1 | No `SUPPORT` grant has `writer=java` on money (`refund.approve*`, `payout.*`, `commission.*`, `payment.reconcile`). |
| SoD-2 | No `FINANCE` grant covers catalog moderation, content publishing, or support-case ownership. |
| SoD-3 | No `CONTENT` grant touches orders, payments, inventory, or PII. |
| SoD-4 | No `SELLER`/`SELLER_STAFF` permission resolves to `scope=any`. |
| SoD-5 | `payout.approve/reject`, `payout.freeze.release`, `refund.approve.large`, `users.erasure.execute`, `audit.export`, `users.impersonate`, `ops.breakglass` all require `dualApproval`. |
| SoD-6 | Every `stepUp` action re-checks MFA freshness at execution time. |

## 8. Step-up and dual-approval registry

- **Step-up MFA:** password/MFA changes, all role/permission/lock/suspend actions, seller approve/suspend/risk, product takedown, order override, inventory-reservation override, refund approvals, payout approve/reject/freeze/freeze-release, commission-rule and all `config.*` changes, `audit.export`, PII export, impersonation, content-policy publish, courier config, break-glass.
- **Dual approval:** `payout.approve` (above threshold), `payout.freeze.release`, `refund.approve.large`, `users.erasure.execute`, `audit.export`, `users.impersonate`, `ops.breakglass`.

## 9. Cross-cutting concerns explicitly covered

Impersonation (`users.impersonate`), GDPR erasure/data-subject (`users.erasure.request.own`/`execute`), PII read/export auditing (`users.pii.*`), break-glass (`ops.breakglass`), API keys & webhooks (`config.apikeys/webhooks.manage`), feature flags & marketplace settings (`config.*`), tax config (`config.tax.manage`), bulk product upload (`product.bulk_upload`), seller appeals (`seller.appeal.*`), notification templates (`notification.template.manage`), content publish workflow (`content.*.manage` → `content.publish`/`content.policy.publish`), report generation/download (`report.*`).

## 10. Verification obligations

The authorization suite (auth module §10, roadmap Gate B) must assert, for **every** row: the allow set, the deny set for all other roles, each `C` condition's positive and negative case, cross-tenant denial (seller A vs seller B), SoD-1…SoD-6, and that no mutation succeeds without an `audit_logs` row. Deny-by-default is proven by hitting each protected endpoint with each role lacking the grant and expecting `403 FORBIDDEN`.

## 11. Seed mapping

On approval, this matrix becomes:
- `permissions` rows — one per `code` above (with `module`, `description`).
- `roles` rows — the six platform roles.
- `role_permissions` — every `A` cell for `CUSTOMER/SELLER/ADMIN/SUPPORT/FINANCE/CONTENT`. `C` cells are granted at the coarse layer and gated by the application-service condition. `D` cells are **not** seeded to a platform role; they populate the seller-delegation catalog used by `seller_staff_permissions`.
- Delivered as `database/migrations/repeatable/R__seed_roles_permissions.sql` so the matrix evolves by reviewed migration.

## 12. Open decisions

1. Refund threshold separating `refund.approve.standard` from `refund.approve.large`, and the payout dual-approval threshold.
2. Whether `ADMIN` may action standard refunds at all, or refunds are strictly FINANCE (tighter SoD).
3. Impersonation policy: read-only vs limited-write, and retention of impersonation audit.
4. Whether `CONTENT` publishes directly or always through an approval step; whether policy copy needs legal sign-off distinct from `ADMIN`.
5. Guest checkout: confirmed out of MVP (account + verified email required) — ratify.
6. Exact `seller_staff` default grant bundle (which `D` capabilities a new staff member receives).
