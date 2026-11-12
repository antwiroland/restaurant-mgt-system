# Changelog

All notable changes to this project are documented here.
Format: [Phase N - Title] followed by Added / Changed / Decisions.

---

## [Phase 1 - Planning and Design] - 2026-03-23

### Added
- `documentation/ROLES_AND_PERMISSIONS.md` - Permission matrix for all four roles
- `documentation/DATABASE_SCHEMA.sql` - Full PostgreSQL V1 schema (17 tables, enums, indexes, triggers)
- `documentation/API_SPEC.md` - REST API specification across 12 endpoint groups
- `documentation/WEBSOCKET_EVENTS.md` - STOMP WebSocket topics and SSE fallback specification
- `documentation/OFFLINE_STRATEGY.md` - Offline scope, cache durations, and sync queue design
- `documentation/DEVELOPMENT_GUIDE.md` - Master guide with 10 phases and checkbox task lists
- `documentation/PROJECT_PROGRESS.md` - Phase progress tracker
- `documentation/CHANGELOG.md` - This file

### Decisions
- UUID primary keys on all tables
- Price snapshot on `order_items` to freeze prices at order time
- Manager PIN to scoped override-token pattern with 5-minute TTL
- Idempotency key on `payments` to prevent duplicate Paystack charges
- Paystack webhook as authoritative payment status source; polling as fallback
- Mobile offline queue with FIFO processing and 3-retry limit

---

## [Phase 2 - Backend Core] - 2026-03-24

### Added
- Spring Boot backend project with Maven and Java 17 (`backend/pom.xml`)
- Environment profile configuration (`application.yml`, `application-dev.yml`, `application-staging.yml`, `application-prod.yml`)
- Flyway migration for Phase 2 core tables (`users`, `refresh_tokens`, `audit_logs`)
- Production structured logging config (`logback-spring.xml` with JSON encoder in `prod`)
- Domain model and repositories for users, refresh tokens, and audit logs
- JWT token service supporting access, refresh, and scoped override tokens
- Security filter chain for stateless JWT authentication and role-based authorization
- Auth endpoints:
  - `POST /auth/register`
  - `POST /auth/login`
  - `POST /auth/refresh`
  - `POST /auth/logout`
  - `POST /auth/pin/verify`
- User endpoints:
  - `GET /users` (admin only)
  - `PATCH /users/{id}/role` (admin only)
  - `POST /users/{id}/pin` (manager/admin constraints)
- PIN-guarded financial endpoints:
  - `POST /financial/discount`
  - `POST /financial/void`
  - `POST /financial/refund`
- Audit event writing for `USER_LOGIN`, `USER_LOGOUT`, `PIN_FAILED`, `PIN_LOCKED`, `PIN_VERIFIED`, and `ROLE_ASSIGNED`
- Phase 2 integration test suite (30 scenario tests)

### Changed
- Test runtime adapted to H2 in PostgreSQL compatibility mode because Docker/Testcontainers is unavailable in this execution environment.

### Verified
- `cd backend && mvn test -q` passes (30 tests, 0 failures).

---

## [Phase 3 - Menu and Table Management] - 2026-03-24

### Added
- Flyway migration `V2__phase3_menu_tables.sql` for `categories`, `menu_items`, `restaurant_tables`, and `reservations`
- Menu module:
  - `GET /menu/categories` (public)
  - `POST /menu/categories` (admin/manager)
  - `PUT /menu/categories/{id}` (admin/manager)
  - `DELETE /menu/categories/{id}` (admin)
  - `GET /menu/items` (public with `categoryId`, `available`, `q` filters)
  - `GET /menu/items/{id}`
  - `POST /menu/items` (admin/manager)
  - `PUT /menu/items/{id}` (admin/manager)
  - `PATCH /menu/items/{id}/availability` (admin/manager/cashier)
  - `DELETE /menu/items/{id}` (admin)
- Table module:
  - `GET /tables` (staff)
  - `POST /tables` (admin)
  - `PUT /tables/{id}` (admin)
  - `PATCH /tables/{id}/status` (admin/manager/cashier)
  - `GET /tables/{id}/qr` (staff)
  - `POST /tables/scan` (public)
- Reservation module:
  - `POST /reservations` (public guest or authenticated customer)
  - `GET /reservations` (staff, `date` and `tableId` filters)
  - `PATCH /reservations/{id}/status` (staff)
  - `DELETE /reservations/{id}` (staff or reservation owner)
