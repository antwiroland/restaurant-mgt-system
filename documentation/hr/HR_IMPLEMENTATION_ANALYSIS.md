# HR Implementation Analysis

Repository analysis performed on 2026-11-09, on branch `feature/restaurant-hr-management` (created from
`feature/operations-expansion` @ `35ef1ed`, which is 11 commits ahead of `main`).

Legend: **[V]** = verified in the repository, **[P]** = proposed for the HR module.

---

## 1. Existing system architecture

- **[V]** Monorepo: `backend/` (Spring Boot), `web/` (Next.js staff POS + public site), `mobile/` (Expo customer + rider app),
  `docker-compose.yml` (Postgres 16, Redis 7, Prometheus, Grafana, Zipkin), `loadtest/`.
- **[V]** Backend is a layered modular monolith in a single Maven module (`backend-core`), packaged by feature under
  `com.restaurantmanager.core.<feature>` (controller → service → Spring Data repository → JPA entity). DTOs are Java records,
  either in a `dto` sub-package or nested in the service.

## 2. Backend technologies

| Concern | Verified implementation |
|---|---|
| Language / framework | Java 17 target (JDK 21 installed locally), Spring Boot 3.3.5 |
| Persistence | Spring Data JPA, Hibernate, `ddl-auto: validate`, `open-in-view: false` |
| Migrations | Flyway, `backend/src/main/resources/db/migration/V1..V26` (sequential integer versions) |
| Database | PostgreSQL 16; UUID PKs generated in `@PrePersist`; `set_updated_at()` trigger per table |
| Auth | Stateless JWT (`JwtAuthFilter`, `JwtService`), BCrypt `PasswordEncoder`, ACCESS/REFRESH/OVERRIDE tokens |
| Authorization | `@EnableMethodSecurity` + `@PreAuthorize("hasAnyRole(...)")`; principal is `UserPrincipal(userId, role, branchId)` |
| Rate limiting | `RateLimitFilter` (auth 20/min, PIN 5/5 min, public 120/min) + Redis-backed login lockout (`SecurityGuardService`) |
| Errors | `ApiException(status, message)` → `GlobalExceptionHandler` → `ApiError` JSON |
| Pagination | `common.Pagination` (page/size params, `X-Total-*` headers, in-memory `slice`) |
| Audit | `audit_logs` table (`actor_id`, `action` enum `AuditAction`, `entity_type`, `entity_id`, JSONB `metadata`, `ip_address`) via `AuditService.log` |
| Scheduling | `@EnableScheduling`; jobs such as `PendingPaymentReconciliationJob`, `TableReservationJob` |
| Notifications | `notification/push` (Expo push, `PushNotificationService.notifyUsers` sends after commit), `email`, `sms`, `whatsapp` gateways |
| File storage | `media.MediaStorage` (local disk or Cloudinary). **Public**: `GET /media/**` is `permitAll` |
| Caching | Spring Cache, optional Redis (`app.cache.redis-enabled`) |
| Observability | Actuator, Micrometer/Prometheus, Brave/Zipkin, logstash encoder |
| API docs | springdoc-openapi (`/swagger-ui`) |
| Tests | JUnit 5 + MockMvc. `BaseIntegrationTest` uses H2 (PostgreSQL mode) with Hibernate `create-drop` and **Flyway disabled**; `BasePostgresIntegrationTest` uses Testcontainers Postgres with Flyway |

## 3. Frontend technologies (web)

- **[V]** Next.js 16.2 (App Router), React 19.2, Tailwind CSS 4, TypeScript, Vitest + Testing Library. No UI kit, no state
  library, no form library, no table library.
- **[V]** Staff routes under `web/src/app/(staff)/*` wrapped by `AppShell` (sidebar `NavSidebar`, bottom nav on phones).
- **[V]** Navigation single source of truth: `web/src/lib/navigation.ts` (`navGroups`, role-based filtering, `canAccessPath`).
- **[V]** API: `web/src/lib/apiClient.ts` (`apiRequest` → `/api/rm` proxy route in `app/api/rm`), session via
  `SessionProvider.useStaffSession()` → `authenticatedFetch` (auto refresh on 401).
- **[V]** Shared components: `ui.tsx` (`PageHeader`, `StatCard`, `EmptyState`, `ErrorState`, `StatusBadge`, `SectionHeader`),
  `Modal`, `Drawer`, `ConfirmModal`, `PinModal`, `Skeleton`, `Spinner`, `Icon`.
- **[V]** Existing workforce screen: `app/(staff)/schedule/page.tsx` (rota + time clock) with helpers in `features/workforce/time.ts`.

