# Restaurant System Development Guide (Final)

## Stack Recommendation
- Backend: Spring Boot
- Database: PostgreSQL
- Staff App: Next.js
- Mobile App: Expo
- Payments: Paystack (MoMo-first UX)

---

## Key Principles
- Mobile Money first UX
- Backend payment verification
- Simple checkout
- Real-time updates
- Offline fallback support
- Strict role-based access
- Manager PIN for sensitive actions

---

## Architecture
- Mobile App (Customer)
- Web App (Staff - Next.js)
- Backend API (Spring Boot)

---

## Phases

### Phase 1: Planning
- User stories
- Architecture
- Database design

### Phase 2: Backend Core
- Auth (Admin, Manager, Cashier)
- PIN system
- Roles & permissions

### Phase 3: Menu + Tables
- Menu APIs
- Table management
- Reservations

### Phase 4: Orders
- Pickup
- Delivery
- Dine-in
- Group ordering

### Phase 5: Payments
- Paystack integration
- MoMo-first UI
- Payment retry & recovery

### Phase 6: Staff App (Next.js)
- POS
- Tables
- Receipts
- Manager override

### Phase 7: Mobile App (Expo)
- Ordering
- Tracking
- Reservations
- Group orders

### Phase 8: Ghana Features
- WhatsApp integration
- QR table ordering
- Loyalty system

### Phase 9: Financial Controls
- Refund
- Void
- Discount
- Reconciliation

### Phase 10: Analytics + QA
- Reports
- Testing
- Deployment

---

## AI Agent Rule
No phase is complete until:
- PROJECT_PROGRESS.md updated
- CHANGELOG.md updated
- Phase report created
