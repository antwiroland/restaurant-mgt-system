# Restaurant Manager — Feature Status

_Last analyzed: 2026-11-06 (commit `1d93de5`)_

This file is based on the source code, not just the docs. `documentation/PROJECT_PROGRESS.md` lists all 10 phases as "Complete", but several of those features exist only as standalone services, simulations or stubs. They are not wired into the real order/payment flow. Those gaps are listed under **Partially implemented** and **Not yet implemented**.

**Stack:** Spring Boot + PostgreSQL/Flyway + Redis (`backend/`) · Next.js staff console (`web/`) · Expo/React Native customer app (`mobile/`) · Docker Compose with Prometheus, Grafana and Zipkin (`docker/`).

Legend: ✅ Implemented & wired end-to-end · 🟡 Partial / stubbed / not integrated · ❌ Not implemented

---

## 1. Implemented ✅

### Authentication & Access Control
- JWT access and refresh tokens: register, login, refresh, logout, and refresh-token revocation (`auth/`)
- Role-based access with four roles: `ADMIN`, `MANAGER`, `CASHIER`, `CUSTOMER` (method security)
- Manager PIN setup and verification, with lockout and a scoped override token that expires after 5 minutes (`/auth/pin/verify`)
- Login brute-force limiting (Redis, with an in-memory fallback) and a request rate-limit filter
- Configurable CORS and WebSocket origin allow-lists
- Audit log for login, failed PIN, PIN lockout and role assignment, plus an audit query API (`/audit`)

### Users & Customers
- Admin user list, role assignment and PIN setup (`/users`)
- Customer profile (`/users/me`) and saved delivery addresses

### Multi-Branch
- Branch list, create and update (`branch/`), branch scoping for tables, orders and KDS, and a web `/branches` page

### Menu
- Category CRUD and menu item CRUD, availability toggle, and public browse/search/filter
- Modifier groups and options (size, spice, add-ons) with selection types. Price snapshots are stored on order items, along with the modifiers chosen (`order_item_modifiers`)
- Web `/menu` management page

### Tables & QR
- Table CRUD and status updates, with real-time table events over STOMP
- QR token generation and scan validation (`/tables/scan`), a dedicated QR management page (`/tables/qr`, `/tables/[id]/qr`)
- Running table bill: several orders can go on one occupied table. Table close and manager "reverse table session" are supported
- Tables are released automatically after payment settles the bill
- Public table bill page (`web /bill/[token]`) and public table tracking

### Reservations
- Create, list, change status and cancel, with conflict detection (overlapping time window on the same table)
- Guest reservations, guest lookup and guest cancel
- Scheduled job `TableReservationJob` marks tables reserved 15 minutes before a booking
- Web `/reservations` page and a mobile reservation screen

### Orders
- `PICKUP`, `DELIVERY` (address required) and `DINE_IN` order types
- Full status lifecycle, including delivery sub-states
- Cancellation. Orders that are confirmed or later need a manager override token
- Order history with role-aware visibility, date filters and pagination
- Pickup code generation and lookup (web `/orders/pickup`)
- Public dine-in ordering from a table QR without an account (`/orders/public/dine-in`)
- Public order status board (`web /orders-status`) and table status board (`web /tables-status`)
- Real-time order events (new order, status change) over STOMP

### Group Ordering
- Create or join a session by code, add items per participant, view the combined cart and finalize it into a single order linked to each participant
- Real-time group-cart events, a web `/group-ordering` page and a mobile `group/[code]` screen

### Kitchen Display System (KDS)
- `/kds/board` groups orders by status and shows item notes and modifiers, with a branch filter
- Web KDS page with WebSocket live updates

### Payments
- MoMo-first payment initiation with an idempotency key, plus get, verify (poll fallback) and retry
- Paystack webhook endpoint with signature verification and deduplication of repeated events
- Status mapping (`INITIATED`, `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`, `VOIDED`). A successful payment moves the order from `PENDING` to `CONFIRMED`
- Scheduled `PendingPaymentReconciliationJob` re-checks payments stuck in pending
- Receipts by payment ID or order ID (web `/receipts`, `/payments`)
- Real-time payment status and failure events

