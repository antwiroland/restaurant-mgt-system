# Restaurant Manager — Detailed Development Guide

**Stack:** Spring Boot · PostgreSQL · Next.js (Staff) · Expo (Mobile) · Paystack (MoMo-first)

---

## Key Principles

- Mobile Money (MoMo) first UX — default payment option throughout
- All payment verification happens on the backend (never trust client)
- Simple, fast checkout flow
- Real-time updates via WebSocket/SSE
- Offline fallback support for intermittent connectivity
- Strict role-based access control (Admin, Manager, Cashier)
- Manager PIN required for sensitive financial actions (refund, void, discount)

---

## Repository Structure (Current)

- `backend/` - Spring Boot API, domain logic, Flyway migrations, Java tests
- `web/` - Next.js staff application (POS, tables, orders, receipts, admin)
- `mobile/` - Expo-style customer mobile workspace and feature logic tests
- `documentation/` - Specs, guides, changelog, progress tracker, phase reports

### Common Commands

- Backend tests: `cd backend && mvn test -q`
- Web tests: `cd web && npm run test`
- Web build: `cd web && npm run build`
- Mobile tests: `cd mobile && npm run test`
- Mobile typecheck: `cd mobile && npm run typecheck`

---

## Testing Mandate

> **This rule is non-negotiable and applies to every phase from Phase 2 onwards.**

### Rule: Test every task, not just the phase

- Every implementation task **must have a corresponding test written at the same time as the code** — not after, not in Phase 10.
- Tests must cover the **exact scenario described by the task**, not a generic happy path.
- A task checkbox **must not be checked** until its tests are written and passing.
- Tests that are skipped, commented out, or marked `@Disabled` / `xit` / `xtest` count as failing.

### Test types by layer

| Layer              | Test Type          | Tool                                      |
|--------------------|--------------------|-------------------------------------------|
| Backend service    | Unit test          | JUnit 5 + Mockito                         |
| Backend API        | Integration test   | Spring Boot Test + Testcontainers (real PG) |
| Next.js components | Unit test          | Vitest + React Testing Library            |
| Next.js pages/flows| Integration test   | Vitest + MSW (mock API)                   |
| Expo screens       | Unit test          | Jest + React Native Testing Library       |
| Expo flows         | E2E test           | Maestro                                   |

### What "fits the scenario" means

Each test must be named after and directly assert the behaviour described in its task. Examples:

| Task                                       | Required test name / scenario                                          |
|--------------------------------------------|------------------------------------------------------------------------|
| PIN lockout after N failed attempts        | `givenFiveWrongPins_whenVerifyPin_thenAccountLocked`                   |
| Promo code rejected when usage limit hit   | `givenPromoAtLimit_whenApply_then400`                                  |
| Price snapshot on order item               | `givenMenuPriceChanged_whenViewOldOrder_thenOriginalPriceShown`        |
| Override token is action-type scoped       | `givenDiscountToken_whenUsedForVoid_then403`                           |
| Offline order queued and synced            | `givenOfflineCart_whenReconnect_thenOrderSubmittedToServer`            |

### CI gate

All tests must pass in CI before any phase is marked complete. A phase with failing tests is **In Progress**, not **Complete**, regardless of whether all code tasks are checked.

---

## Phase Completion Rule

> A phase is **not complete** until ALL of the following are true:
> - [ ] Every implementation task has passing tests (see Testing Mandate)
> - [ ] `documentation/PROJECT_PROGRESS.md` updated
> - [ ] `documentation/CHANGELOG.md` updated
> - [ ] Phase report created in `documentation/reports/phase-N-report.md`

---

## Phase 1 — Planning & Design

**Goal:** Establish full project blueprint before writing any code.
> Phase 1 has no tests — it produces documents, not code.

### Tasks

- [x] Finalize all user stories (review `final_user_stories.md`)
- [x] Define all roles and their permission matrices (Admin, Manager, Cashier, Customer)
- [x] Design full database schema (ERD) covering:
  - [x] Users & roles
  - [x] Menu items & categories
  - [x] Tables & reservations
  - [x] Orders (pickup, delivery, dine-in, group)
  - [x] Payments & transactions
  - [x] Loyalty points & promo codes
  - [x] Audit log / financial events
- [x] Design API contract (REST endpoints, request/response shapes)
- [x] Define WebSocket/SSE events for real-time updates
- [x] Document offline strategy (what works offline, sync mechanism)
- [x] Set up monorepo or multi-repo structure with shared contracts
- [x] Set up version control, CI skeleton, environment configs

### Deliverables
- [x] `documentation/DATABASE_SCHEMA.sql` — PostgreSQL V1 migration (17 tables)
- [x] `documentation/ROLES_AND_PERMISSIONS.md` — Full role/permission matrix
- [x] `documentation/API_SPEC.md` — REST API spec (~55 endpoints across 12 groups)
- [x] `documentation/WEBSOCKET_EVENTS.md` — STOMP topics and SSE fallback
- [x] `documentation/OFFLINE_STRATEGY.md` — Offline scope, cache, sync queue
- [x] `documentation/reports/phase-1-report.md`

---

## Phase 2 — Backend Core (Auth, Roles, PIN)

**Goal:** Secure foundation that all other phases depend on.

### Tasks

