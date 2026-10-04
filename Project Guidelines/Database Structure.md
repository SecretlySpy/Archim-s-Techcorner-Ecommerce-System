# Database Structure

**Reconciled 2026-10-04 from live migrations + code (PHASE 1).** Source of truth is deployed Flyway + entity code, not just design doc.

## Current deployed (migrations present)

See database/migrations/ :
- identity: V1_1_001 core (users,roles,perms,user_roles,...), V1_1_002 sessions/tokens, V1_1_003 audit, V1_1_004 mfa
- commerce: V1_2_001 sellers (seller,store,warehouse,seller_staff), V1_3_001 catalog (cat,brand,product,variant,spec,image), V1_4_001 inventory (inventory,adjustment), V1_5_001 cart_wishlist
- repeatable: R__seed_roles_permissions

Matches implemented features; no order/payment/return/voucher tables yet (as expected per audit).

## Data ownership and model (partial, from migrations+entities)

| Entity/table | Owner | Key/relationships | ... | Source |
|----|----|----|----|----|
| users, roles, ... | identity | public_id BINARY, active_marker unique | ... | MySQL via Java |
| sellers, stores, warehouses | sellers | ... | ... | ... |
| products, variants, images, specs | catalog | seller_id, status | ... | ... |
| inventory, adjustments | inventory | on_hand/reserved, CHECK >=0 | ... | ... |
| cart_items, wishlist_items | cart | user+variant unique | ... | ... |

Full design in documentation/database/mysql-design.md (extends beyond current migrations).

## Integrity and lifecycle

See mysql-design: short tx, no locks across providers (DB-08), DECIMAL money, append-only audit/ledger, etc. Redis disposable. Current impl (cart/inv) follows owner checks + tx.

## Migrations

Additive only observed. Flyway in app. See Verification for migration tests to add.

**Plan:** extend with equivalent for checkout etc when T13+. Never parallel models.