- Phase 3 integration tests for menu, tables, and reservations (`backend/src/test/java/com/restaurantmanager/core/menu|table|reservation`)

### Changed
- Security configuration now permits public access to:
  - `/menu/**`
  - `POST /tables/scan`
  - `POST /reservations`
- Integration-test DB cleanup now includes Phase 3 repositories.

### Verified
- `cd backend && mvn test -q` passes (57 tests total, 0 failures).

---

## [Phase 4 - Orders] - 2026-03-24

### Added
- Flyway migration `V3__phase4_orders.sql` for:
  - `orders`
  - `order_items`
  - `group_order_sessions`
  - `group_session_participants`
  - `group_session_items`
- Order module endpoints:
  - `POST /orders`
  - `GET /orders`
  - `GET /orders/{id}`
  - `PATCH /orders/{id}/status`
  - `DELETE /orders/{id}`
  - `GET /orders/pickup/{pickupCode}`
  - `POST /orders/dine-in/tables/{tableId}/close`
- Group-order endpoints:
  - `POST /orders/group/sessions`
  - `POST /orders/group/sessions/{code}/join`
  - `POST /orders/group/sessions/{code}/items`
  - `GET /orders/group/sessions/{code}`
  - `POST /orders/group/sessions/{code}/finalize`
- Real-time publisher integration for:
  - order created
  - order status changed
  - group cart updated
- Expanded Phase 4 integration tests (`OrderPhase4IntegrationTest`) covering:
  - core order create/update/cancel/history scenarios
  - pickup code generation and lookup
  - delivery status sub-states
  - dine-in table flows
  - group session create/join/add/finalize paths
  - item price snapshot and participant ownership assertions
  - realtime event publishing assertions
- `documentation/reports/phase-4-report.md`

### Verified
- `cd backend && mvn test -q` passes (85 tests total, 0 failures).

---

## [Phase 5 - Payments] - 2026-03-24

### Added
- Flyway migration `V4__phase5_payments.sql` for:
  - `payments`
  - `payment_webhook_events`
- Payment module entities/repositories:
  - `PaymentEntity`
  - `PaymentWebhookEventEntity`
  - `PaymentRepository`
  - `PaymentWebhookEventRepository`
- Payment API endpoints:
  - `POST /payments/initiate`
  - `GET /payments/{id}`
  - `GET /payments/{id}/verify`
  - `POST /payments/{id}/retry`
  - `POST /payments/webhook`
  - `GET /payments/{id}/receipt`
  - `GET /payments/orders/{orderId}/receipt`
- Paystack provider abstraction:
  - `PaystackClient`
  - `StubPaystackClient`
- Payment realtime publisher integration:
  - `PAYMENT_STATUS_CHANGED`
  - `PAYMENT_FAILED`
- Payment configuration properties:
  - `payments.paystack.secret-key`
  - `payments.paystack.base-url`
  - `payments.default-currency`
- Signature verification for webhook via `x-paystack-signature` (HMAC-SHA512)
- Webhook deduplication using processed-event storage
- Payment success handling to set `paidAt` and transition linked order from `PENDING` to `CONFIRMED`
- Phase 5 integration test suite:
  - `backend/src/test/java/com/restaurantmanager/core/payment/PaymentPhase5IntegrationTest.java`
- `documentation/reports/phase-5-report.md`

### Changed
- Security configuration now permits public access to `POST /payments/webhook`.
- Base integration-test cleanup now includes payment repositories.

### Verified
- `cd backend && mvn test -q` passes (102 tests total, 0 failures).

---

## [Phase 6 - Staff Web App (Next.js)] - 2026-03-24

### Added
- New `web/` Next.js application (App Router + TypeScript + Tailwind)
- Staff app routes:
  - `/`
  - `/login`
  - `/dashboard`
  - `/pos`
  - `/tables`
  - `/orders`
  - `/receipts`
  - `/admin`
- Role-aware auth/routing helpers (`web/src/features/auth/auth.ts`)
- POS/cart/order payload logic (`web/src/features/pos/cart.ts`)
- Table map realtime helper logic (`web/src/features/tables/tables.ts`)
- Active-order realtime helper logic (`web/src/features/orders/orders.ts`)
- Financial/PIN helper logic:
  - `web/src/features/financial/pin.ts`
  - `web/src/features/financial/reconciliation.ts`
