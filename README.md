# Archim's TechCorner

Multi-vendor technology marketplace. Modular-monolith Java backend, three Next.js apps, Python analytics — see [`documentation/`](documentation/) for the full design baseline.

## Implementation status

| Component | Status |
|---|---|
| Design baseline (deliverables 1–4) | Complete — [`documentation/`](documentation/) |
| Auth & RBAC design (deliverable 5) | Complete — [`documentation/modules/authentication-rbac.md`](documentation/modules/authentication-rbac.md) |
| RBAC matrix | Complete — [`documentation/security/rbac-matrix.md`](documentation/security/rbac-matrix.md) |
| Auth & RBAC implementation | Complete — `services/marketplace-api` + `database/migrations` + `apps/customer-web` |
| **Seller Onboarding** | Complete — `services/marketplace-api` + `database/migrations` |
| **Catalog Module** | Complete — `services/marketplace-api` |

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

## Layout

```
apps/                 Next.js applications (customer-web implemented)
services/
  marketplace-api/    Java Spring Boot modular monolith (auth module implemented)
database/migrations/  Flyway migrations (identity slice implemented)
documentation/        Architecture, database, security, roadmap, module designs
compose.yaml          Local runtime (MySQL, Redis, MailHog, API)
```

See [`documentation/architecture/project-structure.md`](documentation/architecture/project-structure.md) for the full target structure.
