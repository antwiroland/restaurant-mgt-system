# API Endpoints

All requests are proxied through `/api/rm` → backend at `http://localhost:8080`.
🔓 = public (no auth required) · 🔒 = requires `Authorization: Bearer <accessToken>`

---

## Authentication

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/auth/register` | 🔓 Register a new user |
| `POST` | `/auth/login` | 🔓 Login and receive access + refresh tokens |
| `POST` | `/auth/refresh` | 🔓 Exchange a refresh token for a new access token |
| `POST` | `/auth/logout` | 🔒 Logout (invalidates refresh token) |
| `POST` | `/auth/pin/verify` | 🔒 Verify manager PIN and receive an override token |

**Login request:** `{ phone, password }`
**Register request:** `{ name, phone, email?, password }`
**Refresh request:** `{ refreshToken }`
**PIN verify request:** `{ pin, actionType: "DISCOUNT" | "VOID" | "REFUND" }`

---

## Users

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/users` | 🔒 List all users |
| `PATCH` | `/users/{userId}/role` | 🔒 Assign role (and optional branch) to a user |
| `POST` | `/users/{userId}/pin` | 🔒 Set a user's PIN |

**Assign role request:** `{ role: "ADMIN" | "MANAGER" | "CASHIER" | "CUSTOMER", branchId? }`
**Set PIN request:** `{ pin }`

---

## Menu

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/menu/categories` | 🔓 List all menu categories |
| `POST` | `/menu/categories` | 🔒 Create a category |
| `PUT` | `/menu/categories/{id}` | 🔒 Update a category |
| `DELETE` | `/menu/categories/{id}` | 🔒 Delete a category |
| `GET` | `/menu/items` | 🔓 List all menu items |
| `POST` | `/menu/items` | 🔒 Create a menu item |
| `PUT` | `/menu/items/{id}` | 🔒 Update a menu item |
| `PATCH` | `/menu/items/{id}/availability` | 🔒 Toggle item availability |
| `DELETE` | `/menu/items/{id}` | 🔒 Delete a menu item |
| `GET` | `/menu/items/{menuItemId}/modifiers` | 🔓 List modifier groups for an item |
| `POST` | `/menu/items/{menuItemId}/modifiers` | 🔒 Create a modifier group |
| `PUT` | `/menu/items/{menuItemId}/modifiers/{groupId}` | 🔒 Update a modifier group |
| `DELETE` | `/menu/items/{menuItemId}/modifiers/{groupId}` | 🔒 Delete a modifier group |
| `POST` | `/menu/items/{menuItemId}/modifiers/{groupId}/options` | 🔒 Create a modifier option |
| `PUT` | `/menu/items/{menuItemId}/modifiers/{groupId}/options/{optionId}` | 🔒 Update a modifier option |
| `DELETE` | `/menu/items/{menuItemId}/modifiers/{groupId}/options/{optionId}` | 🔒 Delete a modifier option |

**Create item request:** `{ categoryId, name, description?, price, imageUrl?, available }`
**Availability request:** `{ available: boolean }`

---

## Tables

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/tables` | 🔒 List all tables (staff view with QR tokens) |
| `POST` | `/tables` | 🔒 Create a table |
| `PUT` | `/tables/{tableId}` | 🔒 Update a table |
| `PATCH` | `/tables/{tableId}/status` | 🔒 Update table status |
| `GET` | `/tables/{tableId}/qr` | 🔒 Get QR code data for a table |
| `POST` | `/tables/scan` | 🔓 Scan a QR token to get table info |
| `GET` | `/tables/public` | 🔓 List all tables for the public availability board |

**Create/update request:** `{ number, capacity, zone?, branchId? }`
**Status request:** `{ status: "AVAILABLE" | "OCCUPIED" | "RESERVED" | "CLOSED" }`
**Scan request:** `{ qrToken }`

---