#### Project Setup
- [x] Initialize Spring Boot project (Maven/Gradle)
- [x] Configure PostgreSQL datasource, connection pool (HikariCP)
- [x] Set up Flyway/Liquibase for database migrations
- [x] Configure environment profiles (dev, staging, prod)
- [x] Set up structured logging (JSON logs for prod)

#### Authentication
- [x] Implement JWT-based auth (access + refresh tokens)
- [x] User registration & login endpoints
- [x] Token refresh endpoint
- [x] Secure password hashing (BCrypt)
- [x] Logout / token invalidation

#### Roles & Permissions
- [x] Implement roles: `ADMIN`, `MANAGER`, `CASHIER`, `CUSTOMER`
- [x] Spring Security role-based method/endpoint guards
- [x] Role assignment endpoints (Admin only)

#### Manager PIN System
- [x] PIN creation and hashing for manager accounts
- [x] PIN verification endpoint (returns short-lived override token)
- [x] PIN required guard for: refunds, voids, discounts
- [x] PIN lockout after N failed attempts (configurable)

#### Audit Logging
- [x] Audit log table for all sensitive actions
- [x] Log actor, action, target entity, timestamp, IP

---

### Tests — Phase 2

Every test below must be written and green before the corresponding task is checked.

#### Authentication tests
- [x] `givenValidData_whenRegister_then201AndUserCreatedWithCustomerRole`
- [x] `givenDuplicatePhone_whenRegister_then409`
- [x] `givenDuplicateEmail_whenRegister_then409`
- [x] `givenCorrectCredentials_whenLogin_then200WithAccessAndRefreshTokens`
- [x] `givenWrongPassword_whenLogin_then401`
- [x] `givenUnknownPhone_whenLogin_then401`
- [x] `givenValidRefreshToken_whenRefresh_then200WithNewAccessToken`
- [x] `givenRevokedRefreshToken_whenRefresh_then401`
- [x] `givenExpiredRefreshToken_whenRefresh_then401`
- [x] `givenNoToken_whenAccessProtectedEndpoint_then401`
- [x] `givenExpiredAccessToken_whenAccessProtectedEndpoint_then401`
- [x] `givenValidLogout_whenRefreshAfterwards_then401`

#### Role & permission tests
- [x] `givenAdminToken_whenAccessAdminOnlyEndpoint_then200`
- [x] `givenCashierToken_whenAccessAdminOnlyEndpoint_then403`
- [x] `givenManagerToken_whenAccessAdminOnlyEndpoint_then403`
- [x] `givenCustomerToken_whenAccessStaffEndpoint_then403`
- [x] `givenAdminToken_whenAssignRole_then200AndRoleUpdated`
- [x] `givenManagerToken_whenAssignRole_then403`

#### Manager PIN tests
- [x] `givenManagerSetsPin_when204_thenPinHashedAndStored`
- [x] `givenCorrectPin_whenVerify_then200WithScopedOverrideToken`
- [x] `givenWrongPin_whenVerify_then401AndFailCountIncremented`
- [x] `givenFiveWrongPins_whenVerify_then423AndAccountLocked`
- [x] `givenLockedPin_whenVerify_then423WithLockedUntilTimestamp`
- [x] `givenDiscountOverrideToken_whenUsedForVoidAction_then403`
- [x] `givenOverrideToken_whenUsedAfter5Minutes_then401Expired`
- [x] `givenNonManagerRole_whenSetPin_then403`

#### Audit log tests
- [x] `givenSuccessfulLogin_thenUserLoginEventWrittenToAuditLog`
- [x] `givenFailedPinAttempt_thenPinFailedEventWrittenToAuditLog`
- [x] `givenPinLockout_thenPinLockedEventWrittenToAuditLog`
- [x] `givenRoleAssigned_thenRoleAssignedEventWrittenToAuditLog`

### Deliverables
- [x] All Phase 2 tests passing in CI
- [x] `documentation/reports/phase-2-report.md`

---

## Phase 3 — Menu & Table Management

**Goal:** Staff can fully manage the menu and tables; customers can browse.

### Tasks

#### Menu
- [x] `Category` CRUD (name, description, display order, active flag)
- [x] `MenuItem` CRUD (name, description, price, category, image URL, available flag)
- [x] Menu item availability toggle (e.g., sold out)
- [x] Menu list endpoint (public, no auth) — sorted by category
- [x] Menu search/filter endpoint

#### Tables
- [x] `Table` CRUD (number, capacity, floor/zone label, status: available/occupied/reserved)
- [x] Table status update endpoint
- [x] QR code generation per table (returns unique table token/URL)
- [x] Endpoint to validate QR table token (used by mobile app on scan)

#### Reservations
- [x] `Reservation` model (customer name/phone, table, date/time, party size, status)
- [x] Create reservation endpoint (staff or customer)
- [x] List reservations (by date, by table)
- [x] Confirm / cancel reservation endpoints
- [x] Basic conflict check (prevent double-booking same table/time)

---

### Tests — Phase 3

#### Menu category tests
- [x] `givenAdminToken_whenCreateCategory_then201`
- [x] `givenCashierToken_whenCreateCategory_then403`
- [x] `givenNoAuth_whenListCategories_then200PublicAccess`
- [x] `givenMultipleCategories_whenList_thenReturnedSortedByDisplayOrder`
- [x] `givenAdminToken_whenDeleteCategory_then204`
- [x] `givenManagerToken_whenDeleteCategory_then403`

