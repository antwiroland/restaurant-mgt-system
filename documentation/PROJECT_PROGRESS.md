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
  - `backend/src/test/java/com/restaurantmanager/core/phase8/Phase8FeaturesTest.java`
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
  - `backend/src/test/java/com/restaurantmanager/core/phase9/Phase9FeaturesTest.java`
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
  - `backend/src/test/java/com/restaurantmanager/core/phase10/Phase10FeaturesTest.java`
- `documentation/reports/phase-10-report.md`

### Notes
- Full regression was validated through `cd backend && mvn test -q`, which executes all prior phase tests in the suite.

---

## Post-Phase Runtime Wiring & Hardening (Complete)

**Completed:** 2026-03-24

### Deliverables produced
- Runtime API controllers/services added for previously isolated phase modules:
  - Phase 8 endpoints under `/phase8/**`
  - Phase 9 endpoints under `/phase9/**`
  - Phase 10 endpoints under `/phase10/**`
- Security hardening updates:
  - login brute-force rate limiting (`429` after repeated failed logins)
  - secure random generation for pickup/session codes
  - configurable WebSocket origin allow-list
  - `spring.jpa.open-in-view` disabled
- Repository hygiene:
  - root `.gitignore` added for Maven/Node/IDE artifacts
- New integration test coverage:
  - `backend/src/test/java/com/restaurantmanager/core/runtime/PhaseRuntimeIntegrationTest.java`

### Verification
- `cd backend && mvn test -q` -> PASS