## Orders

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/orders` | 🔒 List all orders |
| `POST` | `/orders` | 🔒 Create an order (staff) |
| `GET` | `/orders/{id}` | 🔒 Get a single order |
| `PATCH` | `/orders/{id}/status` | 🔒 Update order status |
| `DELETE` | `/orders/{id}` | 🔒 Cancel an order |
| `GET` | `/orders/pickup/{pickupCode}` | 🔒 Look up order by pickup code |
| `GET` | `/orders/dine-in/tables/{tableId}/bill` | 🔒 Get the bill for a dine-in table (staff) |
| `POST` | `/orders/dine-in/tables/{tableId}/close` | 🔒 Close a dine-in table session |
| `POST` | `/orders/dine-in/tables/{tableId}/reverse` | 🔒 Reverse a closed table session |
| `POST` | `/orders/public/dine-in` | 🔓 Place a dine-in order via QR scan |
| `GET` | `/orders/public/dine-in/tables/{tableToken}/bill` | 🔓 Get table bill (customer view, by QR token) |
| `GET` | `/orders/public/dine-in/tables/{tableToken}/tracking` | 🔓 Track orders at a table (customer view) |
| `GET` | `/orders/public/status` | 🔓 Active order status board (PENDING → READY, no cancelled/completed) |

**Create order request:** `{ type: "DINE_IN" | "PICKUP" | "DELIVERY", tableId?, deliveryAddress?, notes?, items: [{ menuItemId, quantity, modifierOptionIds? }] }`
**Public dine-in request:** `{ tableToken, notes?, items: [{ menuItemId, quantity, notes?, modifierOptionIds? }] }`
**Status update request:** `{ status }`
**Cancel request:** `{ reason?, overrideToken? }`

---

## Group Orders

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/orders/group/sessions` | 🔒 Create a group ordering session |
| `POST` | `/orders/group/sessions/{code}/join` | 🔒 Join a group session |
| `GET` | `/orders/group/sessions/{code}` | 🔒 Get group session details |
| `POST` | `/orders/group/sessions/{code}/items` | 🔒 Add items for a participant |
| `POST` | `/orders/group/sessions/{code}/finalize` | 🔒 Finalize a group session into an order |

**Create session request:** `{ displayName? }`
**Add items request:** `{ participantId, items: [{ menuItemId, quantity, notes?, modifierOptionIds? }] }`
**Finalize request:** `{ type, tableId?, tableToken?, deliveryAddress?, notes? }`

---

## KDS (Kitchen Display System)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/kds/board` | 🔒 Get the KDS board (all active orders grouped by status) |

**Query params:** `branchId?`

---

## Shifts

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/shifts/active` | 🔒 List active (open) shifts |
| `POST` | `/shifts/open` | 🔒 Open a new shift |
| `POST` | `/shifts/{shiftId}/close` | 🔒 Close a shift |

**Open request:** `{ openingCash, branchId?, notes? }`
**Close request:** `{ closingCash, notes? }`

---

## Reservations

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/reservations` | 🔒 List reservations |
| `POST` | `/reservations` | 🔓 Create a reservation |
| `PATCH` | `/reservations/{id}/status` | 🔒 Update reservation status |
| `DELETE` | `/reservations/{id}` | 🔒 Cancel a reservation |

**Query params:** `date?`, `tableId?`
**Create request:** `{ tableId, customerName?, customerPhone?, partySize, reservedAt, durationMins?, notes? }`
**Status request:** `{ status: "PENDING" | "CONFIRMED" | "CANCELLED" }`

---

## Payments

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/payments/initiate` | 🔒 Initiate a payment |
| `GET` | `/payments/{paymentId}` | 🔒 Get payment details |
| `GET` | `/payments/{paymentId}/verify` | 🔒 Verify payment status with provider |
| `POST` | `/payments/{paymentId}/retry` | 🔒 Retry a failed payment |
| `GET` | `/payments/{paymentId}/receipt` | 🔒 Get receipt by payment ID |
| `GET` | `/payments/orders/{orderId}/receipt` | 🔒 Get receipt by order ID |
| `POST` | `/payments/webhook` | 🔓 Payment provider webhook |

**Initiate request:** `{ orderId, method: "MOBILE_MONEY" | "CARD" | "CASH", momoPhone, idempotencyKey }`
**Retry request:** `{ momoPhone, idempotencyKey }`

---

## Financial Overrides

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/financial/discount` | 🔒 Apply a discount (requires override token) |
| `POST` | `/financial/void` | 🔒 Void a transaction (requires override token) |
| `POST` | `/financial/refund` | 🔒 Refund a transaction (requires override token) |

**Request:** `{ overrideToken }`

---

## Branches

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/branches` | 🔒 List all branches |
| `POST` | `/branches` | 🔒 Create a branch |
| `PUT` | `/branches/{id}` | 🔒 Update a branch |

**Create/update request:** `{ code, name, active }`

---

## Audit

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/audit` | 🔒 List audit events |

**Query params:** `action?`, `from?` (ISO date), `to?` (ISO date)

---

## Public Display Pages

These are Next.js pages (not API endpoints) accessible without login:

| Path | Description |
|------|-------------|
| `/tables-status` | Live table availability board — refreshes every 30s |
| `/orders-status` | Live order status board (PENDING → READY) — refreshes every 30s |
| `/menu` | Public menu browsing |
| `/order` | Customer QR-scan ordering flow |