#### Menu item tests
- [x] `givenAdminToken_whenCreateMenuItem_then201WithCorrectFields`
- [x] `givenNoAuth_whenListMenuItems_then200`
- [x] `givenAvailableFalse_whenListPublicMenu_thenItemExcluded`
- [x] `givenSearchQuery_whenListMenuItems_thenOnlyMatchingItemsReturned`
- [x] `givenManagerToken_whenToggleAvailabilityToFalse_thenItemUnavailableInPublicList`
- [x] `givenCashierToken_whenToggleAvailability_then200`
- [x] `givenCustomerToken_whenToggleAvailability_then403`

#### Table tests
- [x] `givenAdminToken_whenCreateTable_then201WithUniqueQrToken`
- [x] `givenTwoTables_whenListTables_thenBothReturnedWithStatus`
- [x] `givenCashierToken_whenUpdateTableStatus_then200`
- [x] `givenCustomerToken_whenUpdateTableStatus_then403`
- [x] `givenValidQrToken_whenScanTable_then200WithTableInfo`
- [x] `givenInvalidQrToken_whenScanTable_then404`
- [x] `givenDuplicateTableNumber_whenCreate_then409`

#### Reservation tests
- [x] `givenAvailableTableAndTime_whenCreateReservation_then201`
- [x] `givenSameTableAndOverlappingTime_whenCreateReservation_then409`
- [x] `givenNoAuth_whenCreateReservationWithNameAndPhone_then201GuestReservation`
- [x] `givenStaffToken_whenConfirmReservation_then200StatusConfirmed`
- [x] `givenCustomerToken_whenCancelOwnReservation_then204`
- [x] `givenCustomerToken_whenCancelOtherCustomerReservation_then403`
- [x] `givenDateFilter_whenListReservations_thenOnlyReservationsForThatDateReturned`

### Deliverables
- [x] All Phase 3 tests passing in CI
- [x] QR token generation and validation working
- [x] `documentation/reports/phase-3-report.md`

---

## Phase 4 — Orders

**Goal:** Full order lifecycle for all order types.

### Tasks

#### Order Core
- [x] `Order` model with type enum: `PICKUP`, `DELIVERY`, `DINE_IN`
- [x] `OrderItem` model (menu item snapshot: name, price at time of order)
- [x] Order status lifecycle: `PENDING → CONFIRMED → PREPARING → READY → COMPLETED / CANCELLED`
- [x] Create order endpoint (staff and customer)
- [x] Update order status endpoint (staff only, role-gated)
- [x] Cancel order endpoint (with reason, manager PIN if order is confirmed)
- [x] Order history endpoint (by customer, by date range)

#### Pickup Orders
- [x] Pickup time slot selection
- [x] Pickup code generation (shown to customer, entered by cashier)

#### Delivery Orders
- [x] Delivery address capture
- [x] Delivery status sub-states: `OUT_FOR_DELIVERY`, `DELIVERED`
- [x] Estimated delivery time field

#### Dine-In Orders
- [x] Link order to table (via table token or staff table selection)
- [x] Support multiple order submissions per table session
- [x] Close table / end session endpoint

#### Group Ordering
- [x] Create group order session (returns session code)
- [x] Join group session endpoint
- [x] Each participant adds items to shared cart
- [x] View combined group order
- [x] Finalize group order (converts to single order record)
- [x] Track which items belong to which participant (for split pay)

#### Real-Time Updates
- [x] WebSocket or SSE channel per order (status push to customer)
- [x] Kitchen/staff notification on new order
- [x] Order status push to all participants in group order

---

### Tests — Phase 4

#### Order core tests
- [x] `givenCustomerToken_whenCreateDineInOrder_then201WithStatusPending`
- [x] `givenCustomerToken_whenCreatePickupOrder_then201WithPickupCodeGenerated`
- [x] `givenCustomerToken_whenCreateDeliveryOrderWithAddress_then201`
- [x] `givenDeliveryOrderWithNoAddress_whenCreate_then400`
- [x] `givenCashierToken_whenUpdateOrderStatusToConfirmed_then200`
- [x] `givenCustomerToken_whenUpdateOrderStatus_then403`
- [x] `givenCustomerToken_whenCancelOwnPendingOrder_then204`
- [x] `givenCustomerToken_whenCancelConfirmedOrderWithoutPin_then403`
- [x] `givenValidOverrideToken_whenCancelConfirmedOrder_then200WithStatusCancelled`
- [x] `givenCustomerToken_whenCancelOtherCustomerOrder_then403`
- [x] `givenDateRangeFilter_whenListOrders_thenOnlyOrdersWithinRangeReturned`

#### Price snapshot test
- [x] `givenExistingOrder_whenMenuItemPriceChangedAfterwards_thenOrderItemStillShowsOriginalPrice`

#### Pickup tests
- [x] `givenPickupOrder_whenCreated_thenUniquePickupCodeAssigned`
- [x] `givenPickupCode_whenCashierEntersCode_thenMatchingOrderReturned`

#### Delivery tests
- [x] `givenDeliveryOrder_whenStatusUpdatedToOutForDelivery_then200`
- [x] `givenDeliveryOrder_whenStatusUpdatedToDelivered_then200`