- Reusable PIN modal component (`web/src/components/PinModal.tsx`)
- Frontend test toolchain:
  - Vitest
  - @testing-library/react
  - @testing-library/jest-dom
  - @testing-library/user-event
  - jsdom
- Phase 6 frontend scenario tests (`web/src/features/phase6.test.tsx`)
- `documentation/reports/phase-6-report.md`

### Verified
- `npm run test` passes in `web` (23 tests, 0 failures).
- `npm run build` passes in `web`.

---

## [Phase 7 - Customer Mobile App (Expo)] - 2026-03-24

### Added
- New `mobile/` workspace with:
  - `app.json`
  - `app/index.tsx`
  - TypeScript + Vitest setup
- Mobile feature modules:
  - `src/features/auth/auth.ts`
  - `src/features/menu/menu.ts`
  - `src/features/offline/offline.ts`
  - `src/features/qr/qr.ts`
  - `src/features/payment/payment.ts`
  - `src/features/group/group.ts`
  - `src/features/tracking/tracking.ts`
  - `src/features/reservation/reservation.ts`
- Phase 7 scenario test suite:
  - `mobile/src/features/phase7.test.ts`
- `documentation/reports/phase-7-report.md`

### Verified
- `npm run test` passes in `mobile` (25 tests, 0 failures).
- `npm run typecheck` passes in `mobile`.

---

## [Phase 8 - Ghana-Specific Features] - 2026-03-24

### Added
- Phase 8 backend service modules under `backend/src/main/java/com/restaurantmanager/core/phase8/`:
  - `whatsapp/` (`WhatsAppGateway`, `WhatsAppService`)
  - `qr/` (`TableQrPdfService`, `MobileSessionService`)
  - `loyalty/` (`LoyaltyAccount`, `LoyaltyService`, transaction models)
  - `promo/` (`PromoCode`, `PromoService`)
  - `offer/` (`BuyXGetYOfferService`)
  - `common/DiscountType`
- Phase 8 scenario tests:
  - `backend/src/test/java/com/restaurantmanager/core/phase8/Phase8FeaturesTest.java`
- `documentation/reports/phase-8-report.md`

### Verified
- `cd backend && mvn test -q` passes with Phase 8 tests included (123 tests total, 0 failures).

---

## [Phase 9 - Financial Controls & Reconciliation] - 2026-03-24

### Added
- New Phase 9 financial-control service modules:
  - `backend/src/main/java/com/restaurantmanager/core/phase9/refund/**`
  - `backend/src/main/java/com/restaurantmanager/core/phase9/voiding/**`
  - `backend/src/main/java/com/restaurantmanager/core/phase9/discount/**`
  - `backend/src/main/java/com/restaurantmanager/core/phase9/reconciliation/**`
  - `backend/src/main/java/com/restaurantmanager/core/phase9/audit/**`
- Financial audit-action enum values:
  - `PAYMENT_REFUNDED`
  - `ORDER_VOIDED`
  - `DISCOUNT_APPLIED`
- Phase 9 scenario tests:
  - `backend/src/test/java/com/restaurantmanager/core/phase9/Phase9FeaturesTest.java`
- `documentation/reports/phase-9-report.md`

### Verified
- `cd backend && mvn test -q` passes with Phase 9 tests included (148 tests total, 0 failures).

---

## [Phase 10 - Analytics, Testing & Deployment] - 2026-03-24

### Added
- New Phase 10 modules:
  - `backend/src/main/java/com/restaurantmanager/core/phase10/analytics/**`
  - `backend/src/main/java/com/restaurantmanager/core/phase10/load/**`
  - `backend/src/main/java/com/restaurantmanager/core/phase10/security/**`
- Phase 10 scenario tests:
  - `backend/src/test/java/com/restaurantmanager/core/phase10/Phase10FeaturesTest.java`
- `documentation/reports/phase-10-report.md`

### Verified
- `cd backend && mvn test -q` passes with Phase 10 tests included (161 tests total, 0 failures).

---

## [Post-Phase Runtime Wiring & Hardening] - 2026-03-24

