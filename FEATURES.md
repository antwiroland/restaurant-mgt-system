# Restaurant Manager — Feature Status

_Last updated: 2026-11-07 (branch `feature/operations-expansion`)_

This file is based on the source code, not just the docs. The first analysis (commit `1d93de5`) found 16 features that were stubbed, simulated or not connected to the real order and payment flow. All 16 have been finished on this branch, and the `phase8`/`phase9`/`phase10` packages were renamed to domain names.

**Stack:** Spring Boot + PostgreSQL/Flyway + Redis (`backend/`) · Next.js staff console (`web/`) · Expo/React Native customer app (`mobile/`) · Docker Compose with Prometheus, Grafana and Zipkin (`docker/`) · k6 load tests (`loadtest/`).

Legend: ✅ Implemented · ❌ Not implemented

Pre-launch checks (sandbox provider tests, the Postgres migration check, device testing) are listed in `documentation/DEPLOYMENT_READINESS.md`.

---

## 1. Implemented ✅

### Authentication & Access Control
- JWT access and refresh tokens: register, login, refresh, logout, and refresh-token revocation
- Six roles: `ADMIN`, `MANAGER`, `CASHIER`, `CUSTOMER`, `RIDER`, `HR` (method security; riders only reach delivery endpoints and their own web page; HR officers only reach the HR module)
- **SMS OTP**: `/auth/otp/request`, `/auth/otp/verify`, `/auth/otp/login`
  - Codes are stored hashed, expire after 5 minutes, have a 60-second resend cooldown and a cap of 5 per hour, and lock after 5 wrong attempts
  - Customer sign-up requires a verified phone by default (`OTP_REQUIRED_FOR_REGISTRATION`)
  - Customers can also sign in with an SMS code instead of a password
- Manager PIN setup and verification, with lockout and a scoped override token that expires after 5 minutes (`/auth/pin/verify`)
- Login brute-force limiting (Redis, with an in-memory fallback) and a request rate-limit filter that also covers the OTP and public QR routes
- Configurable CORS and WebSocket origin allow-lists
- Audit log for logins, PIN events, role changes and all financial actions (`/audit`, `/financial/audit`)
- **Password reset and email verification**:
  - `/auth/password/forgot` sends a single-use reset link by email, or by SMS when the account has no email; the reply is the same whether or not the account exists
  - `/auth/password/reset` and `/auth/password/change` sign the user out everywhere
  - Admins can send a reset link from the web Users page
  - Web pages: `/forgot-password`, `/reset-password`, `/verify-email` and `/account`
  - Mobile "Forgot password?" screen: request a link, then open it or paste it into the app to set a new password

### Users & Customers
- Admin user list, role assignment (including `RIDER`) and PIN setup (`/users`)
- Customer profile (`/users/me`), saved delivery addresses, and a phone-verified timestamp

### Multi-Branch
- Branch list, create and update, with branch scoping for tables, orders, KDS, financial actions and reconciliation. Web `/branches` page

### Menu
- Category and menu item CRUD, availability toggle, and public browse/search/filter
- Modifier groups and options (size, spice, add-ons). Price snapshots are stored on order items along with the chosen modifiers
- Web `/menu` management page
- **Menu item photos**: JPEG, PNG or WebP up to 5 MB, stored locally or on Cloudinary (`MEDIA_PROVIDER`), uploaded from the menu page

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
- **A table with an open order is not offered for a new one.** Placing a dine-in order marks it occupied, clears the table caches on commit and publishes `/topic/tables.status`. The POS greys out tables that are not free and clears the selected table after an order (a table opened from the floor plan, `/pos?table=`, can still take another round)
- **Dine-in payment watch** (`GET /tables/payment-watch`): what each table owes, when it was served (`orders.served_at`) and whether the bill was asked for
  - Floor plan: each table shows what it owes and its state ("Served 25 min ago · not paid"); tables that need staff are outlined and listed at the top
  - Alerts for cashiers and managers when a table was served and unpaid for 20 minutes, or asked for the bill 10 minutes ago (`tables.payment-watch.*`). Guests are never prompted
- **Walkouts** (`POST /tables/{id}/walkout`, manager): writes off the unpaid orders, frees the table and records the amount, reason, who took the orders and the guests' phone; audited (`WALKOUT_RECORDED`) and reported (`/reports/walkouts`, CSV/PDF with losses by staff member)
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
- **Pickup is paid before it reaches the kitchen.** An unpaid pickup order stays `PENDING` ("awaiting payment"), off the KDS and the pickup board, and staff cannot confirm it. A successful payment confirms it. `UnpaidPickupExpiryJob` cancels pickups still unpaid after 30 minutes (`orders.unpaid-pickup.expire-after-minutes`), releasing stock and promo codes
- **Order lookup by the number on the ticket** (`GET /orders/lookup?number=7D63D7F5`): the first 8 characters of the id, with or without `#`; a full id also works
- Public pickup board (`/orders-status`: paid pickup orders, numbers only, grouped In queue / Preparing / Ready to collect) and table status board (`/tables-status`)