## 4. Database architecture (relevant tables)

| Table | Migration | Notes |
|---|---|---|
| `users` | V1, V5, V11, V19 | `name`, `email` (unique), `phone` (unique), `password_hash`, `role`, `pin_hash`, `pin_fail_count`, `pin_locked_until`, `branch_id` (single), `active` |
| `branches` | V5 | `code` unique, `name`, `active` |
| `cashier_shifts` | V5 | Cash-drawer sessions (opening/closing cash, variance) — **not** employee work shifts |
| `work_schedules` | V18 | Rota: `user_id`, `branch_id`, `starts_at`, `ends_at`, `position` (free text), `notes` |
| `time_entries` | V18 | Time clock: `user_id`, `branch_id`, `schedule_id`, `clock_in_at`, `clock_out_at`, `break_minutes`, `edited_by`, `edit_reason`; partial unique index = one open entry per user |
| `orders` | V3, V9, V13 | `branch_id`, `service_charge`, `tip_amount`, `tax_total` (numeric 12,2) |
| `billing_settings` | V13 | single row: `service_charge_percent`, `tips_enabled` |
| `payments`, `payment_refunds`, `payment_splits` | V4, V9, V14 | Customer payments only |
| `reconciliation_signoffs` | V9 | Daily cash reconciliation |
| `audit_logs` | V1 | Shared audit trail |
| `push_devices` | V25 | Expo push tokens per user |

**Verified absent:** employees, departments, positions, contracts, documents, leave, payroll, salary, tip pools,
service-charge distribution, recruitment, performance, training tables.

## 5. Existing user and employee model

- **[V]** There is no separate employee table. A staff member is a `users` row whose `role` is ADMIN, MANAGER, CASHIER or RIDER.
- **[V]** Staff accounts are created by ADMIN via `/auth/register` + `PATCH /users/{id}/role` (web `users` page).
- **[P]** HR adds `hr_employees` holding employment data, with an **optional, unique** `user_id` FK to `users`. Employees who never
  log in (cleaners, kitchen porters) have no user account; staff with logins are linked 1:1. `users` remains the only
  authentication identity, so no second auth system and no duplicated login identity is introduced.

## 6. Existing branch architecture

- **[V]** `branches` table; each user has at most one `branch_id`; orders, tables, schedules and time entries carry `branch_id`.
- **[V]** Branch scoping is ad hoc per service (`isBranchScoped` / `effectiveBranch` in `FinancialService`, `OrderService`,
  `KdsService`, `DeliveryService`): MANAGER/CASHIER *with* a branch are restricted to it, ADMIN and MANAGER *without* a branch
  see all branches.
- **[P]** HR uses a stricter central rule (`HrAccessService`): ADMIN → all branches; anyone else → only their own branch
  **unless** explicitly granted `hr.branch.all`. A non-admin without a branch gets no branch-specific HR data. This satisfies
  "branch managers must not automatically gain access to every branch".

## 7. Existing POS architecture

- **[V]** The web POS (`app/(staff)/pos/page.tsx`) runs under a normal staff JWT session (phone + password login). There is no
  per-employee PIN login for the POS. PINs (`users.pin_hash`, BCrypt) are used only by `POST /auth/pin/verify` to issue
  short-lived OVERRIDE tokens for void/refund/discount, and only MANAGER may verify.
- **[V]** Cash drawer sessions are `cashier_shifts` (`ShiftService`). Orders record the creating staff user. Payments
  support cash, card and mobile money (Paystack), with idempotency keys.
- **[V]** Offline support exists in the mobile customer app (Zustand queue) and for offline cash payments (V21). The web
  POS has no offline attendance queue.
- **[V]** Existing time clock: `POST /timeclock/clock-in|clock-out` (self, JWT), linking to the nearest schedule within ±90 min.
- **[P]** POS attendance: a "Staff clock" panel on the POS terminal calls a dedicated endpoint, authenticated by the
  terminal's staff session **plus** the employee's own HR clock PIN (separate hash on `hr_employees`, so a cleaner without
  a user account can clock in). It never creates a POS session or changes order attribution. Idempotency key per event.

## 8. Existing financial architecture

- **[V]** `FinancialService` handles refunds, voids, discounts (OVERRIDE token), reconciliation sign-off, cash summaries.
  Revenue figures come from `orders`/`payments`. `orders.tip_amount` and `orders.service_charge` are recorded per order.
- **[V]** No expense ledger, no general ledger, no accounting periods.
- **[P]** Payroll is a separate ledger (`hr_payroll_*`) and never writes to `payments`. Tips and service-charge collections
  are **read** from `orders` (by branch and date) and allocated in HR tables, with a uniqueness guard on the source period.

