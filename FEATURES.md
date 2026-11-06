# Restaurant Manager — Feature Status

_Last updated: 2026-11-06 (branch `refactor/domain-packages-and-feature-completion`, commit `e4f2d19`)_

This file is based on the source code, not just the docs. The first analysis (commit `1d93de5`) found 16 features that were stubbed, simulated or not connected to the real order and payment flow. All 16 have been finished on this branch, and the `phase8`/`phase9`/`phase10` packages were renamed to domain names.

**Stack:** Spring Boot + PostgreSQL/Flyway + Redis (`backend/`) · Next.js staff console (`web/`) · Expo/React Native customer app (`mobile/`) · Docker Compose with Prometheus, Grafana and Zipkin (`docker/`) · k6 load tests (`loadtest/`).

Legend: ✅ Implemented · ⚠️ Implemented but not yet verified in a real environment · ❌ Not implemented

---

## 1. Implemented ✅

### Authentication & Access Control
- JWT access and refresh tokens: register, login, refresh, logout, and refresh-token revocation
- Five roles: `ADMIN`, `MANAGER`, `CASHIER`, `CUSTOMER`, `RIDER` (method security; riders only reach delivery endpoints and their own web page)
- **SMS OTP**: `/auth/otp/request`, `/auth/otp/verify`, `/auth/otp/login`
  - Codes are stored hashed, expire after 5 minutes, have a 60-second resend cooldown and a cap of 5 per hour, and lock after 5 wrong attempts
  - Customer sign-up requires a verified phone by default (`OTP_REQUIRED_FOR_REGISTRATION`)
  - Customers can also sign in with an SMS code instead of a password
- Manager PIN setup and verification, with lockout and a scoped override token that expires after 5 minutes (`/auth/pin/verify`)
- Login brute-force limiting (Redis, with an in-memory fallback) and a request rate-limit filter that also covers the OTP and public QR routes
- Configurable CORS and WebSocket origin allow-lists
- Audit log for logins, PIN events, role changes and all financial actions (`/audit`, `/financial/audit`)

### Users & Customers
- Admin user list, role assignment (including `RIDER`) and PIN setup (`/users`)
- Customer profile (`/users/me`), saved delivery addresses, and a phone-verified timestamp

### Multi-Branch
- Branch list, create and update, with branch scoping for tables, orders, KDS, financial actions and reconciliation. Web `/branches` page

### Menu
- Category and menu item CRUD, availability toggle, and public browse/search/filter
- Modifier groups and options (size, spice, add-ons). Price snapshots are stored on order items along with the chosen modifiers
- Web `/menu` management page

### Tables & QR
- Table CRUD and status updates, with real-time table events
- QR token generation and scan validation, with a QR studio (`/tables/qr`, `/tables/[id]/qr`)
- **Printable PDF table cards** (`/qr/tables/{number}/pdf`): an A6 card with a QR code for `PUBLIC_WEB_URL/scan/{token}`, downloadable from the web QR page
- **Persisted guest sessions** (`table_mobile_sessions`)
  - Linked when a guest opens the QR link on the web or scans it in the app
  - Closed automatically when the table is settled, closed or reversed
  - The tables page shows how many guest phones are ordering at each table
- Running table bill: several orders on one table, table close, and manager "reverse table session"
- Tables are released automatically once payments settle the bill
- Public table bill page (`/bill/[token]`) and public table tracking

### Reservations
- Create, list, change status and cancel, with conflict detection for overlapping bookings on the same table
- Guest reservations, guest lookup and guest cancel
- `TableReservationJob` marks tables reserved 15 minutes before a booking
- Web `/reservations` page and a mobile reservation screen

### Orders & Pricing
- `PICKUP`, `DELIVERY` and `DINE_IN` order types, with the full status lifecycle including `OUT_FOR_DELIVERY` and `DELIVERED`
- **Discounts applied automatically at order creation** (POS, mobile, table QR and group orders):
  1. Buy-X-Get-Y offers
  2. Promo code, with atomic usage limits
  3. Loyalty points (signed-in customers, up to `LOYALTY_MAX_REDEEM_PERCENT` of the order)
- Responses show the full breakdown: promo, offer, loyalty and manual discounts, delivery fee, and total discount
- Cancelling an unpaid order releases the promo use and returns redeemed points
- Cancellation: confirmed or later orders need a manager override token
- Order history with role-aware visibility, date filters and pagination
- Pickup codes and lookup (`/orders/pickup`)
- Public dine-in ordering from a table QR without an account
- Public order status board (`/orders-status`) and table status board (`/tables-status`)

### Group Ordering
- Create or join a session by code, add items per participant, view the combined cart, and finalize it into one order (with promo code and delivery zone)
- Live group-cart events, a web `/group-ordering` page and a mobile `group/[code]` screen

### Delivery
- **Delivery zones** (`/delivery/zones`) with fee, minimum order and ETA. Once zones exist, delivery orders must choose one, and the fee is added to the total
- **Rider dispatch**: staff assign or reassign riders on the web `/delivery` board, which also manages zones
- **Rider workflow** on the web `/rider` page:
  - Open-deliveries queue
  - Mark picked up (`READY` → `OUT_FOR_DELIVERY`) and delivered
  - Open the address in Maps and call the customer
  - Live location sharing from the browser's geolocation