### Billing: Service Charge, Tax and Tips
- Service charge percentage, optionally on dine-in only
- Tax rates such as NHIL, GETFund and VAT. Compound taxes (VAT) are charged on the bill plus the levies. Menu prices can include tax or have it added on top, and the delivery fee can be taxed or not
- Tips on POS and mobile checkout when enabled. Tips are never taxed
- Orders and receipts show each tax line, the service charge and the tip
- Web `/billing` page (admins edit; managers can view)

### Split Payments
- Split a bill equally (2–20 shares) or, for group orders, by what each participant ordered, with fees and taxes shared proportionally
- Each share is paid separately; the order is confirmed once every share is paid
- Web: split-bill panel on the order detail page, with cash, card or MoMo per share
- Mobile: group participants see who has paid and pay their own share by MoMo. Everyone who joined the group can open the placed order (from the group screen or their Orders tab); only the person who placed it can split, change or cancel it
- Web table QR page: dine-in guests (no account) split the bill equally and pay their share by MoMo. Access is limited to orders placed at that table that are still open

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
- `/kds/board` groups orders by status and shows item notes and modifiers, with a branch filter. Unpaid pickup orders are left off until paid
- Web KDS page with WebSocket live updates and screen-reader live regions. Order, table and payment events are sent after the database commit (`common/AfterCommit`), so a refresh triggered by an event always sees the new order
- Item-level "ready" (`PATCH /orders/{id}/items/{itemId}/ready`)

### Payments
- MoMo-first payment initiation with an idempotency key, plus get, verify and retry
- **Paystack**: a live HTTP client (transaction initialize, verify, refund) when `PAYSTACK_MODE=live`; a stub client otherwise. Startup fails if live mode has no `sk_` key
- Paystack webhook with signature verification and deduplication of repeated events
- A successful payment moves the order from `PENDING` to `CONFIRMED`, refreshes table occupancy and awards loyalty points once the order is fully paid
- Full and part-amount refunds tracked per payment (`refunded_amount`, `payment_refunds`)
- **Cash at the counter** (no provider round-trip): recorded as received in one write, with amount tendered and change due
- **An order is paid once.** Staff payments charge what is still owed and refuse an order already paid. Before a new attempt, any attempt still waiting is checked with the provider: a late success is recorded (and the new attempt refused), otherwise it is closed as "Replaced by a new payment attempt"
- `PendingPaymentReconciliationJob` re-checks payments stuck in pending (never cash: a cash row not recorded as received is closed as failed)
- **Receipts from anything on the ticket** (`GET /payments/receipts/search?q=`): order number (7D63D7F5), receipt number (RCP-20261112-2FE7D111), payment reference (CASH-DADB89D6) or a full order or payment id. Web `/receipts` (one search box, `?order=` deep link) and `/payments` (order number, method-specific fields, a readable result card with the change to give back)
- Real-time payment events, including per-order topics

### Financial Controls (`/financial/*`)
- Every action needs a manager override token for the matching action, and is recorded in the audit log
- **Discount**: a flat or percentage manual discount that recalculates the order total. Blocked once a payment is in progress
- **Void**: cancels an unpaid order, voids open payments, releases the table, and returns the promo use and loyalty points
- **Refund**: full or part-amount, through the payment provider (cash refunds are recorded directly). Loyalty points are clawed back proportionally
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

### Inventory
- Ingredients with units, cost, low-stock level and a stock ledger (restock, used by order, order cancelled, waste, stock count)
- Recipes per menu item. Orders deduct stock and cancellations return it
- Items are taken off the menu automatically when an ingredient runs out, and come back when restocked
- Optionally reject orders that would take stock below zero (`INVENTORY_BLOCK_WHEN_INSUFFICIENT`)
- Low-stock alerts on `/topic/inventory.alerts`, shown live to managers in the web app
- Web `/inventory` page, and a recipe editor on the menu page

### Customer Feedback
- Customers rate a completed or delivered order once (overall, food, service or delivery, and a comment); dine-in guests rate from the web table QR page
- Managers see averages, the rating spread and comments, and can reply. Ratings of 2 or lower raise a live alert
- Web `/feedback` page; the order detail page shows the rating. Mobile: rate from the order screen and see the reply

### Staff Scheduling and Timesheets
- Weekly rota with overlap checks; staff clock in and out (with breaks)
- Timesheets show worked vs scheduled hours, overtime (over 8 h a day), late arrivals (over 5 minutes) and missed shifts. Managers can correct entries with a reason
- Web `/schedule` page for all staff roles