### Added
- Runtime API wiring for previously isolated phase modules:
  - `POST /phase8/promo/apply`
  - `POST /phase8/offer/free-items`
  - `POST /phase8/loyalty/accrue`
  - `POST /phase8/loyalty/redeem`
  - `GET /phase8/loyalty/{customerId}/balance`
  - `GET /phase8/loyalty/{customerId}/history`
  - `POST /phase8/qr/sessions/link`
  - `GET /phase8/qr/sessions/{tableNumber}/active`
  - `DELETE /phase8/qr/sessions/{tableNumber}`
  - `GET /phase8/qr/tables/{tableNumber}/pdf`
  - `POST /phase8/whatsapp/order-confirmation`
  - `POST /phase8/whatsapp/receipt`
  - `POST /phase8/whatsapp/status`
  - `POST /phase9/discount/apply`
  - `POST /phase9/refund/apply`
  - `POST /phase9/void/apply`
  - `POST /phase9/reconciliation/summarize`
  - `POST /phase9/reconciliation/{businessDate}/sign-off`
  - `GET /phase9/audit`
  - `POST /phase10/analytics/summary`
  - `POST /phase10/load/run`
  - `POST /phase10/security/sanitize-search`
  - `POST /phase10/security/escape-notes`
  - `GET /phase10/security/order-access/{ownerId}`
- New integration tests for phase runtime endpoints:
  - `backend/src/test/java/com/restaurantmanager/core/runtime/PhaseRuntimeIntegrationTest.java`

### Changed
- Login brute-force protection now returns `429 Too Many Requests` after repeated failures.
- Order pickup/session code generation now uses `SecureRandom` instead of `Math.random`.
- WebSocket allowed origins are now configuration-driven (`websocket.allowed-origin-patterns`) instead of wildcard.
- `spring.jpa.open-in-view` disabled.
- Added repository `.gitignore` for build/dependency artifacts.

### Verified
- `cd backend && mvn test -q` passes (all tests including new phase runtime integration tests).

---

## [Backend Redis Caching] - 2026-03-24

### Added
- Redis caching dependencies:
  - `spring-boot-starter-cache`
  - `spring-boot-starter-data-redis`
- Cache configuration with Redis/in-memory fallback:
  - `backend/src/main/java/com/restaurantmanager/core/config/CacheConfig.java`
  - `backend/src/main/java/com/restaurantmanager/core/config/CacheProps.java`
- Cache property support:
  - `app.cache.redis-enabled`
  - `app.cache.default-ttl-seconds`
  - `spring.data.redis.*` host/port/password settings

### Changed
- Added cache annotations for hot read paths with eviction on writes:
  - menu category/menu item reads and mutating endpoints
  - table list/qr/scan reads and mutating endpoints
  - table-close flow now evicts table caches
- Disabled Redis repository auto-config for cleaner startup:
  - `BackendCoreApplication` excludes `RedisRepositoriesAutoConfiguration`
- Production profile now enables Redis cache by default (`app.cache.redis-enabled: true`)

### Verified
- `cd backend && mvn test -q` passes after caching changes.

---

## [Domain Refactor & Feature Completion] - 2026-11-06

### Changed
- `phase8`/`phase9`/`phase10` packages and routes renamed to domain names: `/promotions`, `/loyalty`, `/qr`, `/notifications/whatsapp`, `/financial`, `/analytics`, `/security`.
- The in-memory `/financial-controls` simulation was merged into the persisted `/financial/*` API.
- The mocked `/load-tests/run` endpoint was replaced by k6 scripts in `loadtest/`.

### Added
- Live Paystack client (initialize, verify, refund), WhatsApp Cloud API gateway, and Hubtel/Arkesel SMS gateways. All are selected by environment variables, with stub/log defaults.
- Real discount, void and refund on orders and payments; `payment_refunds`; reconciliation computed from the ledger with persisted sign-off; financial audit trail in `audit_logs`.
- Promo codes applied at order creation with atomic usage limits; Buy-X-Get-Y offers (`menu_offers`) managed and applied automatically; loyalty redemption at checkout and automatic accrual on full payment.
- Persisted table QR sessions (`table_mobile_sessions`) and printable PDF table cards.
- SMS OTP phone verification for registration, and passwordless customer login.
- Live STOMP updates in the mobile app, with per-order topics.
- Delivery zones and fees, `RIDER` role, dispatch board, rider page with live location, and customer delivery tracking.
- Accessibility fixes (focus return, error linkage, nav labels) and token-only error pages.

### Migrations
- `V9__order_pricing_financial_controls.sql`, `V10__table_mobile_sessions.sql`, `V11__otp_verification.sql`, `V12__delivery.sql`

