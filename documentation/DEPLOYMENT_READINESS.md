# Deployment Readiness

## Status

The project is suitable for local deployment, demos, and staging-style environments. It still needs configuration hardening before unmanaged internet-facing production use.

## Verified From This Workspace

_Last verified: 2026-11-07, branch `feature/operations-expansion`._

- Backend: full suite of 258 tests. One test, `AccountRecoveryIntegrationTest`, fails depending on timing (see Known Issues)
- Web: 39 Vitest tests, `tsc`, and `next build` (39 routes)
- Mobile: 48 Vitest tests and `tsc --noEmit`
- Running stack: backend on local H2, plus the web production build behind the `/api/rm` proxy, loaded with `scripts/dev/seed_local.py`. Checked over HTTP:
  - Every web route returns 200 (unknown routes return 404)
  - Each role sees the correct data: admin dashboard analytics, cashier KDS, tables and orders, rider delivery queue, inventory low stock, feedback summary, billing settings
  - End-to-end flows: POS order with options, notes and live quote → payment → kitchen stages; guest QR order → tracking; guest reservation → conflict check → cancel; menu photo upload (bytes match the original); QR PDF download; admin password reset → reset page → new password works
- Not yet done: a visual check in a real browser, and running the mobile app on a device

_HR module, last verified 2026-11-12 on `feature/hr-recruitment`:_

- Backend: 408 tests pass (H2). Web: HR suites 38/38, `tsc`, ESLint and `next build`; the 3 pre-existing `guest.test.tsx` failures remain
- Each HR migration `V27`–`V35` booted on a temporary PostgreSQL 16 with Flyway and Hibernate `validate`, then exercised through the API
- Local stack started on H2 with `seed_local.py` (HR data up to leave); HR screens not yet clicked through in a browser

## Runtime Topology

- `backend/`: Spring Boot API with PostgreSQL, Flyway, optional Redis caching
- `web/`: Next.js app containing:
  - The customer site: home, menu, dish pages, reservations, QR table ordering, running bill, order board, and the account pages for reset and verify links
  - The staff console
  - A backend proxy route under `/api/rm/**`, which passes binary files (uploads, PDFs) through unchanged
- `mobile/`: Expo/React Native customer app calling backend root routes directly
- `docker-compose.yml`: Postgres, Redis, backend, Zipkin, Prometheus, Grafana, pgAdmin

## Required Backend Environment Variables

Defined in [application.yml](../backend/src/main/resources/application.yml):

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `PAYSTACK_SECRET_KEY`
- `PAYSTACK_BASE_URL`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `CACHE_REDIS_ENABLED`
- `CORS_ALLOWED_ORIGINS`
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`
- `ZIPKIN_ENDPOINT`
- `TRACING_SAMPLING_PROBABILITY`

### Integrations (stub/log by default, so no real money or messages move until configured)

| Variable | Purpose |
|---|---|
| `PAYSTACK_MODE` | `stub` (default) or `live`. Live requires an `sk_test_...`/`sk_live_...` key and fails at startup otherwise |
| `PAYSTACK_CALLBACK_URL` | Optional redirect after Paystack checkout |
| `WHATSAPP_PROVIDER` | `log` (default) or `meta` (WhatsApp Cloud API) |
| `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | Required for `meta` |
| `WHATSAPP_TEMPLATE_NAME`, `WHATSAPP_TEMPLATE_LANGUAGE` | Approved template for business-initiated messages (outside the 24 h window) |
| `SMS_PROVIDER` | `log` (default), `hubtel` or `arkesel`. Used for OTP codes |
| `SMS_SENDER_ID`, `HUBTEL_CLIENT_ID`, `HUBTEL_CLIENT_SECRET`, `ARKESEL_API_KEY` | SMS provider credentials |
| `OTP_REQUIRED_FOR_REGISTRATION` | `true` by default. Customers must verify their phone by SMS before registering |
| `PUBLIC_WEB_URL`, `VENUE_NAME` | Used in printed table QR cards and SMS text |
| `LOYALTY_POINTS_PER_CEDI`, `LOYALTY_POINT_VALUE`, `LOYALTY_MAX_REDEEM_PERCENT`, `LOYALTY_AUTO_ACCRUE` | Loyalty rules |
| `EMAIL_PROVIDER` | `log` (default) or `smtp`. Used for password reset and email verification links |
| `SPRING_MAIL_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `EMAIL_FROM` | SMTP settings, required for `smtp` |
| `MEDIA_PROVIDER` | `local` (default, files under `MEDIA_LOCAL_DIR`) or `cloudinary` for menu photos |
| `MEDIA_PUBLIC_BASE_URL`, `MEDIA_MAX_FILE_SIZE` | Public URL prefix for local images; upload size limit (5 MB) |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_FOLDER` | Required for `cloudinary` |
| `ORDER_DEFAULT_BRANCH_CODE` | Branch code for customer and guest orders that don't get a branch from a table, the staff creator, `branchId` or the delivery zone. If unset and there is exactly one active branch, that branch is used; otherwise such orders form a shared queue that any branch can handle |
| `INVENTORY_BLOCK_WHEN_INSUFFICIENT` | `true` by default. Reject orders that would take an ingredient below zero |

