# Restaurant System Development Guide (Final)

## Stack Recommendation
- Backend: Spring Boot
- Database: PostgreSQL
- Staff App: Next.js (`web/`)
- Mobile App: Expo-style workspace (`mobile/`)
- Payments: Paystack (MoMo-first UX)

---

## Table QR Ordering Guide
- Manager QR generation:
  - Table metadata + token: `GET /tables/{id}/qr`
  - QR image for printing: `GET /tables/{id}/qr-image?payload=<scan-link>&sizePx=240`
- Public scan + ordering:
  - Resolve scanned token: `POST /tables/scan`
  - Public menu listing: `GET /menu/items` and `GET /menu/categories`
  - Place guest dine-in order (no login): `POST /orders/public/dine-in`
  - View running table bill: `GET /orders/public/dine-in/tables/{tableToken}/bill`
- Occupancy lifecycle:
  - New dine-in order marks table `OCCUPIED`.
  - Table cannot be closed while outstanding balance exists.
  - Manager/Admin can reverse table session: `POST /orders/dine-in/tables/{tableId}/reverse`
  - Staff close table after settlement: `POST /orders/dine-in/tables/{tableId}/close`
  - Successful payments automatically release table when no outstanding dine-in balance remains.

---

## Kitchen Display System (KDS)
- Endpoint: `GET /kds/board?branchId=<uuid?>`
- Purpose: kitchen-focused board grouped by `CONFIRMED`, `PREPARING`, `READY`
- Web UI: `web/src/app/kds/page.tsx`

---

## Multi-Branch Support
- Branch master: `branches` entity (`/branches` CRUD for manager/admin)
- Branch linkage:
  - Staff: `users.branch_id`
  - Tables: `restaurant_tables.branch_id`
  - Orders: `orders.branch_id` (derived from table for dine-in; from staff for non-dine-in)

---

## Shift Management
- Open shift: `POST /shifts/open`
- Close shift: `POST /shifts/{id}/close`
- Active shifts: `GET /shifts/active`
- Reconciliation: expected cash = opening cash + successful `CASH` payments for the shift scope

---

## Menu Item Modifiers
- Public modifier discovery: `GET /menu/items/{id}/modifiers`
- Manager/Admin modifier group management:
  - `POST /menu/items/{id}/modifiers`
  - `PUT /menu/items/{menuItemId}/modifiers/{groupId}`
  - `DELETE /menu/items/{menuItemId}/modifiers/{groupId}`
- Manager/Admin modifier option management:
  - `POST /menu/items/{menuItemId}/modifiers/{groupId}/options`
  - `PUT /menu/items/{menuItemId}/modifiers/{groupId}/options/{optionId}`
  - `DELETE /menu/items/{menuItemId}/modifiers/{groupId}/options/{optionId}`
- Order input supports `modifierOptionIds` on each order item.
- Modifier selections are validated per menu-item group rules and persisted in `order_item_modifiers`.
- Price snapshots include modifier price deltas for correct historical totals.

---

## Observability and Tracing
- Actuator enabled with health/metrics/prometheus endpoints.
- Micrometer Prometheus registry integrated for scraping.
- Micrometer Tracing integrated (Brave bridge) with Zipkin exporter.
- Structured JSON logging enabled for `prod` and `staging` profiles.
- Docker stack includes `zipkin`, `prometheus`, and `grafana`.

---

## Current Implementation Status (2026-03-24)
- Phase 1: Complete
- Phase 2: Complete
- Phase 3: Complete
- Phase 4: Complete
- Phase 5: Complete
- Phase 6: Complete
- Phase 7: Complete
- Phase 8: Complete
- Phase 9: Complete
- Phase 10: Complete

---

## Code Footprint
- Backend core modules: `backend/src/main/java/com/restaurantmanager/core/**`
- Phase 8 modules: `backend/src/main/java/com/restaurantmanager/core/phase8/**`
- Phase 9 modules: `backend/src/main/java/com/restaurantmanager/core/phase9/**`
- Phase 10 modules: `backend/src/main/java/com/restaurantmanager/core/phase10/**`
- Backend tests: `backend/src/test/java/com/restaurantmanager/core/**`
- Staff app tests: `web/src/features/phase6.test.tsx`
- Mobile app tests: `mobile/src/features/phase7.test.ts`

---

## Reporting Rule
A phase is complete only when all of the following are present:
- `documentation/PROJECT_PROGRESS.md` updated
- `documentation/CHANGELOG.md` updated
- `documentation/reports/phase-N-report.md` created
- Scenario tests implemented and passing

