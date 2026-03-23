# Phase 1 Report — Planning & Design

**Status:** Complete
**Date:** 2026-03-23

---

## Summary

Phase 1 established the complete blueprint for the Restaurant Manager system. All design artifacts were produced before any code is written, providing a stable foundation for all subsequent phases.

---

## Deliverables Checklist

- [x] Finalize all user stories (`documentation/final_user_stories.md` reviewed; all epics mapped to phases)
- [x] Define all roles and their permission matrices (`docs/ROLES_AND_PERMISSIONS.md`)
- [x] Design full database schema (`docs/DATABASE_SCHEMA.sql`)
- [x] Design API contract (`docs/API_SPEC.md`)
- [x] Define WebSocket/SSE events (`docs/WEBSOCKET_EVENTS.md`)
- [x] Document offline strategy (`docs/OFFLINE_STRATEGY.md`)
- [x] Project structure documented (monorepo: `backend/`, `staff-app/`, `mobile-app/`)
- [x] `PROJECT_PROGRESS.md` updated
- [x] `CHANGELOG.md` updated
- [x] Phase report created (this file)

---

## Database Schema Summary

**17 tables across 6 domains:**

| Domain             | Tables                                                            |
|--------------------|-------------------------------------------------------------------|
| Auth               | `users`, `refresh_tokens`                                         |
| Menu               | `menu_categories`, `menu_items`                                   |
| Operations         | `restaurant_tables`, `reservations`                               |
| Orders             | `orders`, `order_items`, `group_order_sessions`, `group_session_participants` |
| Payments           | `payments`, `promo_codes`, `loyalty_balances`, `loyalty_transactions` |
| Financial / Audit  | `financial_events`, `daily_reconciliations`, `audit_logs`         |

**Notable design choices:**
- UUID primary keys everywhere for distributed-safe ID generation
- `order_items.price_snapshot` / `order_items.name_snapshot` — price frozen at order time
- `payments.idempotency_key` — unique constraint prevents duplicate Paystack charges
- `users.pin_hash` + `pin_fail_count` + `pin_locked_until` — PIN security with lockout
- `audit_logs.metadata JSONB` — flexible structured context per event
- `updated_at` trigger applied to all mutable tables

---

## API Specification Summary

**12 endpoint groups, ~55 endpoints:**

| Group                | Endpoints | Auth Required       |
|----------------------|-----------|---------------------|
| Auth                 | 5         | Partial (login/reg public) |
| Users                | 6         | Admin only          |
| Menu                 | 9         | Browse public; write = staff |
| Tables               | 6         | Staff only          |
| Reservations         | 6         | Partial (create = public) |
| Orders               | 5         | Customer + staff    |
| Group Orders         | 5         | Customer + staff    |
| Payments             | 4         | Customer + staff    |
| Financial Controls   | 4         | Manager+ with PIN   |
| Loyalty / Promos     | 3         | Customer + staff    |
| Analytics            | 3         | Manager+            |
| Audit Log            | 1         | Admin/Manager       |

---

## Real-Time Design Summary

- **WebSocket (STOMP):** Staff dashboard, kitchen display, group cart sync
- **SSE fallback:** Customer order tracking on mobile when WS unavailable
- **Topics defined:** `orders.new`, `orders.status`, `tables`, per-user order/payment/group queues
- **Reconnection:** Exponential backoff on WS; auto-retry on SSE; REST reconciliation on reconnect

---

## Offline Design Summary

- Menu cached 1 hour (mobile), 5 minutes (web)
- Order creation queued locally when offline; synced FIFO on reconnect
- Payment always requires connectivity (Paystack real-time requirement)
- 3-retry limit on queued actions; conflict resolution with user notification

---

## Key Architectural Decisions

1. **JWT + Refresh Tokens** — stateless auth with secure refresh rotation
2. **Manager PIN → Override Token** — scoped 5-minute token per action type; not a session-level bypass
3. **Paystack as payment layer** — webhook is authoritative; polling is fallback only
4. **Price snapshots on order items** — historical orders are unaffected by future menu changes
5. **Idempotency on payments** — client generates key; server enforces uniqueness
6. **Soft deletes** — users and menu items are deactivated, not deleted, to preserve audit integrity

---

## Risks & Watch Points for Phase 2

| Risk                                         | Mitigation                                               |
|----------------------------------------------|----------------------------------------------------------|
| PIN brute force                              | Lockout after 5 failures; audit log every attempt        |
| JWT token leakage                            | Short access token TTL (15 min); refresh token rotation  |
| Role escalation                              | Spring Security method-level guards + integration tests  |
| Override token misuse                        | Token is action-type scoped; single-use enforcement in Phase 2 |

---

## Next Phase

**Phase 2 — Backend Core**

- Initialize Spring Boot project
- Implement JWT auth with refresh tokens
- Implement RBAC (Admin, Manager, Cashier, Customer)
- Implement Manager PIN system with lockout
- Set up audit logging

See `documentation/DEVELOPMENT_GUIDE.md` Phase 2 for the full task checklist.
