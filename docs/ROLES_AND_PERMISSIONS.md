# Roles & Permissions Matrix

## Roles

| Role       | Description                                                      |
|------------|------------------------------------------------------------------|
| `ADMIN`    | Full system access. Manages staff, menu, settings, analytics.    |
| `MANAGER`  | Operational oversight. Approves financial overrides via PIN.     |
| `CASHIER`  | Front-of-house operations. POS, order management, receipts.      |
| `CUSTOMER` | End user. Orders, reservations, payment, loyalty.                |

---

## Permission Matrix

### Auth & Users

| Action                        | ADMIN | MANAGER | CASHIER | CUSTOMER |
|-------------------------------|:-----:|:-------:|:-------:|:--------:|
| Register / Login              | ✓     | ✓       | ✓       | ✓        |
| View own profile              | ✓     | ✓       | ✓       | ✓        |
| Update own profile            | ✓     | ✓       | ✓       | ✓        |
| Set / change own PIN          | —     | ✓       | —       | —        |
| View all staff accounts       | ✓     | —       | —       | —        |
| Create staff account          | ✓     | —       | —       | —        |
| Deactivate staff account      | ✓     | —       | —       | —        |
| Assign roles                  | ✓     | —       | —       | —        |

### Menu

| Action                        | ADMIN | MANAGER | CASHIER | CUSTOMER |
|-------------------------------|:-----:|:-------:|:-------:|:--------:|
| Browse menu (public)          | ✓     | ✓       | ✓       | ✓        |
| Search / filter menu          | ✓     | ✓       | ✓       | ✓        |
| Create category               | ✓     | ✓       | —       | —        |
| Edit category                 | ✓     | ✓       | —       | —        |
| Delete category               | ✓     | —       | —       | —        |
| Create menu item              | ✓     | ✓       | —       | —        |
| Edit menu item                | ✓     | ✓       | —       | —        |
| Toggle item availability      | ✓     | ✓       | ✓       | —        |
| Delete menu item              | ✓     | —       | —       | —        |

### Tables & Reservations

| Action                        | ADMIN | MANAGER | CASHIER | CUSTOMER |
|-------------------------------|:-----:|:-------:|:-------:|:--------:|
| View all tables               | ✓     | ✓       | ✓       | —        |
| Create / edit table           | ✓     | —       | —       | —        |
| Update table status           | ✓     | ✓       | ✓       | —        |
| Generate / view QR code       | ✓     | ✓       | ✓       | —        |
| Validate QR token             | ✓     | ✓       | ✓       | ✓        |
| Create reservation            | ✓     | ✓       | ✓       | ✓        |
| View all reservations         | ✓     | ✓       | ✓       | —        |
| View own reservations         | ✓     | ✓       | ✓       | ✓        |
| Confirm reservation           | ✓     | ✓       | ✓       | —        |
| Cancel reservation            | ✓     | ✓       | ✓       | ✓ (own)  |

### Orders

| Action                        | ADMIN | MANAGER | CASHIER | CUSTOMER |
|-------------------------------|:-----:|:-------:|:-------:|:--------:|
| Create order                  | ✓     | ✓       | ✓       | ✓        |
| View own orders               | ✓     | ✓       | ✓       | ✓        |
| View all orders               | ✓     | ✓       | ✓       | —        |
| Update order status           | ✓     | ✓       | ✓       | —        |
| Cancel order (pending)        | ✓     | ✓       | ✓       | ✓ (own)  |
| Cancel order (confirmed+)     | ✓     | ✓ (PIN) | — (PIN) | —        |
| Create group order session    | ✓     | ✓       | ✓       | ✓        |
| Join group session            | ✓     | ✓       | ✓       | ✓        |
| Finalize group order          | ✓     | ✓       | ✓       | ✓ (host) |

### Payments

| Action                        | ADMIN | MANAGER | CASHIER | CUSTOMER |
|-------------------------------|:-----:|:-------:|:-------:|:--------:|
| Initiate payment              | ✓     | ✓       | ✓       | ✓        |
| View payment status           | ✓     | ✓       | ✓       | ✓ (own)  |
| Retry failed payment          | ✓     | ✓       | ✓       | ✓ (own)  |
| View receipt                  | ✓     | ✓       | ✓       | ✓ (own)  |

### Financial Controls (Manager PIN Required)

| Action                        | ADMIN | MANAGER      | CASHIER          | CUSTOMER |
|-------------------------------|:-----:|:------------:|:----------------:|:--------:|
| Apply manual discount         | ✓     | ✓ (PIN)      | Trigger only     | —        |
| Void order                    | ✓     | ✓ (PIN)      | Trigger only     | —        |
| Issue refund                  | ✓     | ✓ (PIN)      | Trigger only     | —        |
| View reconciliation report    | ✓     | ✓            | —                | —        |
| Sign off daily reconciliation | ✓     | ✓ (PIN)      | —                | —        |
| View audit log                | ✓     | ✓            | —                | —        |
| Export audit log              | ✓     | —            | —                | —        |

### Loyalty & Promotions

| Action                        | ADMIN | MANAGER | CASHIER | CUSTOMER |
|-------------------------------|:-----:|:-------:|:-------:|:--------:|
| View own loyalty balance      | ✓     | ✓       | ✓       | ✓        |
| Redeem loyalty points         | ✓     | ✓       | ✓       | ✓        |
| Create promo code             | ✓     | ✓       | —       | —        |
| Edit / deactivate promo code  | ✓     | ✓       | —       | —        |
| Apply promo code at checkout  | ✓     | ✓       | ✓       | ✓        |
| View loyalty stats            | ✓     | ✓       | —       | —        |

### Analytics

| Action                        | ADMIN | MANAGER | CASHIER | CUSTOMER |
|-------------------------------|:-----:|:-------:|:-------:|:--------:|
| Sales dashboard               | ✓     | ✓       | —       | —        |
| Top-selling items report      | ✓     | ✓       | —       | —        |
| Peak hours report             | ✓     | ✓       | —       | —        |
| Per-cashier breakdown         | ✓     | ✓       | —       | —        |
| Customer retention stats      | ✓     | —       | —       | —        |

---

## Manager PIN — When It Is Required

The PIN is a short-lived override mechanism. It produces a time-limited override token (TTL: 5 minutes) scoped to a specific action type.

| Sensitive Action              | PIN Required By |
|-------------------------------|-----------------|
| Apply manual discount         | Manager+        |
| Void an order                 | Manager+        |
| Issue a refund                | Manager+        |
| Cancel a confirmed order      | Manager+        |
| Daily reconciliation sign-off | Manager+        |

**PIN lockout:** 5 consecutive failures lock the PIN for 30 minutes. Event is logged to audit log.

---

## Notes

- `CASHIER` can *trigger* a PIN-required action (e.g., request a discount), which surfaces a PIN modal. The *approving* manager enters their PIN on the same device or via a manager approval workflow.
- `CUSTOMER` tokens are issued with a `CUSTOMER` role claim. They cannot access any staff endpoints.
- Guest checkout (no account) receives a session token with no role, scoped only to order creation and payment.
