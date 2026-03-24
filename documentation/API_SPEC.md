# Restaurant Manager — API Specification

**Base URL:** `https://api.restaurantmanager.com/api/v1`
**Auth:** Bearer JWT in `Authorization` header
**Content-Type:** `application/json`

---

## Conventions

- All IDs are UUIDs
- All timestamps are ISO 8601 UTC (`2026-03-23T14:00:00Z`)
- Currency amounts are `NUMERIC` strings (e.g. `"12.50"`) in GHS
- Paginated endpoints accept `?page=0&size=20&sort=createdAt,desc`
- Error response shape:
  ```json
  {
    "status": 400,
    "error": "BAD_REQUEST",
    "message": "Human-readable message",
    "timestamp": "2026-03-23T14:00:00Z"
  }
  ```

---

## 1. Authentication

### POST `/auth/register`
Register a new customer account.
```json
// Request
{ "name": "Kofi Mensah", "phone": "+233201234567", "email": "kofi@example.com", "password": "secret123" }

// Response 201
{ "id": "uuid", "name": "Kofi Mensah", "phone": "+233201234567", "role": "CUSTOMER", "accessToken": "...", "refreshToken": "..." }
```

### POST `/auth/login`
```json
// Request
{ "phone": "+233201234567", "password": "secret123" }

// Response 200
{ "accessToken": "...", "refreshToken": "...", "expiresIn": 900, "user": { "id": "uuid", "name": "Kofi Mensah", "role": "CUSTOMER" } }
```

### POST `/auth/refresh`
```json
// Request
{ "refreshToken": "..." }

// Response 200
{ "accessToken": "...", "expiresIn": 900 }
```

### POST `/auth/logout`
Revokes refresh token. Requires Bearer token.
```json
// Request
{ "refreshToken": "..." }
// Response 204 No Content
```

### POST `/auth/pin/verify`
Manager provides their PIN to get a short-lived override token.
```json
// Request
{ "pin": "1234", "actionType": "DISCOUNT" }

// Response 200
{ "overrideToken": "...", "expiresIn": 300, "actionType": "DISCOUNT" }

// Response 401 — wrong PIN
// Response 423 — PIN locked (includes lockedUntil)
```

---

## 2. Users (Admin only)

### GET `/users`
List all staff accounts. `?role=CASHIER`
```json
// Response 200
{ "content": [{ "id": "uuid", "name": "Ama", "role": "CASHIER", "active": true }], "totalElements": 5 }
```

### POST `/users`
Create staff account.
```json
// Request
{ "name": "Ama Asante", "phone": "+233209999999", "email": "ama@restaurant.com", "role": "CASHIER", "password": "temp1234" }
// Response 201 — User object
```

### GET `/users/{id}`
### PUT `/users/{id}`
```json
// Request (partial update allowed)
{ "name": "Ama Asante", "active": false }
```

### DELETE `/users/{id}`
Deactivates (soft delete). Response 204.

### POST `/users/{id}/pin`
Set or update manager PIN.
```json
// Request
{ "pin": "5678" }
// Response 204
```

---

## 3. Menu

### GET `/menu/categories`
Public. Returns active categories with items.
```json
// Response 200
[{ "id": "uuid", "name": "Starters", "displayOrder": 1, "items": [ {...} ] }]
```

### POST `/menu/categories` — Admin/Manager
```json
{ "name": "Starters", "description": "Light bites", "displayOrder": 1 }
```

### PUT `/menu/categories/{id}` — Admin/Manager
### DELETE `/menu/categories/{id}` — Admin only. Response 204.

### GET `/menu/items`
Public. Supports `?categoryId=uuid&available=true&q=chicken`
```json
{ "content": [{ "id": "uuid", "name": "Jollof Rice", "price": "25.00", "available": true, "imageUrl": "..." }] }
```

### GET `/menu/items/{id}`

### POST `/menu/items` — Admin/Manager
```json
{ "categoryId": "uuid", "name": "Jollof Rice", "description": "Smoky...", "price": "25.00", "imageUrl": "https://..." }
```

### PUT `/menu/items/{id}` — Admin/Manager

### PATCH `/menu/items/{id}/availability` — Admin/Manager/Cashier
```json
{ "available": false }
// Response 200 — updated item
```

### DELETE `/menu/items/{id}` — Admin only. Response 204.

---

## 4. Tables

### GET `/tables`
Staff only. Returns all tables with current status.
```json
[{ "id": "uuid", "number": "T1", "capacity": 4, "zone": "Main Hall", "status": "AVAILABLE", "qrToken": "abc123" }]
```

### POST `/tables` — Admin only
```json
{ "number": "T5", "capacity": 6, "zone": "Rooftop" }
```