### Shifts
- Cashiers open and close shifts with opening and closing cash. Managers and admins can close cashier shifts. Active shift listing. Web `/shifts` page

### Promotions (management)
- Promo codes are stored in the database (`V7__promo_codes.sql`) and have management endpoints (`/promotions/codes`: CRUD and status)
- Validation endpoint covers min order, expiry, usage limit, and flat or percentage discounts
- Promo management UI on the web

### Loyalty (ledger)
- Loyalty balance and transaction tables (`V8__loyalty_tables.sql`), with accrue, redeem, balance and history endpoints
- The mobile profile shows the balance and history

### Analytics & Dashboard
- `/analytics/*`: revenue, top items, peak hours, average order value, overview
- Web dashboard with charts

### Staff Web App (Next.js)
- Persistent app shell with a role-aware sidebar (`AppShell`, `NavSidebar`)
- Design token system in `globals.css`
- `window.prompt`/`alert`/`confirm` have been replaced with modals (`Modal`, `ConfirmModal`, `CancelOrderModal`, `PinModal`, `Drawer`). There are no `window.*` dialogs left
- Loading skeletons and spinners, and a touch-friendly POS with categories
- Pages: login, dashboard, POS, orders and order detail, pickup, KDS, tables, table bill and QR, reservations, group ordering, menu, payments, receipts, financial, shifts, users, branches, audit, admin
- Backend proxy route `/api/rm/[...path]`

### Customer Mobile App (Expo)
- Login and register, a tab layout (menu, orders, profile), cart and checkout
- QR scan to link to a table, table tracking, order detail and tracking, reservations, group ordering, and a loyalty view
- Offline menu cache, an offline order queue that syncs on reconnect, and an offline banner
- MoMo payment with retry

### Infrastructure & Quality
- Flyway migrations V1–V8, with JPA `ddl-auto: validate`
- Docker Compose: Postgres, Redis, backend, pgAdmin, Zipkin, Prometheus, Grafana
- Actuator health and Prometheus metrics, Zipkin tracing, OpenAPI/Swagger UI
- Backend integration tests for each phase (H2), an optional Postgres + Flyway smoke profile (`-Ppostgres-it`), and Vitest suites for web and mobile

---

## 2. Partially Implemented 🟡

| Feature | Current state | What's missing |
|---|---|---|
| **Paystack integration** | `PaymentConfig` always registers `StubPaystackClient` | A real HTTP Paystack client chosen by profile or config, plus sandbox and live keys |
| **WhatsApp notifications** | `WhatsAppGateway` bean only **logs** the message | A real provider (WhatsApp Cloud API, Twilio, etc.), templates, and retry/queueing |
| **Financial actions: refund / void / discount** | `/financial/{action}` only **checks the override token** and returns `OK`. `/financial-controls/*` refund/void/discount run on objects built from the request, not on real payments or orders | Apply them to real `PaymentEntity`/`OrderEntity` (change status, call the Paystack refund API, release the table, recalculate totals), and persist the result |
| **Financial audit trail (Phase 9)** | Kept in an **in-memory list**, so it is lost on restart | Write to `audit_logs` through `AuditService`, using the `PAYMENT_REFUNDED`, `ORDER_VOIDED` and `DISCOUNT_APPLIED` actions |
| **Daily reconciliation & sign-off** | Summaries are built from entries sent by the client and kept in a `ConcurrentHashMap` | Compute totals from the payments and shifts tables, and store sign-offs in a new table |
| **Promo codes at checkout** | Mobile sends `promoCode` in the order payload, but `OrderCreateRequest` has **no promo field**, so the discount is never applied | Add the promo to the order DTO and entity, apply the discount to the order total, increment usage, and support promos on POS |
| **Loyalty accrual / redemption** | Endpoints exist but nothing calls them automatically | Accrue points when a payment succeeds, allow redemption at checkout, and add earn/burn rules configuration |
| **Buy-X-Get-Y offers** | Pure calculation service (`/promotions/offers/free-items`) | Persisted offer definitions, admin UI, and automatic application to orders |
| **QR mobile sessions (Phase 8)** | `MobileSessionService` uses an in-memory `HashMap` | Persist the sessions, or merge them with the table session logic that already works |
| **Table QR PDF export** | Generates a payload for the PDF | Check that it produces a real printable PDF and that the web QR page uses it |
| **Customer OTP registration** | Mobile `verifyOtp` compares against a local expected code, and the backend has **no OTP endpoints** | An SMS provider (e.g. Hubtel, Arkesel, Twilio), OTP issue and verify endpoints, and expiry and rate limiting |
| **Mobile real-time updates** | Order, table and group screens poll every 15 s | STOMP or SSE subscriptions (the SSE fallback is described in `WEBSOCKET_EVENTS.md`) |
| **Delivery orders** | Delivery type, address and sub-statuses exist | Rider assignment, rider app or view, delivery fee, zones, and live location |
| **Load testing** | `LoadTestService` calls mocked methods | Real load tests (k6, Gatling or JMeter) against a running stack |
| **Accessibility (web)** | Tokens and focus styles exist | The `REDESIGN_ROADMAP.md` accessibility checklist is not ticked: aria-live on KDS, focus traps, `aria-describedby` |
| **Token migration** | Done on most pages | `error.tsx` and `global-error.tsx` still use hard-coded hex colors |