---

## [Operations Expansion] - 2026-11-07

### Added
- Backend (commit `0f82bf3`): service charge, VAT/NHIL/GETFund taxes and tips on orders and receipts; split payments; ingredient inventory with recipes, stock ledger, low-stock alerts and auto-86; menu image upload (local or Cloudinary); customer feedback; CSV/PDF report exports; staff scheduling, time clock and timesheets; password reset, password change and email verification.
- `GET /scheduling/staff` so managers (who cannot list users) can build the rota.
- Web staff pages: `/billing` (service charge, tax rates, tips), `/inventory`, `/schedule` (time clock, weekly rota, timesheets), `/feedback`, `/reports`, `/account`.
- Web public pages: `/forgot-password`, `/reset-password`, `/verify-email`; "Forgot password?" on the login page.
- Web: itemised bill (discounts, service charge, each tax, tip) on order detail and receipts; split-bill panel on order detail; photo upload and recipe editor on the menu page; tip field on POS; admin "Send reset link" on Users; live low-stock and low-rating alerts in the app shell.
- Mobile: tip choice and a note on service charge and taxes at checkout; itemised bill on the order screen; rate a completed or delivered order and see the restaurant's reply; split a group order by items and pay your own share by MoMo.

### Fixed
- Two logins by the same user within one second produced identical refresh tokens and failed with a 500 (unique `token_hash`). JWTs now carry a random `jti`.
- The web API proxy read request and response bodies as text, which corrupted file uploads and PDF downloads. It now passes raw bytes and forwards `Content-Disposition`.

### Migrations
- `V13__billing_tax_tips.sql` to `V19__account_recovery.sql`

### Verified
- Backend: 258 tests pass (`mvn test`). Web: 29 Vitest tests, typecheck and production build. Mobile: 44 Vitest tests and typecheck.

---

## [Group, Guest and Recovery Follow-ups] - 2026-11-07

### Added
- Group participants can view the placed group order and see it in their order list (view only), so they can pay their share. The mobile group screen links to the order once it is placed; only the order's owner sees "split by items".
- Public guest split-payment endpoints for dine-in tables (equal split, MoMo per share, payment status check), scoped to the table's QR token.
- Web table QR page: guests split and pay their share by MoMo while the order is open, and rate the order once it is completed.
- Mobile "Forgot password?" screen: request a reset link and finish the reset in the app by pasting the link.

### Verified
- Backend: 261 tests pass (H2). Web: 32 tests, typecheck and production build. Mobile: 48 tests and typecheck.
- Not yet run: the Postgres Flyway smoke test (`mvn test -Ppostgres-it`), because the Docker engine was not available.

---

## [HR - Employees, Branches, Scheduling, Attendance, Timesheets] - 2026-11-09

Human resources module under `/hr/**`. Full design, rules and tests: `documentation/hr/`.

### Added
- Employee registry linked (optionally) to staff logins, departments and positions, employment contracts with renewals, private employee documents, an HR audit trail and a read-only self-service profile.
- HR permission model: role defaults plus per-user grants managed by admins, with strict branch scope (`HrAccessService`).
- Multi-branch: assignment history, transfers, secondary and temporary assignments, extra branch scope per user, branch workforce headcount.
- Shift scheduling: templates, policies, validation (overlap, rest, length, weekly hours, availability), draft/publish with push notifications, coverage, availability and shift swaps; the existing rota keeps working.
- Attendance: idempotent clock events, breaks, late/early/overtime, corrections with second-person approval, policies, POS staff clock with personal clock PINs and lockout.
- Timesheets from attendance (draft → submitted → reviewed → approved → locked), effective-dated overtime rules, public holidays and overtime pre-authorisation.

### Migrations
- `V27__hr_foundation.sql` to `V31__hr_timesheets_overtime.sql`

### Verified
- Backend suite green at each phase; each migration booted on PostgreSQL by hand (see `documentation/hr/HR_IMPLEMENTATION_PROGRESS.md`).

---

## [HR - Leave] - 2026-11-12

