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

