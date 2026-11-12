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

### POST `/auth/otp/request`
`{phone, purpose: REGISTER|LOGIN}`. Sends a 6-digit SMS code (expires in 5 minutes, 60 s resend cooldown, 5 per hour). LOGIN requests never reveal whether the phone is registered.

### POST `/auth/otp/verify`
`{phone, code}` for a REGISTER code. Returns `{phone, verificationToken, expiresIn}`; 5 wrong attempts lock the code.

### POST `/auth/otp/login`
`{phone, code}` for a LOGIN code. Passwordless customer sign-in returning the same payload as `/auth/login`.

### POST `/auth/register`
Register a customer account. Requires `verificationToken` from `/auth/otp/verify` unless `OTP_REQUIRED_FOR_REGISTRATION=false`.

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

## 9. Promotions, Offers, Loyalty, QR Sessions & WhatsApp

Discounts are applied automatically when an order is created (`POST /orders`): active Buy-X-Get-Y offers first, then `promoCode` (on the post-offer amount), then `loyaltyPointsToRedeem` (signed-in customers, capped at `LOYALTY_MAX_REDEEM_PERCENT` of the payable amount). Order responses include `promoCode`, `promoDiscount`, `offerDiscount`, `loyaltyPointsRedeemed`, `loyaltyDiscount`, `manualDiscount`, `deliveryFee` and `discountTotal`. Cancelling or voiding an unpaid order releases the promo use and returns the points.

### GET `/promotions/codes/validate/{code}?subtotal=50.00`
Validate a persisted promo code for the given subtotal (preview only; does not consume a use).

### POST `/promotions/apply`
Low-level promo calculation using an explicit promo payload.

### GET `/promotions/codes`
### POST `/promotions/codes`
### PUT `/promotions/codes/{id}`
### PATCH `/promotions/codes/{id}/status`
Promo code management (admin/manager).

### GET `/promotions/offers`
### POST `/promotions/offers`
### PUT `/promotions/offers/{id}`
### PATCH `/promotions/offers/{id}/status`
### DELETE `/promotions/offers/{id}`
Buy-X-Get-Y offer management: `{name, menuItemId, buyQuantity, freeQuantity, startsAt?, endsAt?, active?}`. Running offers apply automatically: buy 2 get 1 makes every third unit of the item free.

### POST `/promotions/offers/free-items`
Calculation helper for free items earned.

### POST `/loyalty/accrue`
Manual staff accrual. Points are also awarded automatically once a customer's order is fully paid (`LOYALTY_POINTS_PER_CEDI`), and clawed back proportionally on refunds.

### POST `/loyalty/redeem`
Stand-alone redemption endpoint.

### GET `/loyalty/{customerId}/balance`
### GET `/loyalty/{customerId}/history`
Balance and transaction history (`EARN`, `REDEEM`, `ADJUST`).

### POST `/qr/sessions/link` (public)
`{tableToken | tableNumber, sessionId}`. Called by the web scan page and the mobile app when a guest scans a table QR. Sessions are persisted and close automatically when the table is settled, closed or reversed.

### GET `/qr/sessions/active`
Open guest sessions per table (staff).

### GET `/qr/sessions/{tableNumber}/active`
### DELETE `/qr/sessions/{tableNumber}`
Inspect or close a table's guest sessions (staff).

### GET `/qr/tables/{tableNumber}/pdf`
Printable A6 table card (`application/pdf`) containing the QR code for `{PUBLIC_WEB_URL}/scan/{qrToken}`.

### POST `/notifications/whatsapp/order-confirmation`
### POST `/notifications/whatsapp/receipt`
### POST `/notifications/whatsapp/status`
Send WhatsApp messages via the configured provider (`WHATSAPP_PROVIDER=meta` for the WhatsApp Cloud API).

## 10. Financial Controls

All actions need a manager override token (`POST /auth/pin/verify`) for the matching action type. Token errors return 401/403 before the request body is checked. Every action is written to `audit_logs`.

### POST `/financial/discount`
`{overrideToken, orderId, mode: FLAT|PERCENTAGE, value, reason?}`. Sets the order's manual discount and recalculates the total. Rejected (409) once the order has a payment in progress or completed.

### POST `/financial/void`
`{overrideToken, orderId, reason?}`. Cancels an unpaid order, voids open payments, releases the table, promo use and loyalty points. Paid orders must be refunded instead (409); completed orders return 400.

### POST `/financial/refund`
`{overrideToken, paymentId, amount?, reason?}`. Full (default) or partial refund through the payment provider; tracked in `payment_refunds`. The payment becomes `REFUNDED` once nothing is refundable.