With `EMAIL_PROVIDER=log`, password reset links are only written to the backend log. With `MEDIA_PROVIDER=local`, uploaded menu photos live on the backend container's disk, so mount a volume or use Cloudinary in production.

With `SMS_PROVIDER=log`, OTP codes are only written to the backend log. Set a real provider before you enable customer registration in production.

### HR (optional; defaults are business settings, not legal advice)

- `HR_DOCUMENTS_DIR` (default `hr-documents`): private store for employee and candidate documents. Must be persistent, shared between
  instances and **never** web-served. `docker-compose.yml` mounts the `hr_documents` volume at `/app/hr-documents`
- `HR_CONTRACT_EXPIRY_CRON` (default `0 15 1 * * *`, in `app.timezone`): contract expiry job
- Scheduling fallbacks: `HR_DEFAULT_MIN_REST_MINUTES`, `HR_DEFAULT_MAX_SHIFT_MINUTES`, `HR_DEFAULT_MAX_WEEKLY_MINUTES`
- Attendance fallbacks: `HR_DEFAULT_LATE_GRACE_MINUTES`, `HR_DEFAULT_EARLY_LEAVE_GRACE_MINUTES`, `HR_DEFAULT_EARLY_CLOCK_IN_MINUTES`,
  `HR_DEFAULT_UNSCHEDULED_CLOCK_IN`, `HR_DEFAULT_REQUIRE_CORRECTION_APPROVAL`, `HR_DEFAULT_MEAL_BREAK_PAID`, `HR_DEFAULT_REST_BREAK_PAID`,
  `HR_DEFAULT_ALLOW_SELF_CLOCK`, `HR_DEFAULT_MISSED_CLOCK_OUT_MINUTES`
- POS clock PINs: `HR_CLOCK_PIN_MAX_ATTEMPTS`, `HR_CLOCK_PIN_LOCKOUT_MINUTES`, `RATE_LIMIT_HR_CLOCK_REQUESTS`, `RATE_LIMIT_HR_CLOCK_WINDOW_SECONDS`
  (behind the web proxy all terminals share one bucket; size it for shift changes)
- Overtime fallbacks: `HR_DEFAULT_OT_DAILY_THRESHOLD_MINUTES`, `HR_DEFAULT_OT_WEEKLY_THRESHOLD_MINUTES`, `HR_DEFAULT_OT_DAILY_MULTIPLIER`,
  `HR_DEFAULT_OT_WEEKLY_MULTIPLIER`, `HR_DEFAULT_OT_HOLIDAY_MULTIPLIER`, `HR_DEFAULT_OT_REQUIRES_APPROVAL`
- Leave: `HR_LEAVE_NON_WORKING_DAYS` (default `SAT,SUN`; restaurants that trade every day may want it empty)

Before go-live, HR must configure in the app (nothing is seeded): leave entitlements, payroll deduction and contribution rules (income tax,
pension, etc.), and tip/service-charge sharing rules.

## Web Environment Variables

| Variable | When | Purpose |
|---|---|---|
| `RESTAURANT_MANAGER_BACKEND_URL` | runtime (server) | Backend base URL used by the `/api/rm` proxy and the customer pages rendered on the server (default `http://localhost:8080`) |
| `NEXT_PUBLIC_BACKEND_WS_URL` | **build time** | STOMP WebSocket URL for live KDS, order and table updates (default `ws://localhost:8080/ws`). Must be `wss://` behind HTTPS |
| `NEXT_PUBLIC_RESTAURANT_NAME`, `_TAGLINE`, `_ABOUT` | **build time** | Brand copy on customer pages and the staff console (default name "Hearth", a placeholder) |
| `NEXT_PUBLIC_RESTAURANT_ADDRESS`, `_PHONE`, `_EMAIL`, `_HOURS` | **build time** | Visit/contact details. `_HOURS` is pipe-separated, e.g. `Mon–Fri 11:00–22:00` and `Sat 10:00–23:00` joined with a pipe character. Anything not set is hidden; the site never shows placeholder contact details |
| `NEXT_PUBLIC_RESTAURANT_HERO_IMAGE` | **build time** | Homepage hero photo URL (otherwise the first menu photo, or a typographic hero) |
| `NEXT_PUBLIC_CURRENCY` | **build time** | Display currency (default `GHS`) |