### GET `/tables/{id}`
### PUT `/tables/{id}` — Admin only

### PATCH `/tables/{id}/status` — Admin/Manager/Cashier
```json
{ "status": "OCCUPIED" }
```

### GET `/tables/{id}/qr`
Returns QR code payload and PNG data URL.
```json
{ "tableId": "uuid", "tableNumber": "T1", "qrToken": "abc123", "qrImageDataUrl": "data:image/png;base64,..." }
```

### POST `/tables/scan`
Validates a QR token and returns table info (used by mobile app).
```json
// Request
{ "qrToken": "abc123" }
// Response 200
{ "tableId": "uuid", "tableNumber": "T1", "status": "AVAILABLE" }
// Response 404 — invalid token
```

---

## 5. Reservations

### GET `/reservations`
Staff only. Supports `?date=2026-03-23&tableId=uuid&status=PENDING`
```json
{ "content": [{ "id": "uuid", "customerName": "Kwame", "tableNumber": "T3", "reservedAt": "...", "partySize": 4, "status": "CONFIRMED" }] }
```

### POST `/reservations`
Auth or public (guest with name/phone).
```json
{ "tableId": "uuid", "customerName": "Kwame", "customerPhone": "+233207777777", "partySize": 4, "reservedAt": "2026-03-25T19:00:00Z", "durationMins": 90, "notes": "Anniversary dinner" }
// Response 201 — Reservation object
// Response 409 — table already booked for that slot
```

### GET `/reservations/{id}`
### PUT `/reservations/{id}` — Staff / reservation owner

### PATCH `/reservations/{id}/status` — Staff
```json
{ "status": "CONFIRMED" }
```

### DELETE `/reservations/{id}` — Staff or owner. Response 204.

---

## 6. Orders

### POST `/orders`
Creates an order. Customer or staff.
```json
{
  "type": "DINE_IN",
  "tableId": "uuid",
  "items": [
    { "menuItemId": "uuid", "quantity": 2, "notes": "No pepper" }
  ],
  "promoCode": "SAVE10",
  "groupSessionId": "uuid",
  "deliveryAddress": null,
  "notes": "Extra napkins please"
}
// Response 201
{
  "id": "uuid",
  "status": "PENDING",
  "type": "DINE_IN",
  "subtotal": "50.00",
  "discountAmount": "5.00",
  "total": "45.00",
  "items": [...]
}
```

### GET `/orders`
Staff: all orders. Customer: own orders. Supports `?status=PREPARING&type=DINE_IN`

### GET `/orders/{id}`
Returns full order with items, payment summary.

### PATCH `/orders/{id}/status` — Staff only
```json
{ "status": "PREPARING" }
```

### DELETE `/orders/{id}`
Cancel. Customer: own pending orders only. Confirmed+ requires manager override token.
```json
// Request for confirmed+ orders
{ "reason": "Customer changed mind", "overrideToken": "..." }
```

### GET `/orders/{id}/receipt`
Returns receipt object.
```json
{
  "receiptNumber": "RCP-20260323-0042",
  "orderId": "uuid",
  "items": [...],
  "subtotal": "50.00",
  "discount": "5.00",
  "total": "45.00",
  "paymentMethod": "MOBILE_MONEY",
  "paidAt": "2026-03-23T14:01:00Z"
}
```

---

## 7. Group Orders

### POST `/orders/group/sessions`
```json
// Request
{ "displayName": "Kofi" }
// Response 201
{ "sessionId": "uuid", "sessionCode": "GRP-4829", "status": "OPEN" }
```

### POST `/orders/group/sessions/{code}/join`
```json
{ "displayName": "Ama" }
// Response 200
{ "sessionId": "uuid", "participantId": "uuid", "participants": [...] }
```

### GET `/orders/group/sessions/{code}`
Live view of group cart. Returns all participants and their items.
```json
{
  "sessionCode": "GRP-4829",
  "status": "OPEN",
  "participants": [
    { "participantId": "uuid", "displayName": "Kofi", "items": [...], "subtotal": "30.00" }
  ],
  "groupTotal": "75.00"
}
```

### POST `/orders/group/sessions/{code}/items`
Participant adds items to the group cart.
```json
{ "participantId": "uuid", "items": [{ "menuItemId": "uuid", "quantity": 1 }] }
```

### POST `/orders/group/sessions/{code}/finalize`
Host converts group session into a single order.
```json
{ "type": "DINE_IN", "tableId": "uuid" }
// Response 201 — Order object (same as POST /orders)
```

---

## 8. Payments

