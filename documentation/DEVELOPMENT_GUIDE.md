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
> - [ ] `PROJECT_PROGRESS.md` updated
> - [ ] `CHANGELOG.md` updated
> - [ ] Phase report created in `reports/phase-N-report.md`

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
- [x] `docs/DATABASE_SCHEMA.sql` — PostgreSQL V1 migration (17 tables)
- [x] `docs/ROLES_AND_PERMISSIONS.md` — Full role/permission matrix
- [x] `docs/API_SPEC.md` — REST API spec (~55 endpoints across 12 groups)
- [x] `docs/WEBSOCKET_EVENTS.md` — STOMP topics and SSE fallback
- [x] `docs/OFFLINE_STRATEGY.md` — Offline scope, cache, sync queue
- [x] `reports/phase-1-report.md`

---

## Phase 2 — Backend Core (Auth, Roles, PIN)

**Goal:** Secure foundation that all other phases depend on.

### Tasks

#### Project Setup
- [ ] Initialize Spring Boot project (Maven/Gradle)
- [ ] Configure PostgreSQL datasource, connection pool (HikariCP)
- [ ] Set up Flyway/Liquibase for database migrations
- [ ] Configure environment profiles (dev, staging, prod)
- [ ] Set up structured logging (JSON logs for prod)

#### Authentication
- [ ] Implement JWT-based auth (access + refresh tokens)
- [ ] User registration & login endpoints
- [ ] Token refresh endpoint
- [ ] Secure password hashing (BCrypt)
- [ ] Logout / token invalidation

#### Roles & Permissions
- [ ] Implement roles: `ADMIN`, `MANAGER`, `CASHIER`, `CUSTOMER`
- [ ] Spring Security role-based method/endpoint guards
- [ ] Role assignment endpoints (Admin only)

#### Manager PIN System
- [ ] PIN creation and hashing for manager accounts
- [ ] PIN verification endpoint (returns short-lived override token)
- [ ] PIN required guard for: refunds, voids, discounts
- [ ] PIN lockout after N failed attempts (configurable)

#### Audit Logging
- [ ] Audit log table for all sensitive actions
- [ ] Log actor, action, target entity, timestamp, IP

---

### Tests — Phase 2

Every test below must be written and green before the corresponding task is checked.

#### Authentication tests
- [ ] `givenValidData_whenRegister_then201AndUserCreatedWithCustomerRole`
- [ ] `givenDuplicatePhone_whenRegister_then409`
- [ ] `givenDuplicateEmail_whenRegister_then409`
- [ ] `givenCorrectCredentials_whenLogin_then200WithAccessAndRefreshTokens`
- [ ] `givenWrongPassword_whenLogin_then401`
- [ ] `givenUnknownPhone_whenLogin_then401`
- [ ] `givenValidRefreshToken_whenRefresh_then200WithNewAccessToken`
- [ ] `givenRevokedRefreshToken_whenRefresh_then401`
- [ ] `givenExpiredRefreshToken_whenRefresh_then401`
- [ ] `givenNoToken_whenAccessProtectedEndpoint_then401`
- [ ] `givenExpiredAccessToken_whenAccessProtectedEndpoint_then401`
- [ ] `givenValidLogout_whenRefreshAfterwards_then401`

#### Role & permission tests
- [ ] `givenAdminToken_whenAccessAdminOnlyEndpoint_then200`
- [ ] `givenCashierToken_whenAccessAdminOnlyEndpoint_then403`
- [ ] `givenManagerToken_whenAccessAdminOnlyEndpoint_then403`
- [ ] `givenCustomerToken_whenAccessStaffEndpoint_then403`
- [ ] `givenAdminToken_whenAssignRole_then200AndRoleUpdated`
- [ ] `givenManagerToken_whenAssignRole_then403`

#### Manager PIN tests
- [ ] `givenManagerSetsPin_when204_thenPinHashedAndStored`
- [ ] `givenCorrectPin_whenVerify_then200WithScopedOverrideToken`
- [ ] `givenWrongPin_whenVerify_then401AndFailCountIncremented`
- [ ] `givenFiveWrongPins_whenVerify_then423AndAccountLocked`
- [ ] `givenLockedPin_whenVerify_then423WithLockedUntilTimestamp`
- [ ] `givenDiscountOverrideToken_whenUsedForVoidAction_then403`
- [ ] `givenOverrideToken_whenUsedAfter5Minutes_then401Expired`
- [ ] `givenNonManagerRole_whenSetPin_then403`

#### Audit log tests
- [ ] `givenSuccessfulLogin_thenUserLoginEventWrittenToAuditLog`
- [ ] `givenFailedPinAttempt_thenPinFailedEventWrittenToAuditLog`
- [ ] `givenPinLockout_thenPinLockedEventWrittenToAuditLog`
- [ ] `givenRoleAssigned_thenRoleAssignedEventWrittenToAuditLog`