### GET `/financial/reconciliation/{businessDate}?branchId=`
Day report computed from the ledger: gross sales, refunds, voids, discounts, net sales, expected cash, totals by payment method, closed-shift cash and sign-off.

### POST `/financial/reconciliation/{businessDate}/sign-off`
`{overrideToken, branchId?, countedCash?, notes?}`. Persists the sign-off with cash variance; one per date and branch (409 on repeat).

### GET `/financial/reconciliation/sign-offs?from=&to=`
### GET `/financial/audit?action=&from=&to=`
Sign-off history and the financial audit trail (admin/manager).

## 11. Analytics & Security Guards

### POST `/analytics/summary`
Analytics summary for supplied records.

### GET `/analytics/revenue`
### GET `/analytics/top-items`
### GET `/analytics/peak-hours`
### GET `/analytics/average-order-value`
### GET `/analytics/overview`
Reports computed from orders and payments.

### POST `/security/sanitize-search`
### POST `/security/escape-notes`
### GET `/security/order-access/{ownerId}`
Security guard helpers.

Load testing is done with k6 against a running stack. See `loadtest/README.md`.

## 11a. Delivery

### GET `/delivery/zones` (public)
Active delivery zones with `fee`, `minOrderAmount` and `estimatedMinutes`. When zones exist, `POST /orders` with `type: DELIVERY` requires `deliveryZoneId`; the zone fee is added to the total. `POST /orders` also accepts an optional `branchId` (customer apps). An order with no table or staff branch takes, in order: `branchId`, the zone's branch, `ORDER_DEFAULT_BRANCH_CODE`, then the only active branch. Orders with no branch are visible to staff at every branch.

### GET `/delivery/zones/all`
### POST `/delivery/zones`
### PUT `/delivery/zones/{id}`
### PATCH `/delivery/zones/{id}/status`
Zone management (admin/manager).

### GET `/delivery/riders`
Active users with the `RIDER` role and their open delivery counts.

### POST `/delivery/orders/{orderId}/assign`
`{riderId}`: assign or reassign a rider before the order leaves the kitchen.

### GET `/delivery/rider/orders`
The signed-in rider's open deliveries.

### POST `/delivery/orders/{orderId}/pickup`
### POST `/delivery/orders/{orderId}/delivered`
Rider workflow: `READY` → `OUT_FOR_DELIVERY` → `DELIVERED`. The customer is notified on WhatsApp.

### POST `/delivery/orders/{orderId}/location`
`{latitude, longitude}` from the assigned rider; broadcast on `/topic/orders.{orderId}.delivery`.

### GET `/delivery/orders/{orderId}/tracking`
Rider, ETA and last location for the ordering customer, staff or the assigned rider.

## 11b. Billing: service charge, tax and tips

### GET `/billing/settings` (public)
Service charge %, whether it applies to dine-in only, whether menu prices include tax, whether the delivery fee is taxed, whether tips are allowed, and the tax list.

### PUT `/billing/settings` (admin)
`{serviceChargePercent, serviceChargeDineInOnly, pricesIncludeTax, taxDeliveryFee, tipsEnabled}`.

### POST `/billing/taxes` · PUT `/billing/taxes/{id}` · DELETE `/billing/taxes/{id}` (admin)
`{code, name, rate, compound, displayOrder, active}`. Rates are percentages. Non-compound levies (NHIL, GETFund) are charged on the taxable base; compound taxes (VAT) on the base plus the levies.

Orders and receipts return `serviceCharge`, `taxTotal`, `taxInclusive`, `tipAmount` and `taxLines[]`. `POST /orders` accepts an optional `tipAmount` when tips are enabled.

## 11c. Split payments

### POST `/orders/{orderId}/splits`
`{mode: "BY_ITEMS" | "EQUAL", parts?, names?}`. `BY_ITEMS` gives each group participant their own items plus a proportional part of fees and taxes; `EQUAL` splits into 2-20 shares. Allowed until a payment starts.

### GET `/orders/{orderId}/splits`
Shares with `status` (`PENDING`, `PAID`, `CANCELLED`) and who paid. Customers see them for their own order or a share assigned to them.

### Guest split payments (public, dine-in)
Under `/orders/public/dine-in/tables/{tableToken}/orders/{orderId}`. The order must have been placed at that table and still be open (not cancelled or completed), otherwise 404/409.
- `GET /splits`
- `POST /splits` `{parts (2-20), names?}`: equal split only
- `POST /splits/{splitId}/pay` `{momoPhone, idempotencyKey}`: Mobile Money only; 409 if that share is paid or a payment for it is already in progress
- `POST /payments/{paymentId}/verify`: refreshes the payment status from the provider