### POST `/payments/initiate`
```json
{
  "orderId": "uuid",
  "method": "MOBILE_MONEY",
  "momoPhone": "+233201234567",
  "idempotencyKey": "client-generated-uuid"
}
// Response 201
{
  "paymentId": "uuid",
  "status": "PENDING",
  "paystackReference": "PAY-abc123",
  "authorizationUrl": "https://checkout.paystack.com/...",
  "message": "Approve payment on your phone"
}
```

### GET `/payments/{id}`
```json
{
  "id": "uuid",
  "orderId": "uuid",
  "amount": "45.00",
  "status": "SUCCESS",
  "method": "MOBILE_MONEY",
  "paidAt": "2026-03-23T14:01:00Z"
}
```

### POST `/payments/{id}/retry`
Re-initiates a failed payment for the same order.
```json
{ "momoPhone": "+233201234567", "idempotencyKey": "new-client-uuid" }
// Response 201 — same as initiate response
```

### POST `/payments/webhook`
Paystack webhook. Verifies `x-paystack-signature` header. Internal use only.
Response: `200 OK` (always respond 200 to Paystack to prevent retries).

---

## 9. Financial Controls

All endpoints require a valid `overrideToken` from `POST /auth/pin/verify`.

### POST `/financial/discount`
```json
{
  "orderId": "uuid",
  "discountType": "PERCENTAGE",
  "discountValue": "10",
  "reason": "Staff courtesy",
  "overrideToken": "..."
}
// Response 200 — updated Order object
```

### POST `/financial/void`
```json
{
  "orderId": "uuid",
  "reason": "Duplicate order",
  "overrideToken": "..."
}
// Response 200 — updated Order (status: VOIDED)
```

### POST `/financial/refund`
```json
{
  "paymentId": "uuid",
  "amount": "20.00",
  "reason": "Customer complaint",
  "overrideToken": "..."
}
// Response 200
{ "paymentId": "uuid", "refundedAmount": "20.00", "status": "PARTIALLY_REFUNDED" }
```

### GET `/financial/reconciliation?date=2026-03-23`
Manager+ only.
```json
{
  "date": "2026-03-23",
  "totalSales": "1240.00",
  "totalMomo": "980.00",
  "totalCard": "200.00",
  "totalCash": "60.00",
  "totalRefunds": "45.00",
  "totalVoids": "25.00",
  "totalDiscounts": "30.00",
  "orderCount": 48,
  "signedBy": null,
  "signedAt": null
}
```

### POST `/financial/reconciliation/{date}/sign-off`
```json
{ "overrideToken": "..." }
// Response 200 — updated reconciliation with signedBy / signedAt
```

---

## 10. Loyalty & Promotions

### GET `/loyalty/balance`
```json
{ "customerId": "uuid", "points": 320 }
```

### GET `/loyalty/transactions`
```json
{ "content": [{ "id": "uuid", "points": 50, "type": "EARN", "orderId": "uuid", "createdAt": "..." }] }
```

### GET `/promo-codes/validate/{code}`
```json
// Response 200
{ "code": "SAVE10", "discountType": "PERCENTAGE", "discountValue": "10", "valid": true }
// Response 400 — expired / usage limit reached
```

---

## 11. Analytics (Admin/Manager)

### GET `/analytics/sales?from=2026-03-01&to=2026-03-23&groupBy=DAY`
```json
{ "data": [{ "period": "2026-03-23", "revenue": "1240.00", "orderCount": 48 }] }
```

### GET `/analytics/top-items?from=2026-03-01&to=2026-03-23&limit=10`
```json
{ "items": [{ "menuItemId": "uuid", "name": "Jollof Rice", "quantitySold": 142, "revenue": "3550.00" }] }
```

### GET `/analytics/peak-hours?date=2026-03-23`
```json
{ "hours": [{ "hour": 12, "orderCount": 18 }, { "hour": 13, "orderCount": 22 }] }
```

---

## 12. Audit Log (Admin/Manager)

### GET `/audit-logs?action=PAYMENT_REFUNDED&from=2026-03-01&to=2026-03-23`
```json
{ "content": [{ "id": "uuid", "actorId": "uuid", "action": "PAYMENT_REFUNDED", "entityType": "Payment", "entityId": "uuid", "metadata": {...}, "createdAt": "..." }] }
```

---

## HTTP Status Code Reference

| Code | Meaning                    |
|------|----------------------------|
| 200  | OK                         |
| 201  | Created                    |
| 204  | No Content                 |
| 400  | Bad Request                |
| 401  | Unauthorized               |
| 403  | Forbidden                  |
| 404  | Not Found                  |
| 409  | Conflict (duplicate/clash) |
| 422  | Unprocessable Entity       |
| 423  | Locked (PIN locked)        |
| 500  | Internal Server Error      |