### Deliverables
- [ ] All Phase 2 tests passing in CI
- [ ] `reports/phase-2-report.md`

---

## Phase 3 — Menu & Table Management

**Goal:** Staff can fully manage the menu and tables; customers can browse.

### Tasks

#### Menu
- [ ] `Category` CRUD (name, description, display order, active flag)
- [ ] `MenuItem` CRUD (name, description, price, category, image URL, available flag)
- [ ] Menu item availability toggle (e.g., sold out)
- [ ] Menu list endpoint (public, no auth) — sorted by category
- [ ] Menu search/filter endpoint

#### Tables
- [ ] `Table` CRUD (number, capacity, floor/zone label, status: available/occupied/reserved)
- [ ] Table status update endpoint
- [ ] QR code generation per table (returns unique table token/URL)
- [ ] Endpoint to validate QR table token (used by mobile app on scan)

#### Reservations
- [ ] `Reservation` model (customer name/phone, table, date/time, party size, status)
- [ ] Create reservation endpoint (staff or customer)
- [ ] List reservations (by date, by table)
- [ ] Confirm / cancel reservation endpoints
- [ ] Basic conflict check (prevent double-booking same table/time)

---

### Tests — Phase 3

#### Menu category tests
- [ ] `givenAdminToken_whenCreateCategory_then201`
- [ ] `givenCashierToken_whenCreateCategory_then403`
- [ ] `givenNoAuth_whenListCategories_then200PublicAccess`
- [ ] `givenMultipleCategories_whenList_thenReturnedSortedByDisplayOrder`
- [ ] `givenAdminToken_whenDeleteCategory_then204`
- [ ] `givenManagerToken_whenDeleteCategory_then403`

#### Menu item tests
- [ ] `givenAdminToken_whenCreateMenuItem_then201WithCorrectFields`
- [ ] `givenNoAuth_whenListMenuItems_then200`
- [ ] `givenAvailableFalse_whenListPublicMenu_thenItemExcluded`
- [ ] `givenSearchQuery_whenListMenuItems_thenOnlyMatchingItemsReturned`
- [ ] `givenManagerToken_whenToggleAvailabilityToFalse_thenItemUnavailableInPublicList`
- [ ] `givenCashierToken_whenToggleAvailability_then200`
- [ ] `givenCustomerToken_whenToggleAvailability_then403`

#### Table tests
- [ ] `givenAdminToken_whenCreateTable_then201WithUniqueQrToken`
- [ ] `givenTwoTables_whenListTables_thenBothReturnedWithStatus`
- [ ] `givenCashierToken_whenUpdateTableStatus_then200`
- [ ] `givenCustomerToken_whenUpdateTableStatus_then403`
- [ ] `givenValidQrToken_whenScanTable_then200WithTableInfo`
- [ ] `givenInvalidQrToken_whenScanTable_then404`
- [ ] `givenDuplicateTableNumber_whenCreate_then409`

#### Reservation tests
- [ ] `givenAvailableTableAndTime_whenCreateReservation_then201`
- [ ] `givenSameTableAndOverlappingTime_whenCreateReservation_then409`
- [ ] `givenNoAuth_whenCreateReservationWithNameAndPhone_then201GuestReservation`
- [ ] `givenStaffToken_whenConfirmReservation_then200StatusConfirmed`
- [ ] `givenCustomerToken_whenCancelOwnReservation_then204`
- [ ] `givenCustomerToken_whenCancelOtherCustomerReservation_then403`
- [ ] `givenDateFilter_whenListReservations_thenOnlyReservationsForThatDateReturned`

### Deliverables
- [ ] All Phase 3 tests passing in CI
- [ ] QR token generation and validation working
- [ ] `reports/phase-3-report.md`

---

## Phase 4 — Orders

**Goal:** Full order lifecycle for all order types.

### Tasks

#### Order Core
- [ ] `Order` model with type enum: `PICKUP`, `DELIVERY`, `DINE_IN`
- [ ] `OrderItem` model (menu item snapshot: name, price at time of order)
- [ ] Order status lifecycle: `PENDING → CONFIRMED → PREPARING → READY → COMPLETED / CANCELLED`
- [ ] Create order endpoint (staff and customer)
- [ ] Update order status endpoint (staff only, role-gated)
- [ ] Cancel order endpoint (with reason, manager PIN if order is confirmed)
- [ ] Order history endpoint (by customer, by date range)

#### Pickup Orders
- [ ] Pickup time slot selection
- [ ] Pickup code generation (shown to customer, entered by cashier)

#### Delivery Orders
- [ ] Delivery address capture
- [ ] Delivery status sub-states: `OUT_FOR_DELIVERY`, `DELIVERED`
- [ ] Estimated delivery time field

#### Dine-In Orders
- [ ] Link order to table (via table token or staff table selection)
- [ ] Support multiple order submissions per table session
- [ ] Close table / end session endpoint

