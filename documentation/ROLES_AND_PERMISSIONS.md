# Roles & Permissions Matrix

## Roles

| Role       | Description                                                      |
|------------|------------------------------------------------------------------|
| `ADMIN`    | Full system access. Manages staff, menu, settings, analytics.    |
| `MANAGER`  | Operational oversight. Approves financial overrides via PIN.     |
| `CASHIER`  | Front-of-house operations. POS, order management, receipts.      |
| `RIDER`    | Delivery staff. Sees and updates their assigned deliveries.      |
| `HR`       | HR officer. Works only in the HR module (every HR permission by default); refused POS, orders, payments, menu, reports and all other areas. |
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

### Human Resources

HR uses its own permissions on top of the roles. Effective HR permissions = the role's defaults + per-user grants (`hr_user_permissions`),
which only an `ADMIN` can change. `CUSTOMER` accounts never receive HR permissions. Full list: `documentation/hr/HR_TECHNICAL_DOCUMENTATION.md` §2.

| HR permission group | ADMIN | HR role | MANAGER default | Others |
|---|:-:|:-:|:-:|:-:|
| Employees: view, register, edit; contracts and documents: view | ✓ | ✓ | ✓ | grant |
| Exits, transfers, departments/positions, contract and document management | ✓ | ✓ | — | grant |
| Pay figures (`hr.compensation.view`) | ✓ | ✓ | — | grant |
| Scheduling, publishing, shift swaps; attendance view/record/correct | ✓ | ✓ | ✓ | grant |
| Timesheet review, overtime approval, leave approval, recruitment, HR dashboard (`hr.reports.view`) | ✓ | ✓ | ✓ | grant |
| Leave configuration (`hr.leave.manage`) | ✓ | ✓ | — | grant |
| Payroll view / process / approve / finalize / pay | ✓ | ✓ | — | grant |
| Tips, service charge | ✓ | ✓ | — | grant |
| HR settings | ✓ | ✓ | — | grant |
| All branches (`hr.branch.all`) | ✓ | when the account has no branch | — | grant |
| Granting HR permissions to others | ✓ | — | — | — |

**HR role isolation:** besides the HR module (`/hr/**`), an `HR` account may call only its own account endpoints (`/auth/**`,
`/users/me`, push devices) and the two lookups HR screens use (`GET /branches`, `GET /scheduling/staff`). Every other request is
refused with 403 by the security filter chain, whatever the endpoint's own rules, and the web app routes HR accounts to `/hr`.

**Branch scope:** `ADMIN` (or a holder of `hr.branch.all`) sees every branch. An `HR` account without a branch is central HR (every
branch); with a branch it is that branch's HR officer. Everyone else sees only the branch on their account plus any
extra branches an admin added; a non-admin without a branch sees no branch data. Unlike the rest of the app, a manager without a branch
does **not** see every branch in HR.

**Self-service** (any staff account linked to an employee record): own profile, with their own phone, email, address and emergency
contacts editable; own contracts (with their own pay) and documents; schedule, availability and swaps, time clock and PIN,
timesheets, leave, payslips, tip shares, and interviews they run.

**Four eyes:** nobody approves their own leave, overtime, timesheet, attendance correction, payroll run they calculated or are paid in,
or a tip pool they calculated or share in.

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