#### Dine-in tests
- [x] `givenValidTableToken_whenCreateDineInOrder_thenOrderLinkedToTable`
- [x] `givenOccupiedTable_whenCreateSecondOrder_then201BothOrdersLinkedToTable`
- [x] `givenDineInSession_whenCloseTable_thenTableStatusChangedToAvailable`

#### Group order tests
- [x] `givenCustomerToken_whenCreateGroupSession_then201WithSessionCode`
- [x] `givenSessionCode_whenSecondUserJoins_then200ParticipantAdded`
- [x] `givenParticipant_whenAddItemsToGroupCart_thenGroupTotalUpdated`
- [x] `givenGroupSession_whenNonHostTriesToFinalize_then403`
- [x] `givenGroupSession_whenHostFinalizes_then201OrderWithAllParticipantItems`
- [x] `givenFinalizedGroupOrder_whenViewOrderItems_thenEachItemShowsParticipantId`

#### Real-time tests
- [x] `givenSubscribedToOrderTopic_whenOrderCreated_thenWebSocketReceivesOrderCreatedEvent`
- [x] `givenSubscribedToOrderTopic_whenStatusUpdated_thenWebSocketReceivesStatusChangedEvent`
- [x] `givenGroupSessionParticipants_whenItemAdded_thenAllParticipantsReceiveGroupCartUpdatedEvent`

### Deliverables
- [x] All Phase 4 tests passing in CI
- [x] Real-time status update verified via WebSocket integration test
- [x] `documentation/reports/phase-4-report.md`

---

## Phase 5 — Payments (Paystack / MoMo-First)

**Goal:** Reliable, verifiable payment flow with MoMo as the default UX.

### Tasks

#### Paystack Integration
- [x] Add Paystack SDK / HTTP client to backend
- [x] Store Paystack secret key in environment config (never in code)
- [x] Implement payment initiation endpoint (returns Paystack payment URL or reference)
- [x] Implement Paystack webhook handler (verify signature, process events)
- [x] Payment verification endpoint (poll fallback for webhook failures)
- [x] Map Paystack payment status to internal `Payment` status

#### MoMo-First UX
- [x] Default payment method set to Mobile Money on all payment flows
- [x] MoMo phone number capture field (pre-filled from customer profile if available)
- [x] USSD push prompt flow (customer approves on phone)
- [x] Display "waiting for MoMo approval" state

#### Payment Model
- [x] `Payment` entity: order, amount, currency, method, status, paystack reference, timestamps
- [x] Payment status: `INITIATED`, `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`, `VOIDED`
- [x] Link payment to order; order moves to `CONFIRMED` on payment success

#### Retry & Recovery
- [x] Retry failed payment endpoint (re-initiates with same order)
- [x] Idempotency key to prevent duplicate charges
- [x] Payment recovery screen (show last failed payment, offer retry or new method)

#### Receipts
- [x] Generate receipt on payment success (PDF or structured JSON)
- [x] Receipt retrieval endpoint by order/payment ID

---

### Tests — Phase 5

#### Payment initiation tests
- [x] `givenValidOrder_whenInitiateMoMoPayment_then201WithPaystackReference`
- [x] `givenSameIdempotencyKey_whenInitiatePaymentTwice_thenSamePaymentReturnedNoDuplicate`
- [x] `givenDifferentIdempotencyKey_whenInitiatePaymentForSameOrder_then201NewPayment`
- [x] `givenNoPaystackKey_whenInitiatePayment_thenConfigurationErrorNotExposedToClient`

#### Webhook tests
- [x] `givenValidPaystackSignature_whenSuccessWebhook_thenPaymentStatusSuccess`
- [x] `givenValidPaystackSignature_whenSuccessWebhook_thenOrderStatusChangedToConfirmed`
- [x] `givenValidPaystackSignature_whenFailedWebhook_thenPaymentStatusFailed`
- [x] `givenInvalidSignature_whenWebhookReceived_then400Rejected`
- [x] `givenMissingSignatureHeader_whenWebhookReceived_then400Rejected`
- [x] `givenDuplicateWebhookEvent_whenReceivedTwice_thenProcessedOnlyOnce`

#### Payment status tests
- [x] `givenInitiatedPayment_whenPolled_thenStatusIsInitiated`
- [x] `givenSuccessfulPayment_whenGetPayment_thenStatusIsSuccessWithPaidAtTimestamp`
- [x] `givenFailedPayment_whenRetried_then201NewPaymentWithNewReference`

#### Receipt tests
- [x] `givenSuccessfulPayment_whenGetReceipt_then200WithCorrectOrderTotalsAndPaymentMethod`
- [x] `givenNoPayment_whenGetReceipt_then404`

#### Real-time payment tests
- [x] `givenSubscribedToPaymentTopic_whenWebhookSuccess_thenWebSocketReceivesPaymentStatusChangedEvent`
- [x] `givenSubscribedToPaymentTopic_whenWebhookFailed_thenWebSocketReceivesPaymentFailedEvent`

### Deliverables
- [x] All Phase 5 tests passing in CI (Paystack sandbox)
- [x] Webhook signature verification confirmed
- [x] `documentation/reports/phase-5-report.md`

---

## Phase 6 — Staff Web App (Next.js)

**Goal:** Staff-facing POS and management interface.

### Tasks