`NEXT_PUBLIC_*` values are inlined by `next build`, so you have to rebuild after changing them. The mobile app reads the matching `EXPO_PUBLIC_RESTAURANT_NAME`, `EXPO_PUBLIC_RESTAURANT_TAGLINE` and `EXPO_PUBLIC_CURRENCY`.

## Local Test Environment (no Docker)

For demos and click-through testing on a machine without Docker, PostgreSQL or Redis:

```bash
# 1. Backend on an empty H2 database (data: backend/target/dev-db, log: backend/target/dev-backend.log)
RESET_DB=1 scripts/dev/run-backend-local.sh            # PORT=8082 for another port

# 2. Seed related test data through the real API (about 3 minutes; it waits out the auth rate limit)
python scripts/dev/seed_local.py                       # --base http://localhost:8082 if needed

# 3. Build and serve the web app against that backend
scripts/dev/run-web-local.sh                           # BACKEND_PORT=8082 if needed → http://localhost:3000

# 4. (Optional) Customer app in Expo Go on a running Android emulator (start it from Android Studio first)
scripts/dev/run-mobile-local.sh                        # BACKEND_PORT=8082 if needed; Metro on :19000
```

If the emulator shows the Expo Go home screen rather than the app, tap **customer-mobile** under "Recently opened". To regenerate the app icon and splash after a brand change, run `python mobile/scripts/generate-brand-assets.py`.

The seed creates **test accounts only**, all with the password `Passw0rd!`:

| Role | Name | Phone |
|---|---|---|
| Admin (owner) | Ama Mensah | +233200000001 |
| Manager (PIN `2468` for financial approvals) | Yaw Owusu | +233200000002 |
| Cashier | Kofi Boateng | +233200000003 |
| Cashier | Abena Asante | +233200000004 |
| Rider | Kwame Darko | +233200000005 |
| Rider | Esi Appiah | +233200000006 |
| Customers (mobile app) | 5 accounts | +233240000001 … +233240000005 |

Data covers:
- 2 branches (Osu with 12 tables; East Legon with 4)
- A 17-dish menu with options, and recipes linked to 9 ingredients (palm oil is low on purpose, to show the low-stock alert)
- Billing settings: 5% dine-in service charge, NHIL 2.5%, GETFund levy 2.5%, and VAT 15% charged on top of those levies; tips on
- 3 delivery zones with minimum orders
- Promo codes `WELCOME10`, `FEAST20` and the expired `EXPIRED5`, plus a "Kelewele 2 + 1" offer
- About 90 orders, with finished ones back-dated across 30 days so the dashboard and reports show trends. Live tickets are at every kitchen stage, with guest QR orders on table 7, one delivery out with a rider and one waiting for dispatch
- 6 reservations, 5 customer reviews (one low rating, to show the manager alert), a week of staff rota, an open cash shift and an open group-order session
- HR (`scripts/dev/seed_hr.py`, run by `seed_local.py`): 22 employees (staff logins imported plus people without logins), contracts and documents,
  branch moves, a permission grant, this week's published rota and next week's draft, availability and a swap request, clock PINs (`2580` for
  everyone, test data), people clocked in, a correction to approve, and leave in every state with test entitlements. Payroll, tips and
  recruitment are not seeded yet

How the seed works:
- **Payments** are confirmed the same way Paystack confirms them: a `charge.success` webhook, signed with the local development secret
- **Admin account:** there is no admin bootstrap, so the first account is promoted to admin with the H2 shell
- **History:** back-dating also uses the H2 shell
- **Re-running:** `--reset` empties every table except `users` (restart the backend afterwards so its in-memory caches clear)

**Never run the seed against a shared or production database.** It refuses to run when menu data already exists, unless you pass `--reset`.

## Known Issues (fix before production)

- ~~Orders from the mobile app had no branch~~ ✅ Fixed (see `ORDER_DEFAULT_BRANCH_CODE` below). With several branches, set a default branch, or send `branchId` from the apps, so pickup orders go to a specific branch instead of the shared queue
- ~~Two logins by the same user in the same second returned 500~~ ✅ Fixed: JWTs carry a random `jti` (`JwtService`)
- **HR:** see `documentation/hr/HR_IMPLEMENTATION_PROGRESS.md` → Outstanding Issues (no offline POS clocking, no payslip PDF, no public
  careers page, payments recorded but not sent, browser-local times on the schedule board)
