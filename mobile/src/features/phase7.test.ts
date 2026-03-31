import { describe, expect, test } from "vitest";
import { createGuestSessionToken, registerPhone, verifyOtp } from "./auth/auth";
import { addItem, itemsForCategory, setCartQty } from "./menu/menu";
import { canAttemptPaymentOffline, openMenuOffline, queueOrderWhenOffline, shouldRetryEntry, MAX_RETRY_COUNT } from "./offline/offline";
import { scanQr } from "./qr/qr";
import { defaultMethod, handlePaystackEvent, pendingState, retryFailedPayment } from "./payment/payment";
import { groupTotal, joinSharedCart, submitGroupByHost } from "./group/group";
import { appearsInHistory, applyStatusUpdate, reorderFromHistory } from "./tracking/tracking";
import { cancelReservation, createReservation } from "./reservation/reservation";

describe("Phase 7 scenarios", () => {
  test("givenValidPhone_whenRegister_thenOtpSentAndRegistrationPending", () => {
    const result = registerPhone("+233201234567");
    expect(result.otpSent).toBe(true);
    expect(result.pending).toBe(true);
  });

  test("givenCorrectOtp_whenVerified_thenTokenIssuedAndUserLoggedIn", () => {
    const result = verifyOtp("123456", "123456");
    expect(result.tokenIssued).toBe(true);
    expect(result.loggedIn).toBe(true);
  });

  test("givenGuestCheckout_whenOrderCreated_thenSessionTokenUsedWithNoRole", () => {
    const result = createGuestSessionToken();
    expect(result.sessionTokenUsed).toBe(true);
    expect(result.role).toBeNull();
  });

  test("givenMenuLoaded_whenCategoryTapped_thenItemsForCategoryShown", () => {
    const items = [
      { id: "1", name: "A", categoryId: "cat1", price: 10 },
      { id: "2", name: "B", categoryId: "cat2", price: 12 },
    ];
    expect(itemsForCategory(items, "cat1")).toHaveLength(1);
  });

  test("givenItemDetailOpen_whenAddToCart_thenCartBadgeIncremented", () => {
    const item = { id: "m1", name: "Jollof", categoryId: "cat", price: 20 };
    const cart = addItem([], item);
    expect(cart[0].quantity).toBe(1);
  });

  test("givenItemInCart_whenQuantitySetToZero_thenItemRemovedFromCart", () => {
    const item = { id: "m2", name: "Tea", categoryId: "cat", price: 8 };
    const cart = setCartQty(addItem([], item), "m2", 0);
    expect(cart).toHaveLength(0);
  });

  test("givenOfflineDevice_whenMenuOpened_thenCachedMenuShownWithStaleWarning", () => {
    const state = openMenuOffline(3);
    expect(state.shownCachedMenu).toBe(true);
    expect(state.staleWarning).toBe(true);
  });

  test("givenOfflineDevice_whenOrderSubmitted_thenOrderQueuedLocallyWithQueuedStatus", async () => {
    const { vi } = await import("vitest");
    const enqueue = vi.fn().mockResolvedValue(undefined);
    const { useOfflineStore } = await import("../store/offline");
    vi.spyOn(useOfflineStore, "getState").mockReturnValue({ enqueue } as any);

    await queueOrderWhenOffline("CREATE_ORDER", { type: "PICKUP", items: [] });

    expect(enqueue).toHaveBeenCalledWith("CREATE_ORDER", { type: "PICKUP", items: [] }, undefined);
    vi.restoreAllMocks();
  });

  test("givenQueuedOrder_whenDeviceReconnects_thenRetryRespectesMaxRetryCountAndSkipsExhausted", () => {
    expect(shouldRetryEntry({ status: "FAILED", retryCount: 0 })).toBe(true);
    expect(shouldRetryEntry({ status: "FAILED", retryCount: MAX_RETRY_COUNT - 1 })).toBe(true);
    expect(shouldRetryEntry({ status: "FAILED", retryCount: MAX_RETRY_COUNT })).toBe(false);
    expect(shouldRetryEntry({ status: "QUEUED", retryCount: 0 })).toBe(false);
  });

  test("givenOfflineDevice_whenPaymentAttempted_thenBlockedWithInternetRequiredMessage", () => {
    const result = canAttemptPaymentOffline(false);
    expect(result.allowed).toBe(false);
    expect(result.message).toContain("Internet required");
  });

  test("givenValidQrCode_whenScanned_thenTableInfoShownAndLinkedToCart", () => {
    const result = scanQr("table:T7");
    expect(result.valid).toBe(true);
    expect(result.tableId).toBe("T7");
  });

  test("givenInvalidQrCode_whenScanned_thenErrorMessageShown", () => {
    const result = scanQr("bad-qr");
    expect(result.valid).toBe(false);
    expect(result.error).toContain("Invalid");
  });

  test("givenPaymentScreen_whenRendered_thenMobileMoneyIsDefaultSelectedMethod", () => {
    expect(defaultMethod()).toBe("MOBILE_MONEY");
  });

  test("givenMoMoPaymentInitiated_whenPending_thenWaitingForApprovalStateShown", () => {
    expect(pendingState()).toContain("waiting for MoMo approval");
  });

  test("givenPaystackSuccessEvent_whenReceived_thenOrderTrackingScreenShown", () => {
    expect(handlePaystackEvent("success")).toBe("TRACKING");
  });

  test("givenPaystackFailedEvent_whenReceived_thenFailureScreenWithRetryOptionShown", () => {
    expect(handlePaystackEvent("failed")).toBe("FAILURE");
  });

  test("givenFailedPayment_whenRetryTapped_thenNewPaymentInitiated", () => {
    expect(retryFailedPayment().initiated).toBe(true);
  });

  test("givenGroupSessionCreated_whenCodeShared_thenSecondDeviceJoinsAndSeesSharedCart", () => {
    const participants = joinSharedCart([{ id: "a", items: 1 }], { id: "b", items: 0 });
    expect(participants).toHaveLength(2);
  });

  test("givenTwoParticipants_whenBothAddItems_thenGroupTotalReflectsAllItems", () => {
    const total = groupTotal([{ id: "a", items: 2 }, { id: "b", items: 3 }]);
    expect(total).toBe(5);
  });

  test("givenGroupCart_whenHostSubmits_thenSingleOrderCreatedAndAllParticipantsNotified", () => {
    const result = submitGroupByHost(true);
    expect(result.singleOrderCreated).toBe(true);
    expect(result.allNotified).toBe(true);
  });

  test("givenActiveOrder_whenStatusChangesToPreparing_thenTrackingScreenUpdatesWithoutManualRefresh", () => {
    const updated = applyStatusUpdate({ id: "o3", status: "PENDING", items: [] }, "PREPARING");
    expect(updated.status).toBe("PREPARING");
  });

  test("givenCompletedOrder_whenViewHistory_thenOrderAppearsInHistoryList", () => {
    expect(appearsInHistory([{ id: "o4", status: "COMPLETED", items: [] }], "o4")).toBe(true);
  });

  test("givenHistoryOrder_whenReorderTapped_thenCartPrePopulatedWithSameItems", () => {
    const result = reorderFromHistory({ id: "o5", status: "COMPLETED", items: ["m1", "m2"] });
    expect(result.prepopulated).toEqual(["m1", "m2"]);
  });

  test("givenAvailableSlot_whenReservationCreated_thenConfirmationShown", () => {
    expect(createReservation(true).confirmationShown).toBe(true);
  });

  test("givenMyReservation_whenCancelTapped_then204AndRemovedFromList", () => {
    const result = cancelReservation({ id: "r1", cancelled: false });
    expect(result.status).toBe(204);
    expect(result.removedFromList).toBe(true);
  });
});