Group orders can be viewed (`GET /orders/{id}`, and they appear in `GET /orders`) by every customer who joined the group session. Only the customer who placed the order can split, change or cancel it.

Once a bill is split, `POST /payments/initiate` requires `splitId` and charges that share's amount. The order is confirmed when every share is paid. Split updates are published on `/topic/orders.{orderId}.payments`.

## 11d. Inventory

### GET `/inventory/ingredients` · GET `/inventory/low-stock`
### POST `/inventory/ingredients` · PUT `/inventory/ingredients/{id}` (admin/manager)
`{name, unit, lowStockThreshold, costPerUnit, initialStock, active}`.

### POST `/inventory/ingredients/{id}/restock`
`{quantity, note}` (admin/manager/cashier).

### POST `/inventory/ingredients/{id}/adjust` (admin/manager)
`{reason: "WASTE" | "ADJUSTMENT", delta?, countedQuantity?, note}`. `countedQuantity` sets the stock to the counted level.

### GET `/inventory/ingredients/{id}/movements`
Stock ledger: `RESTOCK`, `ORDER`, `ORDER_CANCELLED`, `WASTE`, `ADJUSTMENT`.

### GET · PUT `/inventory/menu-items/{menuItemId}/recipe`
Ingredients used per portion. Orders deduct stock (and cancellations return it). Menu items are taken off the menu automatically when an ingredient runs out and come back when restocked. With `INVENTORY_BLOCK_WHEN_INSUFFICIENT=true`, orders that would push stock below zero are rejected.

## 11e. Menu item images

### POST `/menu/items/{menuItemId}/image` (multipart `file`)
JPEG, PNG or WebP up to `MEDIA_MAX_FILE_SIZE` (5 MB). Stored locally (`MEDIA_PROVIDER=local`) or on Cloudinary.

### DELETE `/menu/items/{menuItemId}/image`

## 11f. Customer feedback

### POST `/orders/{orderId}/feedback` (customer)
### POST `/orders/public/dine-in/tables/{tableToken}/orders/{orderId}/feedback` (guest)
`{rating, foodRating?, serviceRating?, deliveryRating?, comment?, guestName?}`. Once per order, after it is `COMPLETED` or `DELIVERED`. Ratings of 2 or lower are published on `/topic/feedback.alerts`.

### GET `/orders/{orderId}/feedback`
### GET `/feedback?from=&to=&maxRating=` · GET `/feedback/summary?from=&to=` (admin/manager)
### POST `/feedback/{id}/respond` (admin/manager)
`{response}`.

## 11g. Report exports

### GET `/reports/sales?from=&to=&branchId=&format=CSV|PDF`
### GET `/reports/reconciliation?date=&branchId=&format=`
### GET `/reports/audit?from=&to=&format=`
### GET `/reports/timesheet?from=&to=&format=`
Admin/manager. Returned as a file download (`Content-Disposition: attachment`).

## 11h. Scheduling and timesheets

### GET `/scheduling/staff` (admin/manager)
Active non-customer users who can be put on the rota.

### GET `/scheduling/shifts?from=&to=&userId=` (admin/manager) · GET `/scheduling/my-shifts?from=&to=` (staff)
### POST `/scheduling/shifts` · PUT `/scheduling/shifts/{id}` · DELETE `/scheduling/shifts/{id}` (admin/manager)
`{userId, branchId?, startsAt, endsAt, position?, notes?}`. Overlapping shifts for the same person are rejected (409); shifts are at most 16 hours.

### POST `/timeclock/clock-in` `{notes?}` · POST `/timeclock/clock-out` `{breakMinutes?}` · GET `/timeclock/status` (staff)
### PUT `/timeclock/entries/{id}` (admin/manager)
`{clockInAt?, clockOutAt?, breakMinutes?, reason}`.

### GET `/timeclock/timesheet?from=&to=&userId=` (admin/manager) · GET `/timeclock/my-timesheet?from=&to=` (staff)
Worked vs scheduled minutes, overtime (over 8 h a day), late arrivals (over 5 min late) and missed shifts.

## 11i. Account recovery

### POST `/auth/password/forgot` `{emailOrPhone}`
Always 202. Sends a single-use reset link (`PUBLIC_WEB_URL/reset-password?token=...`) by email, or by SMS when the account has no email.

