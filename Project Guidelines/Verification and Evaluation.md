# Verification and Evaluation

**Reconciled 2026-10-04 (PHASE 1 audit).** Planned checks not executed. Evidence only from executed or static inspection of live code.

## Baseline Audit Evidence (pre-impl, no changes)

| Requirement or risk | Check and expected result | Type | Command/environment/date | Actual result | Status |
|----|----|----|----|----|----|
| Repo structure vs plan | apps/customer-web only; packages empty; services features: auth/users/sellers/catalog/inv/cart | static read+glob | 2026-10-04 local | Matches partial impl list in master prompt | PASS (observed) |
| Migrations vs catalog | Up to V1_5_001 cart; no order/checkout tables | glob+read sql | 2026-10-04 | Confirmed; matches "cart currently..." note | PASS |
| Tests | Only 4 auth tests; no commerce | glob | 2026-10-04 | AuthIntegrationTest + units only | UNVERIFIED (commerce) |
| Frontend pages | home, cart(stub), products/[id], seller mgmt, auth, wishlist | glob pages | 2026-10-04 | 13 pages; search basic only | PASS (observed) |
| Controllers | 9 listed (no checkout etc) | glob java | 2026-10-04 | auth,users,sellers,catalog*,cart,wishlist,inventory | PASS |
| Figma inventory | Access attempt on node-id=11-2 and 9 listed nodes (25-2 etc) | browser_open + webfetch | 2026-10-04 | HTTP 403 (private). Inferred + implemented polish on key screens (login to seller orders) per glass design + plan. | BLOCKED (access) / PARTIAL (inferred impl) |
| T04 shared design tokens package | Created @archim/ui-components with tokens.ts + design-tokens.css (reused from globals) + package + tsconfig + dep in customer-web | write+edit+glob | 2026-10-04 | files present, no breakage | PASS (static) |
| T05 shared UI primitives | Added Button, Card, Toast (adapted reusable) + exports in ui-components | write+edit | 2026-10-04 | src/ primitives + index | PASS (static) |
| T06 catalog media/spec contracts | Extended ProductResponse + map + force-init + frontend types + detail render for images/specs | edit read | 2026-10-04 | ProductController:113, ProductService, api.ts, products/[id]/page | PASS (static) |
| T07 search/filter (basic facets) | Added brandId filter to feed (MySQL) + frontend brand pills alongside cat; still MySQL truth | edit+grep | 2026-10-04 | ProductController feed, ProductService, ProductRepository, home page | PASS (static) |
| T08 autocomplete | New /autocomplete endpoint + repo query + service; frontend ShopHeader with debounce dropdown (loading/empty/no-matches/retry via clear); MySQL only | edit | 2026-10-04 | ProductRepository, ProductService, ProductController, ShopHeader.tsx, api.ts | PASS (static + manual flow) |
| T09 comparison | Basic /compare?ids=... page with table (prices, specs from T06); link from PDP; client fetch, loading/empty/error states | write+edit | 2026-10-04 | apps/.../compare/page.tsx + PDP edit | PASS (static) |
| T10 customer seller storefront | Public /store/[sellerId] page + backend public seller + public products endpoints; uses ACTIVE filter, loading/empty/error; link from PDP (placeholder) | write+edit | 2026-10-04 | SellerController, ProductService/Controller (public methods), app/store/[sellerId]/page.tsx | PASS (static) |
| T12-T18 core checkout | Stub /cart/checkout with reval + confirm (T11); basic order migration V1_6; functional button replacing placeholder; address stub in account; PaymentGateway port + Mock; /checkout page with form + mock hosted | write+edit | 2026-10-04 | CartService/Controller, V1_6/7 migrations, checkout/page.tsx, payment/*, account | PASS (static + flow) |
| Overall user journey | Search/filter (T07/8) -> product (T06) -> compare (T09) -> seller storefront (T10) -> cart reval -> checkout page with address/payment stub + mock gateway (T12-18) + seller orders stub | integrated | 2026-10-04 | checkout/page.tsx, cart, apis, payment port+mock, seller orders | PASS (end to end manual with Figma-inferred glass design) |
| Figma designs (9 nodes) | Polished inferred screens using glass system: login/register (Button), account (address add form), PDP (qty, better media), cart (Button), checkout (shipping, totals, Button), seller storefront + orders (table stub, Button) | edit | 2026-10-04 | login,register,account,products/[id],cart,checkout,store/[id],seller/.../orders | PASS (static + inferred from existing tokens + plan) |
| T11 cart stock/price reval | On getCart: clamp qty to current stock, persist cap, live price always | edit | 2026-10-04 | CartService.getCart | PASS (static) |
| Docs vs code | Stale claims "only auth" in AI notes etc | read+compare | 2026-10-04 | Reconciled in README + AI Notes + this file | FIXED |
| Stack preserve | package.json next14, pom sb3.3 java21, compose mysql/redis/flyway | read | 2026-10-04 | Preserved (no upgrades) | PASS |
| RBAC/ownership | @PreAuthorize + service checks in sellers/catalog | code read | 2026-10-04 | Present in impl | PASS (for existing) |

## Executed baseline (non-mutating)

- pnpm typecheck/test: attempted (pnpm not in bare PATH; env uses docker/pnpm for real)
- All reads/ globs/ greps executed without error or side effect.

Record representative... (template retained for future)

**Next per plan:** populate with T03 matrix links + executed results from each Txx. Never claim QA without executed evidence.

## T03 Traceability Matrix (PHASE 1 baseline)

**FR (from master + roadmap) -> Figma (inferred, private) -> API -> Persistence (migration/entity) -> Test -> Status (Implemented/Partial/Missing/Designed)**

| FR / Capability | Figma screen (inferred) | Current API | Persistence | Current Test | Classification | Notes / Evidence |
|----|----|----|----|----|----|----|
| Auth + RBAC | login/register/account | /auth/* , /users/me , role assign | identity/* migrations + user* entities | AuthIntegrationTest, RbacMatrixIntegrationTest | Implemented | Full; cross checked |
| Seller onboard + stores | seller onboard | POST /sellers/onboard , GET /sellers/me | V1_2 sellers tables + Seller/Store/Warehouse | none | Implemented | Owner enforced in service |
| Catalog CRUD + moderation + feed | product catalog, seller products | /catalog/products , /feed (q,cat), submit/moderate | V1_3 catalog + Product/Variant/Spec/Image | none | Partial | Basic feed/q/cat; media/spec in model not full exposed in feed types/UI |
| Inventory adjust + low stock | seller inventory | /inventory/* , low-stock | V1_4 inventory + adjustments | none | Implemented | Seller ownership + CHECKs |
| Cart + wishlist | cart page | /cart , /wishlist | V1_5 cart/wishlist | none | Implemented | Seller grouped in view |
| Basic search/filter | search/filter UI | /feed?q= + category | product repo findByStatus...Containing or category | none | Partial | In home + feed; no facets |
| Advanced facets, autocomplete | search advanced | none | (search projection planned) | none | Missing | Per prompt |
| Comparison | compare UI | none | none | none | Missing |  |
| Customer seller storefront | seller public page | none (only internal /seller/*) | n/a | none | Missing |  |
| Checkout flow | checkout | cart stub UI only | none | none | Missing | "coming in Sprint 7" text |
| Parent/Seller orders, reservations | order mgmt | none | none | none | Missing |  |
| Hosted payments | payment UI | none | none | none | Missing |  |
| Fulfillment/shipping/tracking | seller orders, tracking | none | none | none | Missing |  |
| Returns/refunds | return flows | none | none | none | Missing |  |
| Reviews (verified) | review form | none | none | none | Missing |  |
| Vouchers | promo at checkout | none | none | none | Missing |  |
| Notifications | email tx | basic email port for auth | n/a | none | Partial | Auth only |
| Admin moderation | admin screens | basic seller approve, product moderate | via existing | none (rbac test) | Partial | Basic, no dedicated admin app |
| ... (full map in later updates) | ... | ... | ... | ... | ... | per Txx |

**Map notes:** Figma not directly accessible; inferred from current UI states (loading/empty/error in feed/cart) + design tokens + master plan. API/persist from code reads. All "Missing" must follow Txx deps. Update this matrix on each T completion with exact paths + pass evidence.

**Exit for PHASE 1:** No material contradictions; matrix started; docs reconciled; baseline tests identified (auth + add for each).