#### Group Ordering
- [ ] Create group order session (returns session code)
- [ ] Join group session endpoint
- [ ] Each participant adds items to shared cart
- [ ] View combined group order
- [ ] Finalize group order (converts to single order record)
- [ ] Track which items belong to which participant (for split pay)

#### Real-Time Updates
- [ ] WebSocket or SSE channel per order (status push to customer)
- [ ] Kitchen/staff notification on new order
- [ ] Order status push to all participants in group order

---

### Tests — Phase 4

#### Order core tests
- [ ] `givenCustomerToken_whenCreateDineInOrder_then201WithStatusPending`
- [ ] `givenCustomerToken_whenCreatePickupOrder_then201WithPickupCodeGenerated`
- [ ] `givenCustomerToken_whenCreateDeliveryOrderWithAddress_then201`
- [ ] `givenDeliveryOrderWithNoAddress_whenCreate_then400`
- [ ] `givenCashierToken_whenUpdateOrderStatusToConfirmed_then200`
- [ ] `givenCustomerToken_whenUpdateOrderStatus_then403`
- [ ] `givenCustomerToken_whenCancelOwnPendingOrder_then204`
- [ ] `givenCustomerToken_whenCancelConfirmedOrderWithoutPin_then403`
- [ ] `givenValidOverrideToken_whenCancelConfirmedOrder_then200WithStatusCancelled`
- [ ] `givenCustomerToken_whenCancelOtherCustomerOrder_then403`
- [ ] `givenDateRangeFilter_whenListOrders_thenOnlyOrdersWithinRangeReturned`

#### Price snapshot test
- [ ] `givenExistingOrder_whenMenuItemPriceChangedAfterwards_thenOrderItemStillShowsOriginalPrice`

#### Pickup tests
- [ ] `givenPickupOrder_whenCreated_thenUniquePickupCodeAssigned`
- [ ] `givenPickupCode_whenCashierEntersCode_thenMatchingOrderReturned`

#### Delivery tests
- [ ] `givenDeliveryOrder_whenStatusUpdatedToOutForDelivery_then200`
- [ ] `givenDeliveryOrder_whenStatusUpdatedToDelivered_then200`

#### Dine-in tests
- [ ] `givenValidTableToken_whenCreateDineInOrder_thenOrderLinkedToTable`
- [ ] `givenOccupiedTable_whenCreateSecondOrder_then201BothOrdersLinkedToTable`
- [ ] `givenDineInSession_whenCloseTable_thenTableStatusChangedToAvailable`

#### Group order tests
- [ ] `givenCustomerToken_whenCreateGroupSession_then201WithSessionCode`
- [ ] `givenSessionCode_whenSecondUserJoins_then200ParticipantAdded`
- [ ] `givenParticipant_whenAddItemsToGroupCart_thenGroupTotalUpdated`
- [ ] `givenGroupSession_whenNonHostTriesToFinalize_then403`
- [ ] `givenGroupSession_whenHostFinalizes_then201OrderWithAllParticipantItems`
- [ ] `givenFinalizedGroupOrder_whenViewOrderItems_thenEachItemShowsParticipantId`

#### Real-time tests
- [ ] `givenSubscribedToOrderTopic_whenOrderCreated_thenWebSocketReceivesOrderCreatedEvent`
- [ ] `givenSubscribedToOrderTopic_whenStatusUpdated_thenWebSocketReceivesStatusChangedEvent`
- [ ] `givenGroupSessionParticipants_whenItemAdded_thenAllParticipantsReceiveGroupCartUpdatedEvent`

### Deliverables
- [ ] All Phase 4 tests passing in CI
- [ ] Real-time status update verified via WebSocket integration test
- [ ] `reports/phase-4-report.md`

---

## Phase 5 — Payments (Paystack / MoMo-First)

**Goal:** Reliable, verifiable payment flow with MoMo as the default UX.

### Tasks

#### Paystack Integration
- [ ] Add Paystack SDK / HTTP client to backend
- [ ] Store Paystack secret key in environment config (never in code)
- [ ] Implement payment initiation endpoint (returns Paystack payment URL or reference)
- [ ] Implement Paystack webhook handler (verify signature, process events)
- [ ] Payment verification endpoint (poll fallback for webhook failures)
- [ ] Map Paystack payment status to internal `Payment` status

#### MoMo-First UX
- [ ] Default payment method set to Mobile Money on all payment flows
- [ ] MoMo phone number capture field (pre-filled from customer profile if available)
- [ ] USSD push prompt flow (customer approves on phone)
- [ ] Display "waiting for MoMo approval" state

#### Payment Model
- [ ] `Payment` entity: order, amount, currency, method, status, paystack reference, timestamps
- [ ] Payment status: `INITIATED`, `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`, `VOIDED`
- [ ] Link payment to order; order moves to `CONFIRMED` on payment success