- **Customer tracking** (`/delivery/orders/{id}/tracking`): rider name and phone, ETA and last location, with live updates on `/topic/orders.{id}.delivery`
- WhatsApp notifications when the order is on the way and when it is delivered
- POS and mobile checkout take the delivery address and zone and show the fee

### Kitchen Display System (KDS)
- `/kds/board` groups orders by status and shows item notes and modifiers, with a branch filter
- Web KDS page with WebSocket live updates and screen-reader live regions

### Payments
- MoMo-first payment initiation with an idempotency key, plus get, verify and retry
- **Paystack**: a live HTTP client (transaction initialize, verify, refund) when `PAYSTACK_MODE=live`; a stub client otherwise. Startup fails if live mode has no `sk_` key
- Paystack webhook with signature verification and deduplication of repeated events
- A successful payment moves the order from `PENDING` to `CONFIRMED`, refreshes table occupancy and awards loyalty points once the order is fully paid
- Partial and full refunds tracked per payment (`refunded_amount`, `payment_refunds`)
- `PendingPaymentReconciliationJob` re-checks payments stuck in pending
- Receipts by payment ID or order ID (web `/receipts`, `/payments`)
- Real-time payment events, including per-order topics

### Financial Controls (`/financial/*`)
- Every action needs a manager override token for the matching action, and is recorded in the audit log
- **Discount**: a flat or percentage manual discount that recalculates the order total. Blocked once a payment is in progress
- **Void**: cancels an unpaid order, voids open payments, releases the table, and returns the promo use and loyalty points
- **Refund**: full or partial, through the payment provider (cash refunds are recorded directly). Loyalty points are clawed back proportionally
- **Daily reconciliation**: gross sales, refunds, voids, discounts, net sales, expected cash, totals per payment method, and closed-shift cash
- **Sign-off**: saved once per date and branch, with cash variance (`reconciliation_signoffs`)
- Web `/financial` page with manager PIN approval, discount, void and refund forms, the reconciliation report, sign-off and the audit trail

### Shifts
- Cashiers open and close shifts with opening and closing cash. Managers and admins can close cashier shifts. Web `/shifts` page

### Promotions & Loyalty
- Promo code management (`/promotions/codes`) and a validation preview
- **Buy-X-Get-Y offers** (`menu_offers`): managed on the web `/offers` page and at `/promotions/offers`, with optional start and end dates
- Loyalty ledger (`EARN`, `REDEEM`, `ADJUST`), with automatic earning, redemption at checkout and clawback on refunds. Rules are set with `LOYALTY_*` variables
- Mobile: the profile shows the points balance and history, and checkout has a switch to spend points

### Notifications
- **WhatsApp**: Cloud API gateway (free text or an approved template), sent off the request thread, enabled with `WHATSAPP_PROVIDER=meta`. Otherwise messages are only logged
- **SMS** (used for OTP codes): Hubtel or Arkesel, chosen with `SMS_PROVIDER`. Otherwise messages are only logged
- Phone numbers are normalised to international format (default country code 233)

### Real-time
- STOMP over plain WebSocket at `/ws`
- Staff topics: new orders, status changes, tables, payments, deliveries
- Per-order topics for customers: `/topic/orders.{id}`, `.payments` and `.delivery`
- The mobile app uses one shared STOMP connection (order tracking, table tracking, group cart). Screens fall back to polling while it is disconnected

### Analytics & Dashboard
- `/analytics/*`: revenue, top items, peak hours, average order value, overview
- Web dashboard with charts

### Staff Web App (Next.js)
- Persistent app shell with a role-aware sidebar; riders only see their own page
- Design tokens in `globals.css`, with no hard-coded hex colours
- Modals and drawers instead of `window.prompt/alert/confirm`. Dialogs trap focus and return it when they close
- Accessibility checklist complete:
  - Errors linked to their inputs and announced
  - Collapsed nav links have accessible names
  - Live regions on the KDS
  - 44 px touch targets
- Loading skeletons, and a touch-friendly POS with categories, promo codes and delivery
- Pages: login, dashboard, POS, orders, pickup, KDS, tables, table bill and QR, reservations, group ordering, delivery, rider, menu, offers, payments, financial, receipts, shifts, users, branches, audit, admin
- Backend proxy route `/api/rm/[...path]`

### Customer Mobile App (Expo)
- Two-step sign-up with SMS verification and a resend timer, sign-in with a password or an SMS code, and guest mode
- Tab layout (menu, orders, profile), cart and checkout with:
  - Promo codes
  - Spending loyalty points
  - Delivery area picker with the fee
  - Savings summary
- QR scan to link a table (also registers a guest session), table tracking, order tracking with delivery details, reservations, group ordering
- Live updates over STOMP, with polling as a fallback
- Offline menu cache, an offline order queue that syncs on reconnect, and an offline banner
- MoMo payment with retry