---

## 3. Not Yet Implemented ❌

### Business features
- **Split payments** for group orders: separate MoMo payments per participant and tracking who has paid. This is listed in the user stories, but there is no backend or schema support
- **Inventory / stock management**: ingredients, stock levels, auto-86 when stock runs out, low-stock alerts
- **Push notifications** for the mobile app (Expo push or FCM) for order status changes
- **Delivery fee calculation / delivery zones / third-party delivery integration**
- **Tips / service charge and tax (VAT/NHIL/GETFund) configuration** on bills and receipts
- **Printable / thermal receipts** (ESC/POS) and kitchen ticket printing
- **Staff scheduling / timesheets** (beyond cash shifts)
- **Customer feedback / ratings** after an order
- **Menu item images upload** (media storage such as S3)
- **Exports**: CSV or PDF of sales, reconciliation and audit reports
- **Customer-facing web ordering for pickup/delivery** (the web customer pages only cover dine-in QR)
- **Password reset / forgot-password flow** and email verification
- **Multi-language / localization**

### Platform & operations
- **CI/CD pipeline**: the repo has no `.github/workflows` or any other CI config
- **Production deployment config**: reverse proxy, TLS, secret management (see `DEPLOYMENT_READINESS.md`)
- **Remove default secrets** (`JWT_SECRET`, Paystack key, Grafana and pgAdmin passwords) from checked-in config
- **Lock down `/actuator/prometheus`** and admin tools (pgAdmin, Grafana) in compose
- **Prometheus alert rules**, Grafana dashboards and runbooks
- **Distributed rate limiting**: `RateLimitFilter` keeps its counts in memory, so they are not shared across instances
- **Mobile app build/release**: EAS config, app icons and splash screens, store builds
- **End-to-end tests** (Playwright for web, Detox or Maestro for mobile)

### Technical debt
- ~~Rename the `phase8`/`phase9`/`phase10` packages and endpoints to domain names~~ ✅ Done: `/promotions`, `/loyalty`, `/qr`, `/notifications/whatsapp`, `/financial-controls`, `/analytics`, `/load-tests`, `/security`
- Merge the two financial-control paths (`/financial/*` and `/financial-controls/*`) into one real implementation
- Have the default backend tests run against PostgreSQL (Testcontainers) instead of H2 `create-drop`

---

## 4. Suggested Priority

1. **Make money flows real**: real Paystack client, real refund/void/discount on stored entities, and a persisted audit trail and reconciliation
2. **Wire promos and loyalty into orders and payments** (the mobile app already sends `promoCode`)
3. **OTP auth plus SMS provider**, and a **real WhatsApp gateway**
4. **Split payments for group orders**
5. **Production hardening and CI/CD**
6. Mobile real-time updates and push notifications, then delivery and inventory