#### Retry & Recovery
- [ ] Retry failed payment endpoint (re-initiates with same order)
- [ ] Idempotency key to prevent duplicate charges
- [ ] Payment recovery screen (show last failed payment, offer retry or new method)

#### Receipts
- [ ] Generate receipt on payment success (PDF or structured JSON)
- [ ] Receipt retrieval endpoint by order/payment ID

---

### Tests — Phase 5

#### Payment initiation tests
- [ ] `givenValidOrder_whenInitiateMoMoPayment_then201WithPaystackReference`
- [ ] `givenSameIdempotencyKey_whenInitiatePaymentTwice_thenSamePaymentReturnedNoDuplicate`
- [ ] `givenDifferentIdempotencyKey_whenInitiatePaymentForSameOrder_then201NewPayment`
- [ ] `givenNoPaystackKey_whenInitiatePayment_thenConfigurationErrorNotExposedToClient`

#### Webhook tests
- [ ] `givenValidPaystackSignature_whenSuccessWebhook_thenPaymentStatusSuccess`
- [ ] `givenValidPaystackSignature_whenSuccessWebhook_thenOrderStatusChangedToConfirmed`
- [ ] `givenValidPaystackSignature_whenFailedWebhook_thenPaymentStatusFailed`
- [ ] `givenInvalidSignature_whenWebhookReceived_then400Rejected`
- [ ] `givenMissingSignatureHeader_whenWebhookReceived_then400Rejected`
- [ ] `givenDuplicateWebhookEvent_whenReceivedTwice_thenProcessedOnlyOnce`

#### Payment status tests
- [ ] `givenInitiatedPayment_whenPolled_thenStatusIsInitiated`
- [ ] `givenSuccessfulPayment_whenGetPayment_thenStatusIsSuccessWithPaidAtTimestamp`
- [ ] `givenFailedPayment_whenRetried_then201NewPaymentWithNewReference`

#### Receipt tests
- [ ] `givenSuccessfulPayment_whenGetReceipt_then200WithCorrectOrderTotalsAndPaymentMethod`
- [ ] `givenNoPayment_whenGetReceipt_then404`

#### Real-time payment tests
- [ ] `givenSubscribedToPaymentTopic_whenWebhookSuccess_thenWebSocketReceivesPaymentStatusChangedEvent`
- [ ] `givenSubscribedToPaymentTopic_whenWebhookFailed_thenWebSocketReceivesPaymentFailedEvent`

### Deliverables
- [ ] All Phase 5 tests passing in CI (Paystack sandbox)
- [ ] Webhook signature verification confirmed
- [ ] `reports/phase-5-report.md`

---

## Phase 6 — Staff Web App (Next.js)

**Goal:** Staff-facing POS and management interface.

### Tasks

#### Setup
- [ ] Initialize Next.js project (App Router, TypeScript)
- [ ] Configure API client (Axios/fetch with auth interceptors)
- [ ] Set up state management (Zustand or React Query)
- [ ] Configure environment variables for API base URL

#### Authentication
- [ ] Login page (role-aware redirect: Admin → dashboard, Cashier → POS)
- [ ] JWT token storage and refresh logic
- [ ] Protected route wrapper
- [ ] Manager PIN modal component (reusable, triggered for guarded actions)

#### POS (Point of Sale)
- [ ] Menu browser (categories + items, search)
- [ ] Add to cart, update quantities, remove items
- [ ] Order type selector (dine-in / pickup / delivery)
- [ ] Table selector for dine-in
- [ ] Submit order to backend
- [ ] Payment trigger (initiate Paystack, show pending state)
- [ ] Order confirmation screen with receipt option

#### Table Management
- [ ] Table map/grid view with live status colours
- [ ] Click table to view active order(s)
- [ ] Assign / release table
- [ ] QR code display per table

#### Order Management
- [ ] Active orders list (real-time updates)
- [ ] Update order status (confirm, preparing, ready, complete)
- [ ] Cancel order (PIN modal)

#### Financial Controls (Manager+)
- [ ] Apply discount to order (PIN required)
- [ ] Void order (PIN required)
- [ ] Issue refund (PIN required, reason capture)
- [ ] Daily reconciliation summary view

#### Receipts
- [ ] View receipt per order
- [ ] Print receipt button
- [ ] Send receipt via WhatsApp (Phase 8 integration point)

#### Admin Panel
- [ ] User management (create/edit/deactivate staff accounts)
- [ ] Menu management (CRUD for categories and items)
- [ ] Role assignment

---

### Tests — Phase 6

#### Auth / routing tests
- [ ] `givenCashierLogin_whenAuthenticated_thenRedirectedToPosPage`
- [ ] `givenAdminLogin_whenAuthenticated_thenRedirectedToDashboard`
- [ ] `givenUnauthenticated_whenVisitProtectedRoute_thenRedirectedToLogin`
- [ ] `givenExpiredAccessToken_whenApiCallMade_thenRefreshCalledAndRequestRetried`

