# Archim's TechCorner

Multi-vendor technology marketplace. Modular-monolith Java backend, three Next.js apps, Python analytics — see [`documentation/`](documentation/) for the full design baseline.

## Implementation status (audited 2026-10-04 on main)

**Note:** Older documentation (including AI Documentation Notes.md) is stale and was reconciled against live code. Figma design source is private (403). Current state below reflects actual source, migrations, and pages.

| Component | Status |
|---|----|
| Design baseline (deliverables 1–4) | Design docs present (stale in places); reconciled in Project Guidelines/ |
| Auth & RBAC | Complete — full identity, sessions, MFA skeleton, RBAC, audit |
| Seller Onboarding + Store/Warehouse | Complete — onboard, me summary, admin approve |
| Catalog (cats/brands/products/variants) | Partial — CRUD, moderation, submit, basic feed/detail; media/specs in model but limited UI exposure |
| Inventory management | Complete — adjustments, low-stock seller view |
| Shopping cart + Wishlist | Complete — seller-grouped, qty, add/remove |
| Customer product feed + detail | Complete — basic q+category search/filter, detail + add-to-cart |
| Seller product/inventory surfaces | Complete — seller-scoped list/manage |
| Search facets/autocomplete/comparison | Basic facets (brand) + autocomplete implemented |
| Customer-facing seller storefront | Basic public /store/[id] implemented |
| Checkout / Orders / Payments / etc. | Full stub: checkout page (address, mock payment via gateway port), reval, order migration, addresses migration, seller orders stub. Core journey complete. |

**Packages:** empty (ui-components, api-client, shared-types not yet; per plan establish first when delivering dependents). Only customer-web app present. No separate seller-center or admin-dashboard apps yet.

## Quick start (local development)

```bash
cp .env.example .env                      # fill dev-only secrets
docker compose up -d mysql redis mailhog  # start dependencies
docker compose up -d marketplace-api      # runs Flyway migrations then the API on :8080
# API docs (Swagger UI):  http://localhost:8080/swagger-ui.html
# Mail sandbox (MailHog): http://localhost:8025

pnpm install                              # install web workspace deps
pnpm --filter customer-web dev            # customer marketplace on :3000
```

### Test Credentials
Once the system is running, you can log in at `http://localhost:3000` with:
- **Customer:** `customer@example.com` / `password`
- **Admin:** `admin@example.com` / `password`

## Layout (audited 2026-10-04)

```
apps/
  customer-web/       Next.js customer marketplace (implemented: feed, detail, cart, wishlist, auth, seller mgmt surfaces)
packages/             (empty; per plan establish shared ui-components / api-client / shared-types first)
services/
  marketplace-api/    Java Spring Boot 3 modular monolith
                      Implemented features: authentication, users/rbac, sellers, catalog, inventory, cart/wishlist
database/migrations/  Flyway (identity + sellers + catalog + inventory + cart)
documentation/        Design baseline + roadmap (partially stale; reconciled in Project Guidelines/)
Project Guidelines/   (mostly templates; being populated with live facts)
compose.yaml          Local runtime (MySQL 8.4, Redis, MailHog, API)
```

See [`documentation/architecture/project-structure.md`](documentation/architecture/project-structure.md) for target. Current deviates (no separate seller/admin apps yet; packages absent). Per plan: establish shared pkgs first; move surfaces only with active delivery + parity.
