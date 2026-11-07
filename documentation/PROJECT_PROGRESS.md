# Project Progress

## Overall Status

| Phase | Title                      | Status      | Completed  |
|-------|----------------------------|-------------|------------|
| 1     | Planning and Design        | Complete    | 2026-03-23 |
| 2     | Backend Core               | Complete    | 2026-03-24 |
| 3     | Menu and Table Management  | Complete    | 2026-03-24 |
| 4     | Orders                     | Complete    | 2026-03-24 |
| 5     | Payments                   | Complete    | 2026-03-24 |
| 6     | Staff Web App (Next.js)    | Complete    | 2026-03-24 |
| 7     | Customer Mobile App (Expo) | Complete    | 2026-03-24 |
| 8     | Ghana-Specific Features    | Complete    | 2026-03-24 |
| 9     | Financial Controls         | Complete    | 2026-03-24 |
| 10    | Analytics, Testing, Deploy | Complete    | 2026-03-24 |
| 11    | Feature Completion & Operations Expansion | Complete | 2026-11-07 |
| 12    | Platform UI/UX Redesign ("Hearth")        | Complete | 2026-11-07 |
| 13    | Local Test Environment & Seed Data        | Complete | 2026-11-07 |

**Current focus:** fix the remaining issue found while seeding (two logins in the same second collide on the refresh token), check the redesign visually in a browser and the mobile app on a device, then production hardening (see `DEPLOYMENT_READINESS.md`).

---

## Phase 1 - Planning and Design (Complete)

**Completed:** 2026-03-23

### Deliverables produced
- `documentation/ROLES_AND_PERMISSIONS.md` - Full role matrix for Admin, Manager, Cashier, Customer
- `documentation/DATABASE_SCHEMA.sql` - Full PostgreSQL schema (V1 migration)
- `documentation/API_SPEC.md` - REST API contract for all 12 endpoint groups
- `documentation/WEBSOCKET_EVENTS.md` - WebSocket (STOMP) and SSE event specification
- `documentation/OFFLINE_STRATEGY.md` - Offline support scope, cache strategy, sync queue
- `documentation/DEVELOPMENT_GUIDE.md` - Master development guide with checkboxes
- `documentation/CHANGELOG.md` - Initialized
- `documentation/PROJECT_PROGRESS.md` - This file

### Key decisions made
- PostgreSQL with UUID primary keys throughout
- JWT (access + refresh) for auth; STOMP-over-SockJS for WebSocket
- SSE as fallback for mobile real-time updates
- Offline queue in mobile app (AsyncStorage/MMKV) with FIFO sync on reconnect
- Manager PIN produces a scoped, time-limited override token (5-minute TTL)
- Price snapshots stored on `order_items` so menu price changes do not affect past orders
- Idempotency key on payments to prevent duplicate charges
- Paystack webhook is the primary payment confirmation path; polling is fallback

---

## Phase 2 - Backend Core (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Spring Boot backend scaffold (`backend/pom.xml`, profile configs, Flyway migration)
- JWT auth flows (`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`) with refresh token revocation
- Role-guarded endpoints for `ADMIN`, `MANAGER`, `CASHIER`, `CUSTOMER`
- Manager PIN setup and verification with lockout policy and scoped override tokens
- PIN-guarded financial action endpoints (`/financial/discount`, `/financial/void`, `/financial/refund`)
- Audit logging for login, failed PIN, PIN lockout, and role assignment
- Integration tests for all required Phase 2 scenarios (30 tests passing)
- `documentation/reports/phase-2-report.md`

### Notes
- Runtime configuration uses PostgreSQL and Flyway in application profiles.
- Test execution in this environment uses H2 in PostgreSQL compatibility mode because Docker/Testcontainers is not available.

---

## Phase 3 - Menu and Table Management (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Menu category CRUD and menu item CRUD with role guards
- Public menu browse and search/filter endpoints
- Menu item availability toggle for admin/manager/cashier
- Table CRUD, table status update, QR token generation and scan validation
- Reservation create/list/status/cancel flows with conflict detection and ownership checks
- Flyway migration for Phase 3 tables (`categories`, `menu_items`, `restaurant_tables`, `reservations`)
- Integration tests for all Phase 3 scenarios (27 tests passing)
- `documentation/reports/phase-3-report.md`

### Notes
- Public endpoints are enabled for menu browse, table QR scan, and guest reservation creation.
- Conflict prevention for reservations uses table + overlapping time window checks on non-cancelled reservations.

