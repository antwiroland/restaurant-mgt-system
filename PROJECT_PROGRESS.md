# Project Progress

## Overall Status

| Phase | Title                        | Status      | Completed   |
|-------|------------------------------|-------------|-------------|
| 1     | Planning & Design            | Complete    | 2026-03-23  |
| 2     | Backend Core                 | Not Started | —           |
| 3     | Menu & Table Management      | Not Started | —           |
| 4     | Orders                       | Not Started | —           |
| 5     | Payments                     | Not Started | —           |
| 6     | Staff Web App (Next.js)      | Not Started | —           |
| 7     | Customer Mobile App (Expo)   | Not Started | —           |
| 8     | Ghana-Specific Features      | Not Started | —           |
| 9     | Financial Controls           | Not Started | —           |
| 10    | Analytics, Testing, Deploy   | Not Started | —           |

---

## Phase 1 — Planning & Design (Complete)

**Completed:** 2026-03-23

### Deliverables produced
- `docs/ROLES_AND_PERMISSIONS.md` — Full role matrix for Admin, Manager, Cashier, Customer
- `docs/DATABASE_SCHEMA.sql` — Full PostgreSQL schema (V1 migration)
- `docs/API_SPEC.md` — REST API contract for all 12 endpoint groups
- `docs/WEBSOCKET_EVENTS.md` — WebSocket (STOMP) and SSE event specification
- `docs/OFFLINE_STRATEGY.md` — Offline support scope, cache strategy, sync queue
- `documentation/DEVELOPMENT_GUIDE.md` — Master development guide with checkboxes
- `CHANGELOG.md` — Initialized
- `PROJECT_PROGRESS.md` — This file

### Key decisions made
- PostgreSQL with UUID primary keys throughout
- JWT (access + refresh) for auth; STOMP-over-SockJS for WebSocket
- SSE as fallback for mobile real-time updates
- Offline queue in mobile app (AsyncStorage/MMKV) with FIFO sync on reconnect
- Manager PIN produces a scoped, time-limited override token (5 min TTL)
- Price snapshots stored on `order_items` — menu price changes don't affect past orders
- Idempotency key on payments to prevent duplicate charges
- Paystack webhook is the primary payment confirmation path; polling is fallback

---

## Upcoming: Phase 2 — Backend Core

**Goal:** Spring Boot project with auth, roles, and PIN system.
See `documentation/DEVELOPMENT_GUIDE.md` Phase 2 for full task list.
