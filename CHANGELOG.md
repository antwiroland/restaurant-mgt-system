# Changelog

All notable changes to this project will be documented here.
Format: [Phase N — Title] followed by Added / Changed / Decisions.

---

## [Phase 1 — Planning & Design] — 2026-03-23

### Added
- `docs/ROLES_AND_PERMISSIONS.md` — Permission matrix for all four roles
- `docs/DATABASE_SCHEMA.sql` — Full PostgreSQL V1 migration (17 tables, enums, indexes, triggers)
- `docs/API_SPEC.md` — REST API spec across 12 endpoint groups (auth, users, menu, tables, reservations, orders, group orders, payments, financial controls, loyalty, analytics, audit log)
- `docs/WEBSOCKET_EVENTS.md` — STOMP WebSocket topics and SSE fallback specification
- `docs/OFFLINE_STRATEGY.md` — Offline scope, cache durations, sync queue design
- `documentation/DEVELOPMENT_GUIDE.md` — Master guide with 10 phases and checkbox task lists
- `PROJECT_PROGRESS.md` — Phase progress tracker
- `CHANGELOG.md` — This file

### Decisions
- UUID primary keys on all tables
- Price snapshot on `order_items` to freeze price at order time
- Manager PIN → scoped override token pattern (5-minute TTL, action-type bound)
- Idempotency key on `payments` to prevent duplicate Paystack charges
- Paystack webhook is authoritative for payment status; client-side polling is fallback only
- Offline queue (mobile) uses FIFO processing with 3-retry limit and conflict resolution