### POST `/auth/password/reset` `{token, newPassword}`
Minimum 8 characters. Revokes all refresh tokens.

### POST `/auth/password/change` `{currentPassword, newPassword}` (signed in)
### POST `/auth/email/verification` (signed in) · POST `/auth/email/verify` `{token}`
### POST `/users/{userId}/password-reset` (admin)
Sends the user a reset link.

## 12. Audit

### GET `/audit`
Admin/manager/cashier audit-log view with `action`, `from`, and `to` filters.

## 13. Human resources (`/hr/**`)

Staff roles only (`ADMIN`, `MANAGER`, `CASHIER`, `RIDER`). Each call also checks HR permissions and branch scope (see
`ROLES_AND_PERMISSIONS.md` → Human Resources). Out-of-scope records answer **404**. Full request/response rules per endpoint:
`documentation/hr/HR_TECHNICAL_DOCUMENTATION.md`.

| Area | Endpoints |
|---|---|
| Access | `GET /hr/me/access`; admin: `GET /hr/access/permissions`, `GET/PUT /hr/access/grants[/{userId}]`, `PUT /hr/access/branches/{userId}` |
| Organisation | `GET/POST/PUT /hr/departments[/{id}]`, `/hr/positions[/{id}]`, `.../archive`, `.../restore` |
| Employees | `GET/POST /hr/employees`, `GET/PUT /hr/employees/{id}`, `.../status`, `.../link-user`, `.../transfer`, `.../assignments`, `.../audit`, `GET /hr/employees/linkable-users`, `POST /hr/employees/import-users`, `GET /hr/me/profile` |
| Contracts & documents | `GET/POST /hr/employees/{id}/contracts`, `PUT /hr/contracts/{id}`, `.../activate`, `.../renew`, `.../end`, `GET /hr/contracts/due`; `GET/POST /hr/employees/{id}/documents` (multipart), `GET /hr/documents/{id}/download`, `.../archive` |
| Branches | `POST /hr/assignments/{id}/end`, `GET /hr/branches/workforce` |
| Scheduling | `GET/POST/PUT/DELETE /hr/schedules[/{id}]`, `.../cancel`, `POST /hr/schedules/publish`, `/hr/shift-templates`, `/hr/scheduling-policy`, availability, `/hr/shift-swaps`; self: `/hr/me/shifts`, `/hr/me/availability`, `/hr/me/swaps`, `/hr/me/swap-options` |
| Attendance | `GET /hr/attendance/day`, `/hr/attendance/entries`, `POST /hr/attendance/clock`, `/hr/attendance/corrections[/{id}/decision]`, `/hr/attendance-policy`, `/hr/employees/{id}/clock-pin`; POS: `GET /hr/pos/roster`, `POST /hr/pos/clock`, `POST /hr/pos/me`; self: `/hr/me/attendance[/status]`, `/hr/me/clock`, `/hr/me/clock-pin` |
| Timesheets & overtime | `/hr/timesheets` (generate, refresh, submit, review, approve, reopen, lock), `/hr/me/timesheets`, `/hr/overtime-rules`, `/hr/holidays`, `/hr/overtime-requests[/{id}/decision]` |
| Leave | `/hr/leave-types`, `/hr/leave-policies`, `/hr/employees/{id}/leave-balances[/adjust]`, `/hr/leave-balances/{id}/transactions`, `/hr/leave-requests` (quote, decision, cancel, document), `/hr/leave-calendar`; self: `/hr/me/leave-balances`, `/hr/me/leave-requests` |
| Payroll | `/hr/payroll-rules`, `/hr/payroll-runs[/{id}]` (calculate, adjustments, approve, reopen, finalize, pay, cancel), `GET /hr/payslips/{id}`, `GET /hr/me/payslips` |
| Tips & service charge | `/hr/tip-rules`, `/hr/tip-pools[/{id}]` (calculate, approve, reopen, cancel), `GET /hr/me/tips` |
| Recruitment | `/hr/vacancies[/{id}]` (status, applications), `/hr/applications/{id}` (stage, interviews, offer, offer-response, hire), `PUT /hr/interviews/{id}`, `/hr/candidates[/{id}]` (documents, anonymise), `GET /hr/candidate-documents/{id}/download`, `GET /hr/me/interviews` |

HR-specific responses: scheduling returns **409** with `issues` for blocking problems and **422** with `issues` for warnings that need
`acknowledgeWarnings: true`. POS clock calls are rate limited separately (`hrclock`). Paged HR lists return
`{ "items": [...], "page", "size", "total" }` in the body.

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