## 9. Existing notification infrastructure

- **[V]** Push via `PushNotificationService.notifyUsers(userIds, title, body, data)` (after-commit). Email (`email.provider`),
  SMS and WhatsApp gateways exist. No in-app notification inbox table. Staff alerts in web are WebSocket-driven (`StaffAlerts`).
- **[P]** HR notifications reuse `notifyUsers` for linked employees. An in-app HR inbox is deferred and recorded as an open item.

## 10. Existing permission system

- **[V]** Coarse roles only (enum `Role`), checked by `@PreAuthorize`. No permission table.
- **[P]** `HrPermission` enum (codes such as `hr.employee.view`) with **role defaults** (ADMIN: all; MANAGER: branch
  operational set without compensation) and **per-user grants** in `hr_user_permissions`, managed by ADMIN only. This
  lets an accountant (e.g. a CASHIER-role login) receive payroll rights without changing the global `Role` enum, which
  every existing controller depends on.

## 11. Integration opportunities

1. Link `hr_employees.user_id` to existing staff users (import action for current staff).
2. Extend `work_schedules`/`time_entries` (add publish state, source, idempotency, break table) instead of new parallel tables.
3. Reuse `audit_logs` with new `AuditAction` values for HR events.
4. Reuse `PushNotificationService` for schedule, leave and payslip notifications.
5. Read `orders.tip_amount` / `orders.service_charge` for tip and service-charge pools.
6. Read order totals by branch for labour-cost percentage.

## 12. Potential performance bottlenecks

- `WorkforceService.staff()` loads all users with `findAll()` and filters in memory — acceptable for small staff, HR
  directory must use DB-side paging (`Page<>`) and filters.
- `Pagination.slice` pages in memory; HR lists use Spring Data `Pageable` instead.
- Timesheet aggregation is done in Java per request; payroll must snapshot results rather than recompute repeatedly.
- POS endpoints must not wait on HR: attendance calls are separate HTTP requests with their own transactions.

## 13. Security risks

- Public media storage — HR documents need a private store and authorized download endpoint (IDOR protection).
- `MANAGER` without branch = global access in existing services; HR must not inherit this.
- PIN brute force — HR clock PIN needs the same fail-count/lockout as manager PINs plus rate limiting.
- Salary data exposure — compensation fields masked unless `hr.compensation.view` / payroll permissions.
- `WorkforceService` reads `${app.time-zone}` while `application.yml` defines `app.timezone` (default `Africa/Accra` still
  applies, so behaviour is correct today; noted, not changed).

## 14. Database migration considerations

- Next free version: **V27**. Migrations are additive; no destructive changes to existing tables.
- H2 tests do not run Flyway, so entities must be fully describable by JPA annotations (no reliance on DB sequences or
  triggers for correctness). Sequential numbers (employee numbers, contract numbers) use an `hr_sequences` row with a
  pessimistic lock, which works on both H2 and Postgres.
- Every new table needs a `BaseIntegrationTest.cleanupDb()` entry so tests remain isolated.
- `PostgresFlywayIntegrationTest` validates the migrated schema against entities (`ddl-auto: validate`) — run it for each migration.

## 15. Recommended HR architecture

- New package `com.restaurantmanager.core.hr` with sub-packages per domain (`access`, `employee`, `org`, `contract`,
  `document`, later `schedule`, `attendance`, `leave`, `payroll`, `tips`, ...).
- REST under `/hr/**` (the backend has no `/api` prefix; the web proxies `/api/rm/*`).
- Central `HrAccessService` for permission + branch checks, called from services (and `@PreAuthorize` for coarse gating).
- Monetary values: `BigDecimal`, `NUMERIC(14,2)`, explicit `RoundingMode`.
- Web: route group `app/(staff)/hr/*`, `lib/hrApi.ts`, nav group "Human Resources" filtered by HR permissions.

## 16. Files likely to require modification

| File | Reason |
|---|---|
| `backend/.../common/AuditAction.java` | New HR audit actions |
| `backend/.../workforce/*` | Extend rota/time clock (Phases 3–4) |
| `backend/src/test/.../BaseIntegrationTest.java` | Clean up HR tables between tests |
| `web/src/lib/navigation.ts`, `components/NavSidebar.tsx` | HR navigation with permission filtering |
| `web/src/lib/apiClient.ts` | Export the request helper for `hrApi.ts` |
| `web/src/app/(staff)/pos/page.tsx` | Staff clock panel (Phase 4) |