#### Setup
- [x] Initialize Next.js project (App Router, TypeScript)
- [x] Configure API client (Axios/fetch with auth interceptors)
- [x] Set up state management (Zustand or React Query)
- [x] Configure environment variables for API base URL

#### Authentication
- [x] Login page (role-aware redirect: Admin → dashboard, Cashier → POS)
- [x] JWT token storage and refresh logic
- [x] Protected route wrapper
- [x] Manager PIN modal component (reusable, triggered for guarded actions)

#### POS (Point of Sale)
- [x] Menu browser (categories + items, search)
- [x] Add to cart, update quantities, remove items
- [x] Order type selector (dine-in / pickup / delivery)
- [x] Table selector for dine-in
- [x] Submit order to backend
- [x] Payment trigger (initiate Paystack, show pending state)
- [x] Order confirmation screen with receipt option

#### Table Management
- [x] Table map/grid view with live status colours
- [x] Click table to view active order(s)
- [x] Assign / release table
- [x] QR code display per table

#### Order Management
- [x] Active orders list (real-time updates)
- [x] Update order status (confirm, preparing, ready, complete)
- [x] Cancel order (PIN modal)

#### Financial Controls (Manager+)
- [x] Apply discount to order (PIN required)
- [x] Void order (PIN required)
- [x] Issue refund (PIN required, reason capture)
- [x] Daily reconciliation summary view

#### Receipts
- [x] View receipt per order
- [x] Print receipt button
- [x] Send receipt via WhatsApp (Phase 8 integration point)

#### Admin Panel
- [x] User management (create/edit/deactivate staff accounts)
- [x] Menu management (CRUD for categories and items)
- [x] Role assignment

---

### Tests — Phase 6

#### Auth / routing tests
- [x] `givenCashierLogin_whenAuthenticated_thenRedirectedToPosPage`
- [x] `givenAdminLogin_whenAuthenticated_thenRedirectedToDashboard`
- [x] `givenUnauthenticated_whenVisitProtectedRoute_thenRedirectedToLogin`
- [x] `givenExpiredAccessToken_whenApiCallMade_thenRefreshCalledAndRequestRetried`

#### PIN modal tests
- [x] `givenCashierClicksDiscount_whenRendered_thenPinModalAppears`
- [x] `givenCorrectManagerPin_whenEnteredInModal_thenDiscountAppliedAndModalCloses`
- [x] `givenWrongPin_whenEnteredInModal_thenErrorMessageShownModalStaysOpen`
- [x] `givenLockedPin_whenModalOpened_thenLockedStateMessageShown`

#### POS tests
- [x] `givenMenuLoaded_whenItemAddedToCart_thenQuantityAndTotalUpdated`
- [x] `givenItemInCart_whenQuantityIncremented_thenTotalRecalculated`
- [x] `givenItemInCart_whenRemoved_thenCartEmpty`
- [x] `givenDineInSelected_whenTableChosen_thenTableIdIncludedInOrderPayload`
- [x] `givenCompletedCart_whenOrderSubmitted_thenApiCalledAndConfirmationScreenShown`
- [x] `givenOrderSubmitted_whenPaymentInitiated_thenPendingStateShown`

#### Table management tests
- [x] `givenTableList_whenRendered_thenAvailableTablesShowGreenOccupiedShowRed`
- [x] `givenOccupiedTable_whenClicked_thenActiveOrderListShown`
- [x] `givenWebSocketEvent_whenTableStatusChanged_thenTableMapUpdatesWithoutReload`

#### Order management tests
- [x] `givenActiveOrdersList_whenWebSocketReceivesNewOrder_thenOrderAppearsInListWithoutReload`
- [x] `givenOrder_whenStatusAdvanced_thenUpdatedStatusShownInList`
- [x] `givenCashierClicksCancelOrder_whenConfirmed_thenPinModalShown`

#### Financial controls tests
- [x] `givenDiscountApplied_whenViewOrder_thenDiscountAmountAndNewTotalShown`
- [x] `givenRefundApplied_whenViewReceipt_thenRefundedAmountAndStatusShown`
- [x] `givenReconciliationView_whenRendered_thenTotalsMatchExpectedSums`

### Deliverables
- [x] All Phase 6 tests passing in CI
- [x] PIN guard verified on all financial actions
- [x] `documentation/reports/phase-6-report.md`

---

## Phase 7 — Customer Mobile App (Expo)

**Goal:** Customer-facing ordering, tracking, and reservations.

### Tasks

#### Setup
- [x] Initialize Expo project (TypeScript, Expo Router)
- [x] Configure API client with auth
- [x] Push notification setup (Expo Notifications)

#### Onboarding & Auth
- [x] Phone number registration (OTP via SMS or WhatsApp)
- [x] Login / guest checkout option
- [x] Profile screen (name, phone, saved MoMo number)

#### Menu & Ordering
- [x] Menu browse (categories, item details, images)
- [x] Add to cart, quantity control
- [x] Order type selection: pickup / delivery / dine-in
- [x] Delivery address entry
- [x] Pickup time selection
- [x] QR scan → dine-in table link

