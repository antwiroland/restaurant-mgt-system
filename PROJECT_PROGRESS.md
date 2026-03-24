# Project Progress

## Overall Status

| Phase | Title                      | Status      | Completed  |
|-------|----------------------------|-------------|------------|
| 1     | Planning and Design        | Complete    | 2026-03-23 |
| 2     | Backend Core               | Complete    | 2026-03-24 |
| 3     | Menu and Table Management  | Not Started | -          |
| 4     | Orders                     | Not Started | -          |
| 5     | Payments                   | Not Started | -          |
| 6     | Staff Web App (Next.js)    | Not Started | -          |
| 7     | Customer Mobile App (Expo) | Not Started | -          |
| 8     | Ghana-Specific Features    | Not Started | -          |
| 9     | Financial Controls         | Not Started | -          |
| 10    | Analytics, Testing, Deploy | Not Started | -          |

---

## Phase 1 - Planning and Design (Complete)

**Completed:** 2026-03-23

### Deliverables produced
- `docs/ROLES_AND_PERMISSIONS.md` - Full role matrix for Admin, Manager, Cashier, Customer
- `docs/DATABASE_SCHEMA.sql` - Full PostgreSQL schema (V1 migration)
- `docs/API_SPEC.md` - REST API contract for all 12 endpoint groups
- `docs/WEBSOCKET_EVENTS.md` - WebSocket (STOMP) and SSE event specification
- `docs/OFFLINE_STRATEGY.md` - Offline support scope, cache strategy, sync queue
- `documentation/DEVELOPMENT_GUIDE.md` - Master development guide with checkboxes
- `CHANGELOG.md` - Initialized
- `PROJECT_PROGRESS.md` - This file

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
- Spring Boot backend scaffold (`pom.xml`, profile configs, Flyway migration)
- JWT auth flows (`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`) with refresh token revocation
- Role-guarded endpoints for `ADMIN`, `MANAGER`, `CASHIER`, `CUSTOMER`
- Manager PIN setup and verification with lockout policy and scoped override tokens
- PIN-guarded financial action endpoints (`/financial/discount`, `/financial/void`, `/financial/refund`)
- Audit logging for login, failed PIN, PIN lockout, and role assignment
- Integration tests for all required Phase 2 scenarios (30 tests passing)
- `reports/phase-2-report.md`

### Notes
- Runtime configuration uses PostgreSQL and Flyway in application profiles.
- Test execution in this environment uses H2 in PostgreSQL compatibility mode because Docker/Testcontainers is not available.

---

## Upcoming: Phase 3 - Menu and Table Management

**Goal:** Staff can fully manage menu items and tables, while customers can browse.
See `documentation/DEVELOPMENT_GUIDE.md` Phase 3 for full task list.