---

## Phase 4 - Orders (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Full order core with `PICKUP`, `DELIVERY`, and `DINE_IN` flows
- Order status lifecycle support including delivery sub-states
- Order cancellation with manager override-token requirement for confirmed+ orders
- Order history listing with role-aware visibility and date filters
- Pickup code generation and lookup endpoint
- Dine-in table close endpoint
- Group ordering workflows:
  - Create session
  - Join session
  - Add participant items
  - View combined cart totals
  - Finalize group session into a single order with participant-linked items
- WebSocket/STOMP real-time event publishing for:
  - New orders
  - Order status changes
  - Group cart updates
- Flyway migration for Phase 4 tables (`orders`, `order_items`, `group_order_sessions`, `group_session_participants`, `group_session_items`)
- Phase 4 integration tests (`backend/src/test/java/com/restaurantmanager/core/order/OrderPhase4IntegrationTest.java`)
- `documentation/reports/phase-4-report.md`

### Notes
- Test runtime uses H2 with PostgreSQL compatibility mode in this environment.
- Real-time behavior is validated by integration tests that assert event publishing through the realtime publisher layer.

---

## Phase 5 - Payments (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Payment initiation endpoint with MoMo-first request shape and idempotency key handling
- Payment retrieval, verification (poll fallback), and retry endpoints
- Paystack webhook endpoint with signature verification and duplicate-event deduplication
- Internal payment status mapping (`INITIATED`, `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`, `VOIDED`)
- Automatic order transition from `PENDING` to `CONFIRMED` when payment succeeds
- Receipt retrieval by payment ID and by order ID
- WebSocket/STOMP realtime event publishing for payment status changes and failures
- Flyway migration for Phase 5 tables (`payments`, `payment_webhook_events`)
- Integration tests for all required Phase 5 scenarios (`backend/src/test/java/com/restaurantmanager/core/payment/PaymentPhase5IntegrationTest.java`)
- `documentation/reports/phase-5-report.md`

### Notes
- Paystack integration is abstracted behind a provider client to keep external gateway coupling isolated.
- Runtime configuration reads provider credentials from environment-backed properties.

---

## Phase 6 - Staff Web App (Next.js) (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- New Next.js staff app scaffold in `web/` using App Router + TypeScript + Tailwind
- Core staff pages:
  - `/login`
  - `/dashboard`
  - `/pos`
  - `/tables`
  - `/orders`
  - `/receipts`
  - `/admin`
- Auth/routing helpers for role-based redirects and protected-route checks
- Reusable manager PIN modal component and PIN verification logic
- POS/cart utility logic for add/update/remove, total calculation, and dine-in payload shaping
- Table map helper logic for status colors, active-order lookup, and realtime event reduction
- Order list helper logic for realtime new-order and status-update handling
- Financial helper logic for discount, refund, and reconciliation summary calculations
- Frontend test setup with Vitest + Testing Library
- Phase 6 scenario test suite (`web/src/features/phase6.test.tsx`)
- `documentation/reports/phase-6-report.md`

### Notes
- This phase establishes the staff web foundations and validated interaction logic with automated tests.
- `npm run build` and `npm run test` both pass in `web`.

---

## Phase 7 - Customer Mobile App (Expo) (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- New `mobile/` workspace for customer mobile scope with Expo-style configuration
- Core mobile feature logic for:
  - phone registration + OTP verification + guest session token flow
  - menu filtering and cart interactions
  - offline menu cache warnings and offline order queue/sync behavior
  - QR scan validation for dine-in linking
  - MoMo-first payment behavior and retry trigger flow
  - group ordering participation and host finalization flow
  - live tracking state transitions and reorder prepopulation
  - reservation creation and cancellation behavior
- Phase 7 scenario tests implemented in `mobile/src/features/phase7.test.ts`
- `documentation/reports/phase-7-report.md`

### Notes
- Mobile scope is implemented as domain/testable logic modules in this repository stage.
- `npm run test` and `npm run typecheck` pass in `mobile`.

---

## Phase 8 - Ghana-Specific Features (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- WhatsApp service abstraction with support for:
  - order confirmation messages
  - receipt + reorder-link messages
  - order status update messages
  - resilient failure handling and error logging behavior
- QR table-ordering support services for:
  - table QR PDF export payload generation
  - active table mobile-session tracking
  - session close cleanup behavior
- Loyalty system core services for:
  - payment-based points accrual
  - points redemption flow
  - transaction history entries
  - profile balance retrieval