#### PIN modal tests
- [ ] `givenCashierClicksDiscount_whenRendered_thenPinModalAppears`
- [ ] `givenCorrectManagerPin_whenEnteredInModal_thenDiscountAppliedAndModalCloses`
- [ ] `givenWrongPin_whenEnteredInModal_thenErrorMessageShownModalStaysOpen`
- [ ] `givenLockedPin_whenModalOpened_thenLockedStateMessageShown`

#### POS tests
- [ ] `givenMenuLoaded_whenItemAddedToCart_thenQuantityAndTotalUpdated`
- [ ] `givenItemInCart_whenQuantityIncremented_thenTotalRecalculated`
- [ ] `givenItemInCart_whenRemoved_thenCartEmpty`
- [ ] `givenDineInSelected_whenTableChosen_thenTableIdIncludedInOrderPayload`
- [ ] `givenCompletedCart_whenOrderSubmitted_thenApiCalledAndConfirmationScreenShown`
- [ ] `givenOrderSubmitted_whenPaymentInitiated_thenPendingStateShown`

#### Table management tests
- [ ] `givenTableList_whenRendered_thenAvailableTablesShowGreenOccupiedShowRed`
- [ ] `givenOccupiedTable_whenClicked_thenActiveOrderListShown`
- [ ] `givenWebSocketEvent_whenTableStatusChanged_thenTableMapUpdatesWithoutReload`

#### Order management tests
- [ ] `givenActiveOrdersList_whenWebSocketReceivesNewOrder_thenOrderAppearsInListWithoutReload`
- [ ] `givenOrder_whenStatusAdvanced_thenUpdatedStatusShownInList`
- [ ] `givenCashierClicksCancelOrder_whenConfirmed_thenPinModalShown`

#### Financial controls tests
- [ ] `givenDiscountApplied_whenViewOrder_thenDiscountAmountAndNewTotalShown`
- [ ] `givenRefundApplied_whenViewReceipt_thenRefundedAmountAndStatusShown`
- [ ] `givenReconciliationView_whenRendered_thenTotalsMatchExpectedSums`

### Deliverables
- [ ] All Phase 6 tests passing in CI
- [ ] PIN guard verified on all financial actions
- [ ] `reports/phase-6-report.md`

---

## Phase 7 — Customer Mobile App (Expo)

**Goal:** Customer-facing ordering, tracking, and reservations.

### Tasks

#### Setup
- [ ] Initialize Expo project (TypeScript, Expo Router)
- [ ] Configure API client with auth
- [ ] Push notification setup (Expo Notifications)

#### Onboarding & Auth
- [ ] Phone number registration (OTP via SMS or WhatsApp)
- [ ] Login / guest checkout option
- [ ] Profile screen (name, phone, saved MoMo number)

#### Menu & Ordering
- [ ] Menu browse (categories, item details, images)
- [ ] Add to cart, quantity control
- [ ] Order type selection: pickup / delivery / dine-in
- [ ] Delivery address entry
- [ ] Pickup time selection
- [ ] QR scan → dine-in table link

