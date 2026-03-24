# Phase 5 Report - Payments (Paystack / MoMo-First)

**Phase:** 5  
**Title:** Payments  
**Date Completed:** 2026-03-24

## Scope Delivered

- Payment initiation flow for Mobile Money with Paystack-style reference and authorization URL response
- Idempotency handling on initiation requests to prevent duplicate charges
- Payment status model with lifecycle support:
  - `INITIATED`
  - `PENDING`
  - `SUCCESS`
  - `FAILED`
  - `REFUNDED`
  - `VOIDED`
- Webhook handler with:
  - HMAC signature verification via `x-paystack-signature`
  - Success/failed provider status mapping
  - Duplicate webhook-event deduplication
- Payment verification endpoint (poll fallback)
- Retry endpoint for failed payments
- Successful payment behavior:
  - Marks payment as paid with `paidAt`
  - Updates linked order from `PENDING` to `CONFIRMED`
- Receipt retrieval endpoints by payment ID and order ID
- Real-time payment event publishing:
  - payment status changed
  - payment failed

## API Endpoints Implemented

- `POST /payments/initiate`
- `GET /payments/{id}`
- `GET /payments/{id}/verify`
- `POST /payments/{id}/retry`
- `POST /payments/webhook`
- `GET /payments/{id}/receipt`
- `GET /payments/orders/{orderId}/receipt`

## Database Changes

- Added migration: `backend/src/main/resources/db/migration/V4__phase5_payments.sql`
- New tables:
  - `payments`
  - `payment_webhook_events`
- Added indexes for payment status/order/timestamp lookups
- Added `updated_at` trigger on `payments`

## Test Coverage

Implemented and passing tests in:

- `backend/src/test/java/com/restaurantmanager/core/payment/PaymentPhase5IntegrationTest.java`

Covered scenarios:

- Payment initiation
- Idempotency behavior
- Provider configuration error behavior
- Webhook signature validation and status transitions
- Webhook deduplication
- Payment verify/poll behavior
- Retry behavior for failed payments
- Receipt retrieval behavior
- Realtime publisher invocation for success/failure events

**Total Phase 5 tests:** 17 passing

## Command Verification

- `cd backend && mvn test -q` -> PASS

## Files Added/Updated (Phase 5)

- `backend/src/main/resources/db/migration/V4__phase5_payments.sql`
- `backend/src/main/java/com/restaurantmanager/core/config/PaymentProps.java`
- `backend/src/main/java/com/restaurantmanager/core/payment/**`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/restaurantmanager/core/security/SecurityConfig.java`
- `backend/src/test/java/com/restaurantmanager/core/BaseIntegrationTest.java`
- `backend/src/test/java/com/restaurantmanager/core/payment/PaymentPhase5IntegrationTest.java`
- `documentation/reports/phase-5-report.md`
- `documentation/PROJECT_PROGRESS.md`
- `documentation/CHANGELOG.md`