- **Cash is not recorded as an offline payment**: every method goes through the payment provider and needs a payer phone number

## Production Hardening Required

Replace immediately:
- default `JWT_SECRET`
- default `PAYSTACK_SECRET_KEY`
- default Grafana admin password
- default pgAdmin password
- any permissive origin lists

Lock down:
- `CORS_ALLOWED_ORIGINS`
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`
- public access to admin/observability services

## Docker Compose Audit

The compose file is good for development, not production.

Strengths:
- healthchecks for Postgres and Redis
- persistent volumes
- backend observability stack included

Production gaps:
- hard-coded default credentials
- no reverse proxy or TLS termination
- pgAdmin exposed by default
- Grafana exposed with default credentials
- backend secrets supplied inline

## Database and Migration Readiness

Good:
- Flyway enabled in runtime
- JPA uses `ddl-auto: validate`
- promo codes are now persisted through `V7__promo_codes.sql`

Gap:
- default integration tests still run on H2 with `create-drop`, not PostgreSQL with Flyway

Recommendation:
- add a staging or CI path that runs migrations and smoke tests against PostgreSQL. On this machine the Testcontainers profile cannot
  start containers (Docker Engine 29 rejects docker-java's default API version; `DOCKER_API_VERSION=1.44` finds Docker but containers
  still fail), so HR migrations `V27`–`V35` were checked by booting the app on a temporary PostgreSQL instead
- the repository now includes a Docker-backed Maven profile for this path:
  `cd backend && mvn -q -Ppostgres-it -Dtest=PostgresFlywayIntegrationTest test`

## Security Readiness

Implemented:
- stateless JWT auth
- rate limiting
- manager override-token flow
- role-based method security
- configurable CORS and WebSocket origin settings

Remaining concerns:
- default secrets remain in checked-in examples
- `prometheus` actuator endpoint is publicly permitted
- local admin tools are published broadly in compose

## Observability Readiness

Available:
- Prometheus metrics
- Zipkin tracing
- Grafana datasource provisioning
- structured logging support

Missing:
- alert rules
- incident/runbook detail beyond basic setup
- retention and cost-control guidance

## Pre-launch Verification

These features are implemented and covered by automated tests, but have not yet been exercised against real services or devices.

| Item | What is still needed |
|---|---|
| **Migrations V9–V12 on PostgreSQL** | Tests run on H2 with schema generated from entities. Run `mvn -Ppostgres-it -Dtest=PostgresFlywayIntegrationTest test` (needs Docker) before deploying |
| **Paystack live mode** | Tested only against a mock HTTP server. Try it with `sk_test_` keys and a real MoMo test number, including the webhook and refunds |
| **WhatsApp Cloud API** | Tested only against a mock server. Needs a Meta test number and an approved template for messages outside the 24-hour window |
| **Hubtel / Arkesel SMS** | Not called for real yet. Needs a registered sender ID |
| **Web screens** | Every route renders and the main flows work over HTTP against a live, seeded backend (see "Verified" above). A visual check in a real browser (layout, responsive breakpoints, live WebSocket updates) is still needed |
| **Mobile flows** | OTP sign-up, the new menu home, live updates, delivery tracking, tips and split shares pass typechecks and tests, but have not been run on a device |
| **Rider location sharing** | Uses browser geolocation, which needs HTTPS (or localhost) and permission on the rider's phone |
| **k6 scripts** | Written but not yet run against a live stack |

## Release Checklist

1. Replace all default secrets and passwords.
2. Restrict allowed origins to deployed domains only.
3. Put backend/web behind HTTPS and a reverse proxy.
4. Disable or firewall pgAdmin and nonessential admin tools.
5. Run Flyway migrations against PostgreSQL in staging.
6. Verify web proxy and mobile base URLs against deployed hostnames.
7. Set the `NEXT_PUBLIC_*` restaurant details and the `wss://` WebSocket URL **before** `next build`.
8. Fix the remaining known issue above (same-second refresh-token collision). With more than one branch, set `ORDER_DEFAULT_BRANCH_CODE`.
9. Use the `scripts/dev/` seed data for local and staging testing only. Never load it into production, and delete any seeded test accounts from shared environments.

## Conclusion

- Backend core: strong
- Web app: good. The redesign is complete, every route builds and renders, and flows were checked against a live, seeded backend; a visual browser check is still needed
- Mobile app: good
- Documentation accuracy: acceptable after sync, but still requires maintenance discipline
- Production hardening: incomplete

Overall: ready for local and staging-style deployment, not ready for direct public production without environment and security hardening.
