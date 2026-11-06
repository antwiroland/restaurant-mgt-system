# Restaurant Manager — Feature Status

_Last updated: 2026-11-06 (branch `refactor/domain-packages-and-feature-completion`)_

This file is based on the source code, not just the docs. The first analysis (commit `1d93de5`) found 16 features that were stubbed or not wired into the real order and payment flow. All 16 have since been finished on this branch. Section 2 lists what was done for each, and which settings switch on the external providers.

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

## 2. Previously Partial, Now Completed ✅

External providers (Paystack, WhatsApp, SMS) ship with stub/log defaults. They only send real charges or messages once their environment variables are set (see `documentation/DEPLOYMENT_READINESS.md`). The live HTTP calls were tested against mocked servers, not against the real provider APIs.

| Feature | Now |
|---|---|
| **Paystack integration** | `HttpPaystackClient` (transaction initialize, verify, refund) runs when `PAYSTACK_MODE=live`; startup fails if live mode has no `sk_` key |
| **WhatsApp notifications** | WhatsApp Cloud API gateway (text or approved template), delivered off the request thread; `WHATSAPP_PROVIDER=meta` |
| **Refund / void / discount** | `/financial/*` changes real orders and payments: manual discount recalculates the total; void cancels unpaid orders, voids open payments and releases the table, promo use and points; refund goes through the provider (full or partial) and is recorded in `payment_refunds` |
| **Financial audit trail** | Written to `audit_logs`, queryable at `/financial/audit` |
| **Daily reconciliation & sign-off** | Computed from payments, refunds, voids, discounts and closed shifts; sign-off with cash variance persisted in `reconciliation_signoffs` |
| **Promo codes at checkout** | Applied at order creation (POS, mobile, QR, group) with atomic usage limits; released when an unpaid order is cancelled |
| **Loyalty accrual / redemption** | Points awarded automatically when an order is fully paid, redeemable at checkout (capped), clawed back on refunds; rules are configurable |
| **Buy-X-Get-Y offers** | `menu_offers` table, admin page (`/offers`), applied automatically to orders |
| **QR mobile sessions** | Persisted in `table_mobile_sessions`, linked from the web scan page and the mobile app, closed automatically when the table is released; staff see guest phones on the tables page |
| **Table QR PDF export** | Real A6 PDF card with the scan QR (`/qr/tables/{n}/pdf`), downloadable from the web QR page |
| **Customer OTP registration** | `/auth/otp/request`, `/auth/otp/verify`, `/auth/otp/login` with hashed codes, expiry, cooldown and lockout; Hubtel/Arkesel SMS; registration requires a verified phone by default |
| **Mobile real-time updates** | Shared STOMP client; order, table and group screens update live, with polling as a fallback |
| **Delivery orders** | Zones with fees, minimum order and ETA; `RIDER` role; dispatch board; rider page with live location; customer tracking with map link |
| **Load testing** | k6 scenarios in `loadtest/` (checkout, browse, realtime) replace the mocked endpoint |
| **Accessibility (web)** | Roadmap checklist complete: focus return, error linkage, nav labels, live regions |
| **Token migration** | Error pages use design tokens; no hard-coded hex colours remain |

## 3. Not Yet Implemented ❌

### Business features
- **Split payments** for group orders: separate MoMo payments per participant and tracking who has paid. This is listed in the user stories, but there is no backend or schema support
- **Inventory / stock management**: ingredients, stock levels, auto-86 when stock runs out, low-stock alerts
- **Push notifications** for the mobile app (Expo push or FCM) for order status changes
- **Third-party delivery integration** (e.g. Bolt Food or Glovo); in-house delivery zones, fees and riders are done
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
- **Live provider verification**: run Paystack, WhatsApp and SMS against their sandbox accounts before going live
- **Mobile app build/release**: EAS config, app icons and splash screens, store builds
- **End-to-end tests** (Playwright for web, Detox or Maestro for mobile)

### Technical debt
- ~~Rename the `phase8`/`phase9`/`phase10` packages and endpoints to domain names~~ ✅ Done: `/promotions`, `/loyalty`, `/qr`, `/notifications/whatsapp`, `/financial`, `/analytics`, `/security`
- ~~Merge the two financial-control paths into one real implementation~~ ✅ Done: `/financial/*`
- Have the default backend tests run against PostgreSQL (Testcontainers) instead of H2 `create-drop`

---

## 4. Suggested Priority

1. **Verify the providers in sandbox**: Paystack test keys, a WhatsApp Cloud API test number, and a Hubtel or Arkesel sender ID
2. **Split payments for group orders**
3. **Production hardening and CI/CD**: secrets, TLS, lock down admin tools, a pipeline running backend, web and mobile tests plus the Postgres Flyway check
4. **Push notifications** for the mobile app
5. **Tax / service charge, receipt printing, inventory**