### Added
- Leave types and effective-dated entitlements (per branch and employment type; none seeded), yearly balances with a ledger, pro-rating, monthly accrual and capped carry-over, HR adjustments.
- Leave requests counting working or calendar days (holidays, half days) with a configurable approval chain (reporting manager, branch manager, HR), required documents, cancellation and push notifications.
- Approved leave blocks scheduling, shows as `LEAVE` on timesheets and `ON_LEAVE` on the attendance board.
- Web: `/hr/leave`, `/hr/my-leave`, a Leave tab on employee profiles. Seed data for leave.

### Migrations
- `V32__hr_leave.sql`

### Verified
- Backend 384 tests; web HR suites 25/25; V32 on PostgreSQL 28/28 API checks.

---

## [HR - Payroll] - 2026-11-12

### Added
- Configurable deduction and employer-contribution rules (percent, fixed, progressive brackets, pre-tax, caps; no statutory rates seeded).
- Payroll runs per branch and period from contracts, locked timesheets (approved overtime, holiday/weekend/night premiums) and unpaid leave, with one-off adjustments; draft → calculated → approved by someone else → finalized → paid. A timesheet is paid in one run only.
- Web: `/hr/payroll`, `/hr/my-payslips`.

### Migrations
- `V33__hr_payroll.sql`

### Verified
- Backend 394 tests; web HR suites 30/30; V33 on PostgreSQL 31/31 API checks.

---

## [HR - Tips and Service Charge] - 2026-11-12

### Added
- Sharing rules (by hours, hours × position points, or equally; house share; minimum time; taxable) and pools per branch and period built from paid orders. Shares are exact to the cent, approved by a second person and paid through each person's home-branch payroll.
- Web: `/hr/tips`; shares on My Payslips.

### Migrations
- `V34__hr_tips_service_charge.sql`

### Verified
- Backend 402 tests; web HR suites 34/34; V34 on PostgreSQL 37/37 API checks.

---

## [HR - Recruitment] - 2026-11-12

### Added
- Vacancies, deduplicated candidates with private CVs, a hiring pipeline with stage history, interviews and trial shifts (interviewers record results), offers with pay visible only to those allowed, a one-step hire creating the employee, a draft contract and copied documents, and candidate anonymisation.
- Recruitment added to branch managers' default HR permissions (branch-scoped).
- Web: `/hr/recruitment`; "Interviews I run" on My Schedule.

### Migrations
- `V35__hr_recruitment.sql`

### Verified
- Backend 408 tests; web HR suites 38/38; V35 on PostgreSQL 36/36 API checks.

---

## [Fixes - POS, KDS, pickup board, guest QR ordering] - 2026-11-12

### Fixed
- POS orders now reach the KDS straight away. Realtime events (orders, tables, payments) were sent inside the
  transaction, so the KDS reloaded before the new order was committed and only saw it on the next 15-second poll.
  They are now sent after commit (`common/AfterCommit`).
- A table used for a dine-in order is no longer offered as free. Placing the order marked the table OCCUPIED but did
  not clear the 2-minute per-user table cache or publish `/topic/tables.status`; both now happen on commit. The POS
  greys out tables that are not AVAILABLE, clears the selected table after a successful order and reloads the
  tables. A table opened from the floor plan (`/pos?table=`) can still take another round.

### Changed
- `/orders-status` is now the pickup order board: pickup orders only, order numbers grouped by status in four
  columns like the KDS (Received, Accepted, Preparing, Ready to collect). `GET /orders/public/status` returns
  `{orderNumber, status, updatedAt}` only: no items, table, customer or full order id.
- Table QR page (`/scan/{token}`): guests enter a phone number (name optional) before the menu. No app or account is
  needed. The contact is remembered on that device and sent as `guestName` / `guestPhone`. It is no longer folded
  into the order notes, which the kitchen ticket and table tracking show. Staff see it on the order page.
- Guests can pay a whole order by Mobile Money (`POST .../orders/{orderId}/pay`), with their phone prefilled, as
  well as splitting the bill. Table tracking returns `total` and `amountPaid`, so paid orders show as paid.

### Migrations
- `V37__order_guest_contact.sql` (`orders.guest_name`, `orders.guest_phone`). V36 is on `feature/public-careers`:
  merge that branch first, or Flyway will refuse V36 on a database that already has V37.

### Verified
- Backend `mvn test`: 412 run, 0 failures (+4 in `GuestTableAndPickupBoardIntegrationTest`); web 83 passed; `tsc` clean.

---

## [Pickup paid before the kitchen] - 2026-11-12