#### Group Ordering
- [x] Create group session (share code/link)
- [x] Join group session by code
- [x] Live view of group cart (other participants' items)
- [x] Ready / submit trigger

#### Payment
- [x] MoMo-first payment screen (phone number, amount, confirm)
- [x] "Waiting for MoMo approval" live state
- [x] Payment success / failure screens
- [x] Retry failed payment
- [x] View receipt

#### Order Tracking
- [x] Live order status screen (push + polling fallback)
- [x] Delivery tracking state
- [x] Order history list
- [x] Reorder from history

#### Reservations
- [x] Browse available tables and time slots
- [x] Make reservation (name, party size, date/time)
- [x] View / cancel my reservations

#### Offline Support
- [x] Cache menu for offline browsing
- [x] Queue order submission when offline (sync on reconnect)
- [x] Show offline banner

---

### Tests — Phase 7

#### Auth tests
- [x] `givenValidPhone_whenRegister_thenOtpSentAndRegistrationPending`
- [x] `givenCorrectOtp_whenVerified_thenTokenIssuedAndUserLoggedIn`
- [x] `givenGuestCheckout_whenOrderCreated_thenSessionTokenUsedWithNoRole`

#### Menu & cart tests
- [x] `givenMenuLoaded_whenCategoryTapped_thenItemsForCategoryShown`
- [x] `givenItemDetailOpen_whenAddToCart_thenCartBadgeIncremented`
- [x] `givenItemInCart_whenQuantitySetToZero_thenItemRemovedFromCart`

#### Offline tests
- [x] `givenOfflineDevice_whenMenuOpened_thenCachedMenuShownWithStaleWarning`
- [x] `givenOfflineDevice_whenOrderSubmitted_thenOrderQueuedLocallyWithQueuedStatus`
- [x] `givenQueuedOrder_whenDeviceReconnects_thenOrderSubmittedToServerAndStatusUpdated`
- [x] `givenOfflineDevice_whenPaymentAttempted_thenBlockedWithInternetRequiredMessage`

#### QR / dine-in tests
- [x] `givenValidQrCode_whenScanned_thenTableInfoShownAndLinkedToCart`
- [x] `givenInvalidQrCode_whenScanned_thenErrorMessageShown`

#### Payment tests
- [x] `givenPaymentScreen_whenRendered_thenMobileMoneyIsDefaultSelectedMethod`
- [x] `givenMoMoPaymentInitiated_whenPending_thenWaitingForApprovalStateShown`
- [x] `givenPaystackSuccessEvent_whenReceived_thenOrderTrackingScreenShown`
- [x] `givenPaystackFailedEvent_whenReceived_thenFailureScreenWithRetryOptionShown`
- [x] `givenFailedPayment_whenRetryTapped_thenNewPaymentInitiated`

#### Group order tests (mobile)
- [x] `givenGroupSessionCreated_whenCodeShared_thenSecondDeviceJoinsAndSeesSharedCart`
- [x] `givenTwoParticipants_whenBothAddItems_thenGroupTotalReflectsAllItems`
- [x] `givenGroupCart_whenHostSubmits_thenSingleOrderCreatedAndAllParticipantsNotified`

#### Order tracking tests
- [x] `givenActiveOrder_whenStatusChangesToPreparing_thenTrackingScreenUpdatesWithoutManualRefresh`
- [x] `givenCompletedOrder_whenViewHistory_thenOrderAppearsInHistoryList`
- [x] `givenHistoryOrder_whenReorderTapped_thenCartPrePopulatedWithSameItems`

#### Reservation tests (mobile)
- [x] `givenAvailableSlot_whenReservationCreated_thenConfirmationShown`
- [x] `givenMyReservation_whenCancelTapped_then204AndRemovedFromList`

### Deliverables
- [x] All Phase 7 tests passing (unit + Maestro E2E on iOS and Android)
- [x] Offline queue sync verified end-to-end
- [x] `documentation/reports/phase-7-report.md`

---

## Phase 8 — Ghana-Specific Features

**Goal:** Localized features that match Ghanaian market expectations.

### Tasks

#### WhatsApp Integration
- [x] Integrate WhatsApp Business API (or Twilio WhatsApp)
- [x] Send order confirmation via WhatsApp
- [x] Send receipt link via WhatsApp
- [x] Send order status updates via WhatsApp
- [x] Include reorder deep-link in WhatsApp receipt

#### QR Table Ordering
- [x] Print-ready QR code per table (PDF export for manager)
- [x] Mobile app QR scanner (links session to table automatically)
- [x] Staff view shows which tables have active mobile sessions

#### Loyalty & Promotions System
- [x] Loyalty points accrual on every payment (configurable rate)
- [x] Redeem points at checkout (partial or full payment)
- [x] Promo code model (code, discount type: flat/%, min order, expiry, usage limit)
- [x] Apply promo code at checkout
- [x] "Buy X get Y" offer model and engine
- [x] Loyalty points balance on customer profile screen

---

### Tests — Phase 8

#### WhatsApp tests
- [x] `givenSuccessfulPayment_whenProcessed_thenWhatsAppOrderConfirmationSent`
- [x] `givenReceipt_whenGenerated_thenWhatsAppMessageContainsReceiptLinkAndReorderLink`
- [x] `givenOrderStatusChangedToReady_whenUpdated_thenWhatsAppStatusUpdateSent`
- [x] `givenWhatsAppApiDown_whenMessageFails_thenErrorLoggedAndOrderNotAffected`

#### QR table ordering tests
- [x] `givenTableQrCode_whenExportedAsPdf_thenPdfContainsCorrectTableNumber`
- [x] `givenMobileQrScan_whenTableLinked_thenStaffViewShowsTableHasActiveMobileSession`
- [x] `givenTableSessionClosed_whenStaffViews_thenActiveMobileSessionBadgeRemoved`

#### Loyalty tests
- [x] `givenPaymentSuccess_whenProcessed_thenPointsAccruedAtConfiguredRateForCustomer`
- [x] `givenCustomerWith200Points_whenRedeem100Points_thenOrderTotalReducedByEquivalentAmount`
- [x] `givenRedeemMorePointsThanBalance_whenAttempted_then400`
- [x] `givenLoyaltyTransaction_whenViewHistory_thenEarnAndRedeemEntriesShown`
- [x] `givenCustomerProfile_whenViewed_thenCurrentLoyaltyBalanceShown`

#### Promo code tests
- [x] `givenValidPromoCode_whenApplied_thenCorrectDiscountAppliedToOrderTotal`
- [x] `givenPercentagePromo_whenApplied_thenDiscountIsPercentageOfSubtotal`
- [x] `givenFlatPromo_whenApplied_thenFixedAmountDeducted`
- [x] `givenOrderBelowMinAmount_whenPromoApplied_then400MinimumNotMet`
- [x] `givenExpiredPromoCode_whenApplied_then400Expired`
- [x] `givenPromoAtUsageLimit_whenApplied_then400LimitReached`
- [x] `givenInactivePromoCode_whenApplied_then400Invalid`

#### Buy X Get Y tests
- [x] `givenBuyXGetYOffer_whenCustomerOrdersMeetsCriteria_thenFreeItemAddedToOrder`
- [x] `givenBuyXGetYOffer_whenCriteriaNotMet_thenNoFreeItemAdded`

### Deliverables
- [x] All Phase 8 tests passing in CI
- [x] WhatsApp receipt delivery tested in staging
- [x] Loyalty accrual and redemption verified end-to-end
- [x] `documentation/reports/phase-8-report.md`

---

## Phase 9 — Financial Controls & Reconciliation

**Goal:** Airtight financial audit trail with manager-gated overrides.

### Tasks

#### Refunds
- [x] Refund endpoint (manager PIN, reason, amount — partial or full)
- [x] Trigger Paystack refund API
- [x] Update payment status to `REFUNDED`
- [x] Log refund event to audit log
- [x] Display refund status to cashier/customer

#### Voids
- [x] Void order endpoint (manager PIN, before payment processed)
- [x] Void releases table if dine-in
- [x] Void logged to audit log

#### Discounts
- [x] Manual discount (manager PIN, amount or %, reason)
- [x] Discount applied to order total before payment
- [x] Discount logged to audit log

#### End-of-Day Reconciliation
- [x] Summary report: total sales, total MoMo, total card, total cash, total refunds, total voids, total discounts
- [x] Per-cashier breakdown
- [x] Export to CSV / PDF
- [x] Manager sign-off endpoint (marks day as reconciled)

#### Audit Log Review
- [x] Admin UI to browse audit log (filter by action type, user, date)
- [x] Exportable audit log

---

### Tests — Phase 9

#### Refund tests
- [x] `givenNoOverrideToken_whenRefundAttempted_then403`
- [x] `givenValidManagerOverrideToken_whenFullRefund_thenPaystackRefundCalledAndPaymentStatusRefunded`
- [x] `givenValidManagerOverrideToken_whenPartialRefund_thenPaymentStatusPartiallyRefundedCorrectAmount`
- [x] `givenRefundAmountExceedsPaymentAmount_whenAttempted_then400`
- [x] `givenSuccessfulRefund_thenRefundEventWrittenToAuditLog`
- [x] `givenAlreadyRefundedPayment_whenRefundedAgain_then400`

#### Void tests
- [x] `givenNoOverrideToken_whenVoidAttempted_then403`
- [x] `givenValidManagerOverrideToken_whenOrderVoided_thenOrderStatusVoided`
- [x] `givenDineInOrder_whenVoided_thenLinkedTableStatusChangedToAvailable`
- [x] `givenAlreadyCompletedOrder_whenVoidAttempted_then400`
- [x] `givenSuccessfulVoid_thenVoidEventWrittenToAuditLog`

#### Discount tests
- [x] `givenNoOverrideToken_whenDiscountAttempted_then403`
- [x] `givenValidManagerOverrideToken_whenFlatDiscountApplied_thenOrderTotalReducedByAmount`
- [x] `givenValidManagerOverrideToken_whenPercentageDiscountApplied_thenOrderTotalReducedByPercentage`
- [x] `givenDiscountExceedsOrderTotal_whenApplied_then400`
- [x] `givenSuccessfulDiscount_thenDiscountEventWrittenToAuditLog`

#### Reconciliation tests
- [x] `givenDayWithKnownTransactions_whenReconciliationFetched_thenTotalsMatchSumOfPayments`
- [x] `givenDayWithRefunds_whenReconciliationFetched_thenRefundTotalCorrect`
- [x] `givenDayWithVoids_whenReconciliationFetched_thenVoidTotalCorrect`
- [x] `givenNoOverrideToken_whenSignOffAttempted_then403`
- [x] `givenValidManagerOverrideToken_whenSignOff_thenSignedByAndSignedAtPopulated`
- [x] `givenAlreadySignedOffDay_whenSignOffAgain_then409`

#### Audit log tests
- [x] `givenActionFilter_whenAuditLogQueried_thenOnlyMatchingActionsReturned`
- [x] `givenDateFilter_whenAuditLogQueried_thenOnlyEventsInRangeReturned`
- [x] `givenCashierToken_whenAuditLogQueried_then403`

### Deliverables
- [x] All Phase 9 tests passing in CI
- [x] Reconciliation totals verified against test transaction data
- [x] `documentation/reports/phase-9-report.md`

---

## Phase 10 — Analytics, Testing & Deployment

**Goal:** Production-ready system with monitoring and verified quality.

### Tasks

#### Analytics & Reports
- [x] Sales dashboard (daily, weekly, monthly revenue charts)
- [x] Top-selling items report
- [x] Peak hours heatmap
- [x] Customer retention / repeat order rate
- [x] Loyalty program stats (points issued, redeemed)
- [x] Delivery performance (avg delivery time)

#### Testing
- [x] Backend unit tests — service layer (JUnit 5, Mockito)
- [x] Backend integration tests — API layer (Spring Boot Test, Testcontainers)
- [x] Payment webhook integration test (mock Paystack events)
- [x] Frontend unit tests (Vitest / Jest + React Testing Library)
- [x] Expo mobile E2E tests (Detox or Maestro)
- [x] Load test critical endpoints (order creation, payment initiation)
- [x] Security review: OWASP Top 10 checklist

#### Deployment
- [x] Dockerize Spring Boot backend
- [x] Dockerize Next.js staff app
- [x] Docker Compose for local dev (backend + PostgreSQL + Redis if used)
- [x] CI/CD pipeline (GitHub Actions or similar):
  - [x] Lint, test, build on every PR
  - [x] Deploy to staging on merge to `develop`
  - [x] Deploy to prod on merge to `main`
- [x] Production PostgreSQL setup (managed DB, automated backups)
- [x] HTTPS / TLS configured
- [x] Environment secrets managed (not in code)
- [x] Health check endpoints (`/actuator/health`)
- [x] Error monitoring (Sentry or equivalent)
- [x] Uptime monitoring

#### Documentation
- [x] API reference (Swagger/OpenAPI auto-generated)
- [x] Staff onboarding guide
- [x] Deployment runbook
- [x] Environment variable reference

---

### Tests — Phase 10

#### Analytics tests
- [x] `givenKnownOrders_whenSalesDailyReportFetched_thenRevenueMatchesSumOfSuccessfulPayments`
- [x] `givenKnownOrders_whenTopItemsReportFetched_thenItemsRankedByQuantitySoldDescending`
- [x] `givenKnownOrders_whenPeakHoursFetched_thenHourWithMostOrdersRanksFirst`
- [x] `givenCustomerWithTwoOrders_whenRetentionStatsFetched_thenCountedAsRepeatCustomer`

#### Full regression gate
- [x] All Phase 2 tests passing
- [x] All Phase 3 tests passing
- [x] All Phase 4 tests passing
- [x] All Phase 5 tests passing
- [x] All Phase 6 tests passing
- [x] All Phase 7 tests passing
- [x] All Phase 8 tests passing
- [x] All Phase 9 tests passing

#### Load tests
- [x] `50ConcurrentOrderCreationRequests_allSucceedWithin2Seconds`
- [x] `50ConcurrentPaymentInitiationRequests_allSucceedWithin3Seconds`
- [x] `WebSocketWith100SimultaneousSubscribers_orderEventDeliveredToAll`

#### Security tests
- [x] SQL injection attempt on search endpoints → sanitized, no data leaked
- [x] XSS payload in order notes → escaped in receipt output
- [x] IDOR: customer cannot fetch another customer's order by guessing UUID
- [x] Brute-force login → rate limited after 10 attempts
- [x] Paystack webhook without signature header → rejected

### Deliverables
- [x] All tests green in CI (zero failures, zero skipped)
- [x] Load test results documented
- [x] Security checklist signed off
- [x] App deployed to staging with smoke tests passing
- [x] `documentation/reports/phase-10-report.md`

---

## Overall Progress Tracker

| Phase | Title                        | Status       |
|-------|------------------------------|--------------|
| 1     | Planning & Design            | **Complete** |
| 2     | Backend Core                 | **Complete** |
| 3     | Menu & Table Management      | **Complete** |
| 4     | Orders                       | **Complete** |
| 5     | Payments                     | **Complete** |
| 6     | Staff Web App (Next.js)      | **Complete** |
| 7     | Customer Mobile App (Expo)   | **Complete** |
| 8     | Ghana-Specific Features      | **Complete** |
| 9     | Financial Controls           | **Complete** |
| 10    | Analytics, Testing, Deploy   | **Complete** |

> Update status values to: `In Progress` · `Blocked` · `Complete`

---

## User Story Coverage Map

| Epic                | Phase(s)       |
|---------------------|----------------|
| Payments (MoMo)     | 5, 6, 7        |
| Group Ordering      | 4, 7           |
| Walk-In + QR Mobile | 3, 7, 8        |
| WhatsApp            | 8              |
| Loyalty & Promos    | 8              |
| Core Ordering       | 4, 6, 7        |
| Staff Operations    | 6              |
| Financial Control   | 9              |
| Reliability         | 4, 7, 10       |

---

*Last updated: 2026-03-24*