#### Group Ordering
- [ ] Create group session (share code/link)
- [ ] Join group session by code
- [ ] Live view of group cart (other participants' items)
- [ ] Ready / submit trigger

#### Payment
- [ ] MoMo-first payment screen (phone number, amount, confirm)
- [ ] "Waiting for MoMo approval" live state
- [ ] Payment success / failure screens
- [ ] Retry failed payment
- [ ] View receipt

#### Order Tracking
- [ ] Live order status screen (push + polling fallback)
- [ ] Delivery tracking state
- [ ] Order history list
- [ ] Reorder from history

#### Reservations
- [ ] Browse available tables and time slots
- [ ] Make reservation (name, party size, date/time)
- [ ] View / cancel my reservations

#### Offline Support
- [ ] Cache menu for offline browsing
- [ ] Queue order submission when offline (sync on reconnect)
- [ ] Show offline banner

---

### Tests — Phase 7

#### Auth tests
- [ ] `givenValidPhone_whenRegister_thenOtpSentAndRegistrationPending`
- [ ] `givenCorrectOtp_whenVerified_thenTokenIssuedAndUserLoggedIn`
- [ ] `givenGuestCheckout_whenOrderCreated_thenSessionTokenUsedWithNoRole`

#### Menu & cart tests
- [ ] `givenMenuLoaded_whenCategoryTapped_thenItemsForCategoryShown`
- [ ] `givenItemDetailOpen_whenAddToCart_thenCartBadgeIncremented`
- [ ] `givenItemInCart_whenQuantitySetToZero_thenItemRemovedFromCart`

#### Offline tests
- [ ] `givenOfflineDevice_whenMenuOpened_thenCachedMenuShownWithStaleWarning`
- [ ] `givenOfflineDevice_whenOrderSubmitted_thenOrderQueuedLocallyWithQueuedStatus`
- [ ] `givenQueuedOrder_whenDeviceReconnects_thenOrderSubmittedToServerAndStatusUpdated`
- [ ] `givenOfflineDevice_whenPaymentAttempted_thenBlockedWithInternetRequiredMessage`

#### QR / dine-in tests
- [ ] `givenValidQrCode_whenScanned_thenTableInfoShownAndLinkedToCart`
- [ ] `givenInvalidQrCode_whenScanned_thenErrorMessageShown`

#### Payment tests
- [ ] `givenPaymentScreen_whenRendered_thenMobileMoneyIsDefaultSelectedMethod`
- [ ] `givenMoMoPaymentInitiated_whenPending_thenWaitingForApprovalStateShown`
- [ ] `givenPaystackSuccessEvent_whenReceived_thenOrderTrackingScreenShown`
- [ ] `givenPaystackFailedEvent_whenReceived_thenFailureScreenWithRetryOptionShown`
- [ ] `givenFailedPayment_whenRetryTapped_thenNewPaymentInitiated`

#### Group order tests (mobile)
- [ ] `givenGroupSessionCreated_whenCodeShared_thenSecondDeviceJoinsAndSeesSharedCart`
- [ ] `givenTwoParticipants_whenBothAddItems_thenGroupTotalReflectsAllItems`
- [ ] `givenGroupCart_whenHostSubmits_thenSingleOrderCreatedAndAllParticipantsNotified`

#### Order tracking tests
- [ ] `givenActiveOrder_whenStatusChangesToPreparing_thenTrackingScreenUpdatesWithoutManualRefresh`
- [ ] `givenCompletedOrder_whenViewHistory_thenOrderAppearsInHistoryList`
- [ ] `givenHistoryOrder_whenReorderTapped_thenCartPrePopulatedWithSameItems`

#### Reservation tests (mobile)
- [ ] `givenAvailableSlot_whenReservationCreated_thenConfirmationShown`
- [ ] `givenMyReservation_whenCancelTapped_then204AndRemovedFromList`

### Deliverables
- [ ] All Phase 7 tests passing (unit + Maestro E2E on iOS and Android)
- [ ] Offline queue sync verified end-to-end
- [ ] `reports/phase-7-report.md`

---

## Phase 8 — Ghana-Specific Features

**Goal:** Localized features that match Ghanaian market expectations.

### Tasks

#### WhatsApp Integration
- [ ] Integrate WhatsApp Business API (or Twilio WhatsApp)
- [ ] Send order confirmation via WhatsApp
- [ ] Send receipt link via WhatsApp
- [ ] Send order status updates via WhatsApp
- [ ] Include reorder deep-link in WhatsApp receipt

#### QR Table Ordering
- [ ] Print-ready QR code per table (PDF export for manager)
- [ ] Mobile app QR scanner (links session to table automatically)
- [ ] Staff view shows which tables have active mobile sessions

#### Loyalty & Promotions System
- [ ] Loyalty points accrual on every payment (configurable rate)
- [ ] Redeem points at checkout (partial or full payment)
- [ ] Promo code model (code, discount type: flat/%, min order, expiry, usage limit)
- [ ] Apply promo code at checkout
- [ ] "Buy X get Y" offer model and engine
- [ ] Loyalty points balance on customer profile screen

---

### Tests — Phase 8

#### WhatsApp tests
- [ ] `givenSuccessfulPayment_whenProcessed_thenWhatsAppOrderConfirmationSent`
- [ ] `givenReceipt_whenGenerated_thenWhatsAppMessageContainsReceiptLinkAndReorderLink`
- [ ] `givenOrderStatusChangedToReady_whenUpdated_thenWhatsAppStatusUpdateSent`
- [ ] `givenWhatsAppApiDown_whenMessageFails_thenErrorLoggedAndOrderNotAffected`

#### QR table ordering tests
- [ ] `givenTableQrCode_whenExportedAsPdf_thenPdfContainsCorrectTableNumber`
- [ ] `givenMobileQrScan_whenTableLinked_thenStaffViewShowsTableHasActiveMobileSession`
- [ ] `givenTableSessionClosed_whenStaffViews_thenActiveMobileSessionBadgeRemoved`

#### Loyalty tests
- [ ] `givenPaymentSuccess_whenProcessed_thenPointsAccruedAtConfiguredRateForCustomer`
- [ ] `givenCustomerWith200Points_whenRedeem100Points_thenOrderTotalReducedByEquivalentAmount`
- [ ] `givenRedeemMorePointsThanBalance_whenAttempted_then400`
- [ ] `givenLoyaltyTransaction_whenViewHistory_thenEarnAndRedeemEntriesShown`
- [ ] `givenCustomerProfile_whenViewed_thenCurrentLoyaltyBalanceShown`

#### Promo code tests
- [ ] `givenValidPromoCode_whenApplied_thenCorrectDiscountAppliedToOrderTotal`
- [ ] `givenPercentagePromo_whenApplied_thenDiscountIsPercentageOfSubtotal`
- [ ] `givenFlatPromo_whenApplied_thenFixedAmountDeducted`
- [ ] `givenOrderBelowMinAmount_whenPromoApplied_then400MinimumNotMet`
- [ ] `givenExpiredPromoCode_whenApplied_then400Expired`
- [ ] `givenPromoAtUsageLimit_whenApplied_then400LimitReached`
- [ ] `givenInactivePromoCode_whenApplied_then400Invalid`

#### Buy X Get Y tests
- [ ] `givenBuyXGetYOffer_whenCustomerOrdersMeetsCriteria_thenFreeItemAddedToOrder`
- [ ] `givenBuyXGetYOffer_whenCriteriaNotMet_thenNoFreeItemAdded`

### Deliverables
- [ ] All Phase 8 tests passing in CI
- [ ] WhatsApp receipt delivery tested in staging
- [ ] Loyalty accrual and redemption verified end-to-end
- [ ] `reports/phase-8-report.md`

---

## Phase 9 — Financial Controls & Reconciliation

**Goal:** Airtight financial audit trail with manager-gated overrides.

### Tasks

#### Refunds
- [ ] Refund endpoint (manager PIN, reason, amount — partial or full)
- [ ] Trigger Paystack refund API
- [ ] Update payment status to `REFUNDED`
- [ ] Log refund event to audit log
- [ ] Display refund status to cashier/customer

#### Voids
- [ ] Void order endpoint (manager PIN, before payment processed)
- [ ] Void releases table if dine-in
- [ ] Void logged to audit log

#### Discounts
- [ ] Manual discount (manager PIN, amount or %, reason)
- [ ] Discount applied to order total before payment
- [ ] Discount logged to audit log

#### End-of-Day Reconciliation
- [ ] Summary report: total sales, total MoMo, total card, total cash, total refunds, total voids, total discounts
- [ ] Per-cashier breakdown
- [ ] Export to CSV / PDF
- [ ] Manager sign-off endpoint (marks day as reconciled)

#### Audit Log Review
- [ ] Admin UI to browse audit log (filter by action type, user, date)
- [ ] Exportable audit log

---

### Tests — Phase 9

#### Refund tests
- [ ] `givenNoOverrideToken_whenRefundAttempted_then403`
- [ ] `givenValidManagerOverrideToken_whenFullRefund_thenPaystackRefundCalledAndPaymentStatusRefunded`
- [ ] `givenValidManagerOverrideToken_whenPartialRefund_thenPaymentStatusPartiallyRefundedCorrectAmount`
- [ ] `givenRefundAmountExceedsPaymentAmount_whenAttempted_then400`
- [ ] `givenSuccessfulRefund_thenRefundEventWrittenToAuditLog`
- [ ] `givenAlreadyRefundedPayment_whenRefundedAgain_then400`

#### Void tests
- [ ] `givenNoOverrideToken_whenVoidAttempted_then403`
- [ ] `givenValidManagerOverrideToken_whenOrderVoided_thenOrderStatusVoided`
- [ ] `givenDineInOrder_whenVoided_thenLinkedTableStatusChangedToAvailable`
- [ ] `givenAlreadyCompletedOrder_whenVoidAttempted_then400`
- [ ] `givenSuccessfulVoid_thenVoidEventWrittenToAuditLog`

#### Discount tests
- [ ] `givenNoOverrideToken_whenDiscountAttempted_then403`
- [ ] `givenValidManagerOverrideToken_whenFlatDiscountApplied_thenOrderTotalReducedByAmount`
- [ ] `givenValidManagerOverrideToken_whenPercentageDiscountApplied_thenOrderTotalReducedByPercentage`
- [ ] `givenDiscountExceedsOrderTotal_whenApplied_then400`
- [ ] `givenSuccessfulDiscount_thenDiscountEventWrittenToAuditLog`

#### Reconciliation tests
- [ ] `givenDayWithKnownTransactions_whenReconciliationFetched_thenTotalsMatchSumOfPayments`
- [ ] `givenDayWithRefunds_whenReconciliationFetched_thenRefundTotalCorrect`
- [ ] `givenDayWithVoids_whenReconciliationFetched_thenVoidTotalCorrect`
- [ ] `givenNoOverrideToken_whenSignOffAttempted_then403`
- [ ] `givenValidManagerOverrideToken_whenSignOff_thenSignedByAndSignedAtPopulated`
- [ ] `givenAlreadySignedOffDay_whenSignOffAgain_then409`

#### Audit log tests
- [ ] `givenActionFilter_whenAuditLogQueried_thenOnlyMatchingActionsReturned`
- [ ] `givenDateFilter_whenAuditLogQueried_thenOnlyEventsInRangeReturned`
- [ ] `givenCashierToken_whenAuditLogQueried_then403`

### Deliverables
- [ ] All Phase 9 tests passing in CI
- [ ] Reconciliation totals verified against test transaction data
- [ ] `reports/phase-9-report.md`

---

## Phase 10 — Analytics, Testing & Deployment

**Goal:** Production-ready system with monitoring and verified quality.

### Tasks

#### Analytics & Reports
- [ ] Sales dashboard (daily, weekly, monthly revenue charts)
- [ ] Top-selling items report
- [ ] Peak hours heatmap
- [ ] Customer retention / repeat order rate
- [ ] Loyalty program stats (points issued, redeemed)
- [ ] Delivery performance (avg delivery time)

#### Testing
- [ ] Backend unit tests — service layer (JUnit 5, Mockito)
- [ ] Backend integration tests — API layer (Spring Boot Test, Testcontainers)
- [ ] Payment webhook integration test (mock Paystack events)
- [ ] Frontend unit tests (Vitest / Jest + React Testing Library)
- [ ] Expo mobile E2E tests (Detox or Maestro)
- [ ] Load test critical endpoints (order creation, payment initiation)
- [ ] Security review: OWASP Top 10 checklist

#### Deployment
- [ ] Dockerize Spring Boot backend
- [ ] Dockerize Next.js staff app
- [ ] Docker Compose for local dev (backend + PostgreSQL + Redis if used)
- [ ] CI/CD pipeline (GitHub Actions or similar):
  - [ ] Lint, test, build on every PR
  - [ ] Deploy to staging on merge to `develop`
  - [ ] Deploy to prod on merge to `main`
- [ ] Production PostgreSQL setup (managed DB, automated backups)
- [ ] HTTPS / TLS configured
- [ ] Environment secrets managed (not in code)
- [ ] Health check endpoints (`/actuator/health`)
- [ ] Error monitoring (Sentry or equivalent)
- [ ] Uptime monitoring

#### Documentation
- [ ] API reference (Swagger/OpenAPI auto-generated)
- [ ] Staff onboarding guide
- [ ] Deployment runbook
- [ ] Environment variable reference

---

### Tests — Phase 10

#### Analytics tests
- [ ] `givenKnownOrders_whenSalesDailyReportFetched_thenRevenueMatchesSumOfSuccessfulPayments`
- [ ] `givenKnownOrders_whenTopItemsReportFetched_thenItemsRankedByQuantitySoldDescending`
- [ ] `givenKnownOrders_whenPeakHoursFetched_thenHourWithMostOrdersRanksFirst`
- [ ] `givenCustomerWithTwoOrders_whenRetentionStatsFetched_thenCountedAsRepeatCustomer`

#### Full regression gate
- [ ] All Phase 2 tests passing
- [ ] All Phase 3 tests passing
- [ ] All Phase 4 tests passing
- [ ] All Phase 5 tests passing
- [ ] All Phase 6 tests passing
- [ ] All Phase 7 tests passing
- [ ] All Phase 8 tests passing
- [ ] All Phase 9 tests passing

#### Load tests
- [ ] `50ConcurrentOrderCreationRequests_allSucceedWithin2Seconds`
- [ ] `50ConcurrentPaymentInitiationRequests_allSucceedWithin3Seconds`
- [ ] `WebSocketWith100SimultaneousSubscribers_orderEventDeliveredToAll`

#### Security tests
- [ ] SQL injection attempt on search endpoints → sanitized, no data leaked
- [ ] XSS payload in order notes → escaped in receipt output
- [ ] IDOR: customer cannot fetch another customer's order by guessing UUID
- [ ] Brute-force login → rate limited after 10 attempts
- [ ] Paystack webhook without signature header → rejected

### Deliverables
- [ ] All tests green in CI (zero failures, zero skipped)
- [ ] Load test results documented
- [ ] Security checklist signed off
- [ ] App deployed to staging with smoke tests passing
- [ ] `reports/phase-10-report.md`

---

## Overall Progress Tracker

| Phase | Title                        | Status       |
|-------|------------------------------|--------------|
| 1     | Planning & Design            | **Complete** |
| 2     | Backend Core                 | Not Started  |
| 3     | Menu & Table Management      | Not Started  |
| 4     | Orders                       | Not Started  |
| 5     | Payments                     | Not Started  |
| 6     | Staff Web App (Next.js)      | Not Started  |
| 7     | Customer Mobile App (Expo)   | Not Started  |
| 8     | Ghana-Specific Features      | Not Started  |
| 9     | Financial Controls           | Not Started  |
| 10    | Analytics, Testing, Deploy   | Not Started  |

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

*Last updated: 2026-03-23*