### Report Exports
- CSV or PDF downloads of sales, daily reconciliation, the audit trail and timesheets (`/reports/*`)
- Web `/reports` page

### Notifications
- **WhatsApp**: Cloud API gateway (free text or an approved template), sent off the request thread, enabled with `WHATSAPP_PROVIDER=meta`. Otherwise messages are only logged
- **SMS** (used for OTP codes): Hubtel or Arkesel, chosen with `SMS_PROVIDER`. Otherwise messages are only logged
- **Email** (password reset and verification links): SMTP with `EMAIL_PROVIDER=smtp`. Otherwise messages are only logged
- Phone numbers are normalised to international format (default country code 233)

### Real-time
- STOMP over plain WebSocket at `/ws`, **signed in with the access token on CONNECT** and with every subscription checked by role and ownership (see `documentation/WEBSOCKET_EVENTS.md`); public screens use only public topics
- Staff topics: new orders, status changes, tables, payments, deliveries
- Per-order topics for customers: `/topic/orders.{id}`, `.payments` and `.delivery`
- The mobile app uses one shared STOMP connection (order tracking, table tracking, group cart). Screens fall back to polling while it is disconnected

### Analytics & Dashboard
- `/analytics/*`: revenue, top items, peak hours, average order value, overview
- Web dashboard with charts

### Design system ("Hearth")
- Shared tokens for web (`web/src/app/globals.css`) and mobile (`mobile/src/theme.ts`): ivory and cream surfaces, espresso ink and dark surfaces, terracotta for actions, muted gold for highlights, sage for success. Order and table status colours are the same on every screen
- Type: Manrope for the interface, Fraunces for headings on customer pages, IBM Plex Mono for order numbers
- Components: icon set (`components/Icon.tsx`), `PageHeader`, `StatCard`, `EmptyState`, `ErrorState`, `StatusBadge`, data table, chips, segmented controls, skeletons, modal/bottom sheet, drawer, image uploader
- Chart colours checked for colour-blind separation; every chart has hover values, and the revenue chart can be shown as a table
- Restaurant name, tagline, address, phone, hours and hero photo come from `NEXT_PUBLIC_RESTAURANT_*` (web) and `EXPO_PUBLIC_RESTAURANT_*` (mobile). Anything not set is left off the page instead of showing placeholder data

### Customer Web (Next.js, no sign-in)
- Homepage (`/`) built from the live menu: dishes available today, categories, how table ordering works, reservations, about and visit sections
- Menu (`/explore`) with search, category filter, sorting, an "available now" filter, grid and list views, and filters kept in the URL. Dish page (`/explore/[id]`) shows modifier groups with their prices
- Reservations (`/reserve`): book by date, time slot, party size and table; manage or cancel a booking by phone number
- QR table ordering (`/scan/[token]`), designed for phones, with no app or account: the guest enters a phone number (name optional) before the menu, then orders with options in a bottom sheet, a floating cart and kitchen notes, follows a live progress tracker for each order, calls a waiter or asks for the bill, pays the whole order by Mobile Money (phone prefilled) or their share of a split bill, and rates a finished order. The phone is stored on the order (`guest_name`, `guest_phone`) for staff, never on public views
- Running bill (`/bill/[token]`) and a dark display-screen pickup board (`/orders-status`)
- Account pages for the links the backend sends: `/forgot-password`, `/reset-password`, `/verify-email`

### Staff Web App (Next.js)
- App shell with a dark sidebar, collapsible icon rail and a phone bottom bar. What each role sees matches the backend permissions:
  - Cashiers go straight to the POS and don't see owner analytics or admin pages
  - Riders only see their deliveries
  - Users is admin-only
- The shell handles loading, sign-in and "not available for your role" for every page. The API still enforces access
- Owner dashboard: live tiles (new, cooking, ready, tables in use), KPIs, revenue trend with hover values, best sellers, payment and channel mix, busiest hours, order outcomes, recent orders, and a branch table when there is more than one branch
- POS with menu and ticket side by side:
  - Option picker that enforces required groups
  - Kitchen notes per item and for the whole order
  - Pricing from `/orders/quote` (offers, promo, delivery fee, service charge, tax lines)
  - Table picker as a grid of buttons; `/pos?table=` preselects the table
  - Keyboard search (`/`, then Enter adds the top result)
  - Separate payment step