- Promo engine support for:
  - valid/invalid promo checks
  - min-order checks
  - expiry checks
  - usage-limit checks
  - flat and percentage discount modes
- Buy-X-Get-Y offer service for free-item allocation when criteria are satisfied
- Phase 8 scenario tests:
  - `backend/src/test/java/com/restaurantmanager/core/promotion/EngagementFeaturesTest.java`
- `documentation/reports/phase-8-report.md`

### Notes
- Phase 8 implementation is delivered as backend service logic with deterministic unit-test coverage for all required scenarios.
- `cd backend && mvn test -q` passes with Phase 8 suite included.

---

## Phase 9 - Financial Controls & Reconciliation (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Refund control services:
  - override-token gate enforcement
  - partial/full refund handling
  - refund-amount guardrails
  - already-refunded protection
  - refund audit-event writing
- Void control services:
  - override-token gate enforcement
  - void status transition handling
  - dine-in table release on void
  - completed-order void rejection
  - void audit-event writing
- Discount control services:
  - override-token gate enforcement
  - flat and percentage discount application
  - discount>total rejection
  - discount audit-event writing
- Reconciliation services:
  - daily summary totals aggregation
  - manager sign-off with override-token requirement
  - duplicate sign-off conflict protection
- Audit review services:
  - action-based filtering
  - date-range filtering
  - cashier-forbidden audit query guard
- Added financial audit actions: `PAYMENT_REFUNDED`, `ORDER_VOIDED`, `DISCOUNT_APPLIED`
- Phase 9 scenario tests:
  - `backend/src/test/java/com/restaurantmanager/core/financial/FinancialControlsFeaturesTest.java`
- `documentation/reports/phase-9-report.md`

### Notes
- Phase 9 is implemented as backend financial-control domain services with comprehensive scenario-driven tests.
- `cd backend && mvn test -q` passes with Phase 9 tests included.

---

## Phase 10 - Analytics, Testing & Deployment (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Analytics services:
  - successful-payment daily revenue aggregation
  - top-selling items ranking by quantity
  - peak-hour detection
  - repeat-customer retention counting
- Load-test simulation services:
  - concurrent order-creation latency checks
  - concurrent payment-initiation latency checks
  - websocket-style event broadcast delivery count checks
- Security guard services:
  - search input sanitization checks
  - receipt-note escaping checks
  - customer order-access ownership checks (IDOR guard)
  - brute-force login rate-limit checks
  - webhook signature-header presence checks
- Phase 10 scenario tests:
  - `backend/src/test/java/com/restaurantmanager/core/analytics/AnalyticsSecurityFeaturesTest.java`
- `documentation/reports/phase-10-report.md`

### Notes
- Full regression was validated through `cd backend && mvn test -q`, which executes all prior phase tests in the suite.

---

## Post-Phase Runtime Wiring & Hardening (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Runtime API controllers/services added for previously isolated phase modules:
  - Phase 8 endpoints under `/promotions/**`, `/loyalty/**`, `/qr/**`, `/notifications/whatsapp/**` (originally `/phase8/**`)
  - Phase 9 financial controls merged into `/financial/**` (originally `/phase9/**`)
  - Phase 10 endpoints under `/analytics/**`, `/security/**`; load testing moved to k6 in `loadtest/` (originally `/phase10/**`)
- Security hardening updates:
  - login brute-force rate limiting (`429` after repeated failed logins)
  - secure random generation for pickup/session codes
  - configurable WebSocket origin allow-list
  - `spring.jpa.open-in-view` disabled
- Repository hygiene:
  - root `.gitignore` added for Maven/Node/IDE artifacts
- New integration test coverage:
  - `backend/src/test/java/com/restaurantmanager/core/runtime/DomainRuntimeIntegrationTest.java`

### Verification
- `cd backend && mvn test -q` -> PASS

---

## Phase 11 - Feature Completion & Operations Expansion (Complete)

**Completed:** 2026-11-07