### Changed
- Pickup orders are paid before the kitchen sees them. An unpaid pickup order stays PENDING ("awaiting payment"):
  it is not on the KDS or the pickup board, and staff cannot move it to CONFIRMED (409). A successful payment
  (cash at the POS, MoMo or card) confirms it, and that now publishes `/topic/orders.status`, so the KDS and the
  pickup board update at once.
- The pickup board starts at "In queue" (paid): columns In queue, Preparing, Ready to collect.
- The POS says "Take payment to send it to the kitchen" after creating a pickup order.
- `UnpaidPickupExpiryJob` cancels pickup orders still unpaid after `orders.unpaid-pickup.expire-after-minutes`
  (default 30) that have no payment waiting for approval, and releases their stock and promo codes.
- Dine-in is unchanged: it goes to the kitchen at once and is paid at the end.

### Verified
- Backend `mvn test`: 424 run, 0 failures (+3 pickup tests; KDS and status tests updated for the new rule);
  web 94 passed.

---

## [Dine-in payment safety: watch, alerts, walkouts] - 2026-11-12

Dine-in stays pay-at-the-end; guests are never prompted. Staff are told when to go to a table with the bill.

### Added
- Floor plan: each table shows what it owes and its state ("Served 25 min ago · not paid", "Asked for the bill");
  tables that need staff are outlined in red and listed at the top with the total owed. Refreshes every minute.
- Alerts for cashiers (floor staff) and managers when a table has been served and unpaid for 20 minutes, or asked
  for the bill 10 minutes ago and is still unpaid (`tables.payment-watch.*`). A dismissed alert stays hidden until
  the table's situation changes.
- Walkouts: a manager records that guests left without paying (reason, note). The unpaid orders are written off,
  the table is freed, and the loss is recorded with who took the orders and any phone the guests left. Audited.
- Walkout report (`/reports/walkouts`, CSV/PDF, Reports page), with losses by staff member.
- `orders.served_at` (set when the kitchen marks a dine-in order served) and `orders.updated_at` now updated on
  every save.

### Migrations
- `V38__table_walkouts.sql` (`table_walkouts`, `orders.walkout_id`, `orders.served_at`).

### Verified
- Backend `mvn test`: 426 run, 0 failures (+2 in `TablePaymentIntegrationTest`); web 97 passed; `tsc` clean.

---

## [Fixes - staff screen layout and payments] - 2026-11-12

### Fixed
- The phone bottom bar showed on every screen size (an inline `display: grid` overrode `lg:hidden`). On laptops it
  covered the bottom of pages: the POS "Send to kitchen" button and the last rows' Cancel buttons on Orders.
- Orders list: the action buttons and total stay on one line instead of wrapping under each other.
- Table drawer overflowed sideways: a long item list widened the drawer's grid. Drawers and modals now use a
  shrinkable single column, and the table drawer's lists truncate.
- Payments page: cash no longer asks for a MoMo number (cash takes an optional amount received and shows change; the
  phone is required for Mobile Money only, optional for card).
- Orders can be found by the number on the ticket (e.g. 7D63D7F5, with or without #) on the Payments page and in the
  POS "Pay existing" flow: `GET /orders/lookup?number=` (admin, manager, cashier; 404 if none, 409 if ambiguous).

### Verified
- Backend 427 tests, web 97, `tsc` clean; checked in a browser at 1366×768 and 390×844.

---

## [Fixes - pay once, receipts from anything on the ticket] - 2026-11-12

### Fixed
- An order could be paid any number of times. Staff payments now charge what is still owed and refuse an order that
  is already paid ("Order #7D63D7F5 has already been paid"). Before a new attempt, any attempt still waiting is
  checked with the provider: a late success is recorded (and the new attempt refused); otherwise it is closed as
  "Replaced by a new payment attempt", so only one payment can count.
- Cash is stored once, already received (it was saved as INITIATED and then updated, and some rows were left
  INITIATED). The reconciliation job no longer sends cash to the provider and closes any such row as failed.
- Receipt Center: one search for the order number (7D63D7F5), receipt number (RCP-20261112-2FE7D111), payment
  reference (CASH-DADB89D6) or a full order or payment id: `GET /payments/receipts/search?q=`.
- A malformed id in a URL is a 400 (was 500); an unsupported HTTP method is a 405 (was 500).

### Verified
- Backend 431 tests (+4 in `PaymentOnceIntegrationTest`), web 103.
