# Restaurant Manager API Specification

> Preferred source of truth:
> - OpenAPI JSON: `/v3/api-docs`
> - Swagger UI: `/swagger-ui.html`
>
> This file is a human-oriented runtime reference for the current backend.

**Base URL:** `https://api.restaurantmanager.com`
**Auth:** Bearer JWT in `Authorization` header
**Content-Type:** `application/json`

## Notes

- The backend does not use a runtime `/api/v1` prefix.
- The staff web app proxies browser requests through `/api/rm/**` and forwards them to backend root routes.
- Paginated endpoints return pagination metadata through response headers such as `X-Page`, `X-Size`, `X-Total-Elements`, and `X-Total-Pages`.

## 1. Authentication

### POST `/auth/register`
Register a customer account.

### POST `/auth/login`
Returns access token, refresh token, expiry, and user payload.

### POST `/auth/refresh`
Returns a new access token.

### POST `/auth/logout`
Revokes the supplied refresh token.

### POST `/auth/pin/verify`
Returns a short-lived override token for manager-controlled actions.

## 2. Users

### GET `/users`
Admin only. List users.

### PATCH `/users/{id}/role`
Admin only. Assign a role and optional branch.

### POST `/users/{id}/pin`
Authenticated user with permission can set a PIN.

### GET `/users/me`
Authenticated profile lookup.

### PATCH `/users/me`
Customer only. Update own profile.

### GET `/users/me/addresses`
Customer only. List delivery addresses.

### POST `/users/me/addresses`
Customer only. Add delivery address.

## 3. Menu

### GET `/menu/categories`
Public. List categories with pagination headers.

### GET `/menu/items`
Public. Supports `categoryId`, `available`, and `q`.

### GET `/menu/items/{id}`
Public/staff lookup by ID.

### GET `/menu/items/{id}/modifiers`
Public/staff lookup for modifier groups and options.

### POST `/menu/categories`
Admin/manager only.

### PUT `/menu/categories/{id}`
Admin/manager only.

### DELETE `/menu/categories/{id}`
Admin only.

### POST `/menu/items`
Admin/manager only.

### PUT `/menu/items/{id}`
Admin/manager only.

### PATCH `/menu/items/{id}/availability`
Admin/manager/cashier only.

### DELETE `/menu/items/{id}`
Admin only.

## 4. Tables

### GET `/tables/public`
Public table-status view.

### GET `/tables`
Staff only.

### POST `/tables`
Admin only.

### PUT `/tables/{id}`
Admin only.

### PATCH `/tables/{id}/status`
Admin/manager/cashier only.

### GET `/tables/{id}/qr`
Staff only.

### GET `/tables/{id}/qr-image`
Staff only. PNG response.

### POST `/tables/scan`
Public QR-token validation.

## 5. Reservations

### POST `/reservations`
Public or authenticated customer creation.

### GET `/reservations`
Staff list endpoint with `date` and `tableId` filters.

### GET `/reservations/mine`
Customer only.

### PATCH `/reservations/{id}/status`
Staff only.

### DELETE `/reservations/{id}`
Staff or reservation owner.

## 6. Orders

### POST `/orders`
Create order.

### GET `/orders`
Role-aware list endpoint.

### GET `/orders/{id}`
Role-aware order lookup.

### PATCH `/orders/{id}/status`
Staff only.

### DELETE `/orders/{id}`
Role-aware cancellation path.

### GET `/orders/pickup/{pickupCode}`
Staff only.

### POST `/orders/dine-in/tables/{tableId}/close`
Staff only.

### POST `/orders/dine-in/tables/{tableId}/reverse`
Admin/manager only.

### GET `/orders/dine-in/tables/{tableId}/bill`
Staff only.

### GET `/orders/public/status`
Public status board.

### POST `/orders/public/dine-in`
Public table order creation.

### GET `/orders/public/dine-in/tables/{tableToken}/bill`
Public.

### GET `/orders/public/dine-in/tables/{tableToken}/tracking`
Public.

## 7. Group Orders

### POST `/orders/group/sessions`
Create group session.

### POST `/orders/group/sessions/{code}/join`
Join group session.

### POST `/orders/group/sessions/{code}/items`
Add participant items.

### GET `/orders/group/sessions/{code}`
View live group cart.

### POST `/orders/group/sessions/{code}/finalize`
Finalize group order.

## 8. Payments

### POST `/payments/initiate`
Initiate payment.

### GET `/payments/{id}`
Get payment status.

### GET `/payments/{id}/verify`
Verify payment.

### POST `/payments/{id}/retry`
Retry failed payment.

### POST `/payments/webhook`
Public webhook endpoint.

### GET `/payments/{id}/receipt`
Receipt by payment ID.

### GET `/payments/orders/{orderId}/receipt`
Receipt by order ID.

## 9. Phase 8 Runtime

### GET `/phase8/promo-codes/validate/{code}?subtotal=50.00`
Validate a persisted promo code for the given subtotal.

### POST `/phase8/promo/apply`
Low-level promo runtime endpoint using an explicit promo payload.

### POST `/phase8/offer/free-items`
Buy-X-get-Y runtime helper.

### POST `/phase8/loyalty/accrue`
Staff accrual endpoint.

### POST `/phase8/loyalty/redeem`
Loyalty redemption endpoint.

### GET `/phase8/loyalty/{customerId}/balance`
Loyalty balance lookup.

### GET `/phase8/loyalty/{customerId}/history`
Loyalty history lookup.

### POST `/phase8/qr/sessions/link`
Link mobile QR session.

### GET `/phase8/qr/sessions/{tableNumber}/active`
Check active QR session.

### DELETE `/phase8/qr/sessions/{tableNumber}`
Close QR session.

### GET `/phase8/qr/tables/{tableNumber}/pdf`
Export QR PDF payload.

### POST `/phase8/whatsapp/order-confirmation`
### POST `/phase8/whatsapp/receipt`
### POST `/phase8/whatsapp/status`
WhatsApp runtime endpoints.

## 10. Phase 9 Runtime

### POST `/phase9/discount/apply`
### POST `/phase9/refund/apply`
### POST `/phase9/void/apply`
Financial control endpoints.

### POST `/phase9/reconciliation/summarize`
### POST `/phase9/reconciliation/{businessDate}/sign-off`
Reconciliation endpoints.

### GET `/phase9/audit`
Phase 9 audit review endpoint.

## 11. Phase 10 Runtime

### POST `/phase10/analytics/summary`
Synthetic analytics summary endpoint.

### GET `/phase10/analytics/revenue`
Revenue points by period.

### GET `/phase10/analytics/top-items`
Top-selling items.

### GET `/phase10/analytics/peak-hours`
Peak-hour report.

### GET `/phase10/analytics/average-order-value`
Average order value report.

### POST `/phase10/load/run`
Synthetic load report.

### POST `/phase10/security/sanitize-search`
### POST `/phase10/security/escape-notes`
### GET `/phase10/security/order-access/{ownerId}`
Security/runtime guard endpoints.

## 12. Audit

### GET `/audit`
Admin/manager/cashier audit-log view with `action`, `from`, and `to` filters.

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict |
| 422 | Unprocessable Entity |
| 423 | Locked |
| 429 | Too Many Requests |
| 500 | Internal Server Error |