### Infrastructure & Quality
- Flyway migrations V1–V12, with JPA `ddl-auto: validate`:
  - V9: order pricing, refunds, reconciliation and offers
  - V10: table sessions
  - V11: OTP
  - V12: delivery
- Docker Compose: Postgres, Redis, backend, pgAdmin, Zipkin, Prometheus, Grafana. Provider settings come from the environment, and k6 runs under the `loadtest` profile
- **k6 load tests** (`loadtest/`): checkout throughput, public browsing, and WebSocket fan-out, each with latency and error-rate thresholds
- Actuator health and Prometheus metrics, Zipkin tracing, OpenAPI/Swagger UI
- Tests:
  - 235 backend tests (H2), including integration suites for financial operations, order pricing, OTP, delivery and QR sessions, plus HTTP client tests against mock servers
  - Web: 23 Vitest tests and a production build
  - Mobile: 35 Vitest tests and a typecheck
  - An optional Postgres + Flyway smoke profile (`-Ppostgres-it`)

---

## 2. Implemented, Needs Verification ⚠️

| Item | What is still needed |
|---|---|
| **Migrations V9–V12 on PostgreSQL** | Tests run on H2 with schema generated from entities. Run `mvn -Ppostgres-it -Dtest=PostgresFlywayIntegrationTest test` (needs Docker) before deploying |
| **Paystack live mode** | Tested only against a mock HTTP server. Try it with `sk_test_` keys and a real MoMo test number, including the webhook and refunds |
| **WhatsApp Cloud API** | Tested only against a mock server. Needs a Meta test number and an approved template for messages outside the 24-hour window |
| **Hubtel / Arkesel SMS** | Not called for real yet. Needs a registered sender ID |
| **Web and mobile screens** | The new pages (financial, offers, delivery, rider, QR PDF download) and the mobile flows (OTP sign-up, live updates, delivery tracking) pass typechecks and tests but have not been tried in a browser or on a device |
| **Rider location sharing** | Uses browser geolocation, which needs HTTPS (or localhost) and permission on the rider's phone |
| **k6 scripts** | Written but not yet run against a live stack |

---

## 3. Not Yet Implemented ❌

### Business features
- **Split payments** for group orders: separate MoMo payments per participant, and tracking who has paid
- **Inventory and stock**: ingredients, stock levels, auto-86 when an item runs out, low-stock alerts
- **Push notifications** for the mobile app (Expo push or FCM)
- **Native rider app** (riders currently use the web `/rider` page) and an in-app map view (customers get a map link)
- **Third-party delivery integration** (e.g. Bolt Food, Glovo)
- **Tips, service charge and tax** (VAT/NHIL/GETFund) on bills and receipts
- **Printable / thermal receipts** (ESC/POS) and kitchen ticket printing
- **Staff scheduling and timesheets** (beyond cash shifts)
- **Customer feedback and ratings** after an order
- **Menu item image upload** (media storage such as S3)
- **Exports**: CSV or PDF of sales, reconciliation and audit reports
- **Customer-facing web ordering for pickup and delivery** (web customer pages only cover dine-in QR)
- **Password reset** for staff and email verification (customers can already sign in by SMS code)
- **Multi-language / localization**

### Platform & operations
- **CI/CD pipeline**: no `.github/workflows` or other CI config yet
- **Production deployment config**: reverse proxy, TLS, secret management (see `documentation/DEPLOYMENT_READINESS.md`)
- **Remove default secrets** (`JWT_SECRET`, Paystack key, Grafana and pgAdmin passwords) from checked-in config
- **Lock down `/actuator/prometheus`** and admin tools (pgAdmin, Grafana) in compose
- **Prometheus alert rules**, Grafana dashboards and runbooks
- **Distributed rate limiting**: `RateLimitFilter` keeps its counts in memory, so they are not shared across instances
- **Mobile app build and release**: EAS config, app icons and splash screens, store builds
- **End-to-end tests** (Playwright for web, Detox or Maestro for mobile)

### Technical debt
- ~~Rename the `phase8`/`phase9`/`phase10` packages and endpoints to domain names~~ ✅ `/promotions`, `/loyalty`, `/qr`, `/notifications/whatsapp`, `/financial`, `/analytics`, `/security`
- ~~Merge the two financial-control paths into one real implementation~~ ✅ `/financial/*`
- ~~Replace the mocked load-test endpoint~~ ✅ k6 in `loadtest/`
- Run the default backend tests on PostgreSQL (Testcontainers) instead of H2 `create-drop`
- The SSE fallback described in `documentation/WEBSOCKET_EVENTS.md` was never built; REST polling is used instead

---

## 4. Suggested Priority

1. **Verify before merging**: the Postgres Flyway check for V9–V12, then a click-through of the new web pages and mobile flows
2. **Sandbox the providers**: Paystack test keys, a WhatsApp Cloud API test number, a Hubtel or Arkesel sender ID
3. **CI/CD and production hardening**: secrets, TLS, lock down admin tools, and a pipeline running backend, web and mobile tests plus the Postgres check
4. **Split payments for group orders**
5. **Push notifications**, then tax and service charge, receipt printing, and inventory