- Kitchen display: dark high-contrast board, waiting timers that tick, late tickets highlighted, item notes and options shown, full-screen mode, swipe to advance
- Orders: status tabs with counts, channel and date filters, table view on desktop and cards on phones
- Floor plan with status counts, seat dots and running totals per table, plus "Take order" and "View bill" shortcuts
- Menu builder with dish photo upload (drag and drop, replace, remove) and an availability switch
- Staff page: admins can send a password-reset link (not to themselves); My Account for changing your own password
- Menu items have a "Photo & recipe" drawer that links ingredients to stock
- Live manager alerts for low stock, items taken off the menu, and low ratings
- Modals and drawers instead of `window.prompt/alert/confirm`. Dialogs trap focus and return focus when they close; errors are linked to their inputs and announced; 44 px touch targets
- Pages: login, dashboard, POS, orders, pickup, KDS, tables, table bill and QR, reservations, group ordering, delivery, rider, menu, offers, payments, financial, receipts, shifts, users, branches, audit, admin, billing, inventory, schedule, feedback, reports, account
- Backend proxy route `/api/rm/[...path]` passes binary files through unchanged (image uploads, QR PDFs and PNGs)

### Customer Mobile App (Expo)
- Two-step sign-up with SMS verification and a resend timer, sign-in with a password or an SMS code, and guest mode
- Tab layout (menu, orders, profile), cart and checkout with:
  - Promo codes
  - Spending loyalty points
  - Delivery area picker with the fee
  - Savings summary
  - Tip choice (5/10/15% or a custom amount) and a note on service charge and taxes
- Itemised bill on the order screen, rating a finished order, and paying your own share of a split group bill
- QR scan to link a table (also registers a guest session), table tracking, order tracking with delivery details, reservations, group ordering
- Live updates over STOMP, with polling as a fallback
- Offline menu cache, an offline order queue that syncs on reconnect, and an offline banner
- MoMo payment with retry

### Infrastructure & Quality
- Flyway migrations V1–V19, with JPA `ddl-auto: validate`:
  - V9: order pricing, refunds, reconciliation and offers
  - V10: table sessions
  - V11: OTP
  - V12: delivery
  - V13–V19: billing and tax, payment splits, inventory, menu images, feedback, scheduling and timesheets, account recovery
- Docker Compose: Postgres, Redis, backend, pgAdmin, Zipkin, Prometheus, Grafana. Provider settings come from the environment, and k6 runs under the `loadtest` profile
- **k6 load tests** (`loadtest/`): checkout throughput, public browsing, and WebSocket fan-out, each with latency and error-rate thresholds
- Actuator health and Prometheus metrics, Zipkin tracing, OpenAPI/Swagger UI
- Tests:
  - 258 backend tests (H2), including integration suites for financial operations, order pricing, OTP, delivery, QR sessions, billing, split payments, inventory, feedback, reports, workforce and account recovery, plus HTTP client tests against mock servers
  - Web: 39 Vitest tests and a production build (39 routes)
  - Mobile: 48 Vitest tests and a typecheck
  - An optional Postgres + Flyway smoke profile (`-Ppostgres-it`)
- **Local development without Docker** (`scripts/dev/`):
  - `run-backend-local.sh` runs the backend on an H2 file database. It needs no Postgres or Redis; payments use the Paystack stub, and SMS, WhatsApp and email are only logged
  - `seed_local.py` loads related test data through the real API:
    - 6 staff accounts covering every role, plus 5 customers
    - 2 branches, 16 tables, a 17-dish menu with options, and recipes linked to stock
    - Billing settings: 5% service charge, NHIL, GETFund levy and VAT
    - 3 delivery zones, promo codes and a buy-X-get-Y offer
    - About 90 orders with 30 days of history: kitchen tickets at every stage, deliveries assigned to riders, customer reviews
    - Reservations, a week of staff rota, an open cash shift and an open group-order session
  - `run-web-local.sh` builds the web app for a given backend port and serves it
  - Steps are in `documentation/DEPLOYMENT_READINESS.md` → "Local test environment"

---

## 2. Not Yet Implemented ❌

### Business features
- **Native rider app** (riders currently use the web `/rider` page) and an in-app map view (customers get a map link)
- **Third-party delivery integration** (e.g. Bolt Food, Glovo)
- **Prepayment for risky dine-in tables** (a second QR order while the first is unpaid above a limit waits for a waiter): proposed, not built
- **Refunding duplicate payments taken before the pay-once fix** is a manual step (Financial → Refund)

### Known issues
- None open at the moment (WebSocket sign-in was added on 2026-11-12)

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

## 3. Suggested Priority

1. **Verify before merging**: the Postgres Flyway check for V9–V19, then a click-through of the web and mobile flows using the seeded local environment (`scripts/dev/`)
2. **Payroll follow-ups**: provider-specific bank and Mobile Money file formats once a bank or provider is chosen
3. **Sandbox the providers**: Paystack test keys, a WhatsApp Cloud API test number, a Hubtel or Arkesel sender ID, an SMTP account, and Cloudinary (or a mounted volume for local images)
4. **CI/CD and production hardening**: secrets, TLS, lock down admin tools, and a pipeline running backend, web and mobile tests plus the Postgres check
5. **Native rider app** and third-party delivery integration