### Deliverables produced
- The 16 features that were stubbed or simulated are now real, and the `phase8`/`phase9`/`phase10` packages were renamed to domain names (`/promotions`, `/loyalty`, `/qr`, `/financial`, `/analytics`, `/security`)
- SMS OTP phone verification and passwordless customer login (Hubtel or Arkesel; only logged unless a provider is set)
- Delivery zones with fees and minimum orders, rider dispatch, the rider workflow, and live rider location with customer tracking
- Live STOMP updates in the mobile app, with polling as a fallback
- Billing and tax: service charge, tax rates (including compound taxes), tips, and tax lines on orders
- Split payments for group and table bills, including guests paying their own share
- Inventory: ingredients, recipes, stock movements, items taken off the menu automatically when stock runs out, and low-stock alerts
- Menu photo upload (local disk or Cloudinary)
- Customer feedback and ratings, with manager replies
- Reports with CSV or PDF downloads (sales, reconciliation, audit, timesheets), staff scheduling, time clock and timesheets
- Account recovery: forgot/reset password by email or SMS link, email verification, password change, and an admin-triggered reset
- Web and mobile screens for all of the above; k6 load tests replace the mocked load-test endpoint
- Flyway migrations V13–V19

### Verification
- 258 backend tests (H2), plus web and mobile Vitest suites and typechecks

---

## Phase 12 - Platform UI/UX Redesign ("Hearth") (Complete)

**Completed:** 2026-11-07

### Deliverables produced
- **Design system** shared by web (`web/src/app/globals.css`) and mobile (`mobile/src/theme.ts`):
  - Colours: ivory, espresso, terracotta, muted gold and sage
  - Fonts: Manrope and Fraunces
  - Order and table status colours that are the same everywhere
  - A chart palette checked for colour-blind users
  - An icon set and shared components (`PageHeader`, `StatCard`, `EmptyState`, `ErrorState`, `StatusBadge`, data table, chips, segmented controls, skeletons, modal/bottom sheet, drawer, image uploader)
- **Customer web:**
  - New homepage built from the live menu, menu explorer, dish pages, and reservations with manage/cancel by phone number
  - QR table ordering redesigned for phones
  - Receipt-style running bill and a dark display-screen order board
  - Account pages for the links the backend sends
- **Staff web:**
  - App shell where what each role sees matches the backend permissions, with central handling of loading, sign-in and role checks
  - POS: menu and ticket side by side, required options enforced, kitchen notes, live pricing from `/orders/quote`, table picker, separate payment step
  - Dark high-contrast KDS; redesigned dashboard, orders, floor plan, menu builder (photo upload) and staff pages
- **Mobile:** every screen moved to the shared palette; the menu home, tab bar and headers were redesigned
- **Fixes found during the redesign:**
  - The POS could not sell dishes that have required options
  - Cash and card payments failed without a payer phone number
  - The API proxy corrupted binary uploads and downloads
  - The backend's reset and verify links pointed at pages that didn't exist
  - A "Request Service" button only showed a message and notified nobody; it was removed
  - Guest bookings needed a table ID, which customers couldn't know (the table `id` is now in `/tables/public`)
- The redesign was merged with the operations-expansion work. Both sides' features were kept: tips on the POS, guest split pay and ratings on the QR page, the recipe drawer, staff alerts, and the new nav entries

### Verification
- Web: 39 tests, `tsc`, production build (39 routes). Mobile: 48 tests and `tsc`. Backend table suites pass
- Every route and the main flows were checked over HTTP against a live, seeded backend. A visual check in a browser is still pending

---

## Phase 13 - Local Test Environment & Seed Data (Complete)

**Completed:** 2026-11-07

### Deliverables produced
- `scripts/dev/run-backend-local.sh`: backend on an H2 file database. It needs no Docker, PostgreSQL or Redis; payments use the stub, and SMS, WhatsApp and email are only logged
- `scripts/dev/seed_local.py`: related test data loaded through the real API:
  - Every staff role plus customers, 2 branches, 16 tables
  - A menu with options and recipes linked to stock
  - Billing settings and Ghana taxes, delivery zones, promo codes and offers
  - About 90 orders: 30 days of history plus live tickets at every kitchen stage
  - Reservations, a staff rota, feedback, a cash shift and a group-order session
  - Payments are confirmed with signed Paystack webhooks
- `scripts/dev/run-web-local.sh`: builds the web app for a given backend port and serves it
- Steps and test accounts are in `DEPLOYMENT_READINESS.md` → "Local Test Environment"

### Issues found and status
- Fixed: the dashboard "Best sellers" names were blank (the API sends `name` and the web read `itemName`)
- Fixed: orders from the mobile app had no branch. They now take the requested branch, the delivery zone's branch, the configured default or the only active branch, and any remaining unassigned orders are a shared queue for branch staff (`OrderBranchResolver`, `OrderBranchAssignmentIntegrationTest`)
- Open: two logins in the same second produce the same refresh token and return 500

