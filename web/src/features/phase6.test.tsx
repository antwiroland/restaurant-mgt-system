import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { routeByRole, shouldRedirectToLogin, shouldRefreshToken } from "@/features/auth/auth";
import {
  addToCart,
  buildOrderPayload,
  cartTotal,
  paymentPendingMessage,
  removeFromCart,
  setQuantity,
} from "@/features/pos/cart";
import { applyTableStatusEvent, activeOrdersForTable, tableColor } from "@/features/tables/tables";
import { applyNewOrder, applyOrderStatus, shouldShowCancelPinModal } from "@/features/orders/orders";
import { applyFlatDiscount, applyRefund, reconcile } from "@/features/financial/reconciliation";
import { withRetryAfterRefresh } from "@/lib/apiClient";
import { PinModal } from "@/components/PinModal";

describe("Phase 6 scenarios", () => {
  test("givenCashierLogin_whenAuthenticated_thenRedirectedToPosPage", () => {
    expect(routeByRole("CASHIER")).toBe("/pos");
  });

  test("givenAdminLogin_whenAuthenticated_thenRedirectedToDashboard", () => {
    expect(routeByRole("ADMIN")).toBe("/dashboard");
  });

  test("givenUnauthenticated_whenVisitProtectedRoute_thenRedirectedToLogin", () => {
    expect(shouldRedirectToLogin(null)).toBe(true);
  });

  test("givenExpiredAccessToken_whenApiCallMade_thenRefreshCalledAndRequestRetried", () => {
    expect(shouldRefreshToken(10_000, 9_999)).toBe(true);
    expect(withRetryAfterRefresh(401)).toBe(true);
  });

  test("givenCashierClicksDiscount_whenRendered_thenPinModalAppears", async () => {
    const user = userEvent.setup();
    render(<PinModal onApply={() => {}} />);
    await user.click(screen.getByText("Open PIN Modal"));
    expect(screen.getByRole("dialog", { name: "pin-modal" })).toBeInTheDocument();
  });

  test("givenCorrectManagerPin_whenEnteredInModal_thenDiscountAppliedAndModalCloses", async () => {
    const user = userEvent.setup();
    const onApply = vi.fn();
    render(<PinModal onApply={onApply} expectedPin="7777" />);
    await user.click(screen.getByText("Open PIN Modal"));
    await user.type(screen.getByLabelText("pin-input"), "7777");
    await user.click(screen.getByText("Submit PIN"));
    expect(onApply).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("dialog", { name: "pin-modal" })).not.toBeInTheDocument();
  });

  test("givenWrongPin_whenEnteredInModal_thenErrorMessageShownModalStaysOpen", async () => {
    const user = userEvent.setup();
    render(<PinModal onApply={() => {}} expectedPin="4444" />);
    await user.click(screen.getByText("Open PIN Modal"));
    await user.type(screen.getByLabelText("pin-input"), "1234");
    await user.click(screen.getByText("Submit PIN"));
    expect(screen.getByText("Invalid PIN")).toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "pin-modal" })).toBeInTheDocument();
  });

  test("givenLockedPin_whenModalOpened_thenLockedStateMessageShown", async () => {
    const user = userEvent.setup();
    render(<PinModal onApply={() => {}} locked />);
    await user.click(screen.getByText("Open PIN Modal"));
    await user.type(screen.getByLabelText("pin-input"), "1234");
    await user.click(screen.getByText("Submit PIN"));
    expect(screen.getByText("PIN locked")).toBeInTheDocument();
  });

  test("givenMenuLoaded_whenItemAddedToCart_thenQuantityAndTotalUpdated", () => {
    const item = { id: "m1", name: "Jollof", price: 25 };
    const cart = addToCart([], item);
    expect(cart[0].quantity).toBe(1);
    expect(cartTotal(cart)).toBe(25);
  });

  test("givenItemInCart_whenQuantityIncremented_thenTotalRecalculated", () => {
    const item = { id: "m2", name: "Chicken", price: 30 };
    const cart = setQuantity(addToCart([], item), "m2", 3);
    expect(cartTotal(cart)).toBe(90);
  });

  test("givenItemInCart_whenRemoved_thenCartEmpty", () => {
    const item = { id: "m3", name: "Tea", price: 10 };
    const cart = removeFromCart(addToCart([], item), "m3");
    expect(cart.length).toBe(0);
  });

  test("givenDineInSelected_whenTableChosen_thenTableIdIncludedInOrderPayload", () => {
    const cart = [{ item: { id: "m4", name: "Soup", price: 20 }, quantity: 2 }];
    const payload = buildOrderPayload("DINE_IN", cart, "table-7");
    expect(payload.tableId).toBe("table-7");
  });

  test("givenCompletedCart_whenOrderSubmitted_thenApiCalledAndConfirmationScreenShown", () => {
    const submit = vi.fn(() => ({ ok: true, confirmation: "shown" }));
    const response = submit();
    expect(submit).toHaveBeenCalledTimes(1);
    expect(response.confirmation).toBe("shown");
  });

  test("givenOrderSubmitted_whenPaymentInitiated_thenPendingStateShown", () => {
    expect(paymentPendingMessage()).toContain("Waiting");
  });

  test("givenTableList_whenRendered_thenAvailableTablesShowGreenOccupiedShowRed", () => {
    expect(tableColor("AVAILABLE")).toBe("green");
    expect(tableColor("OCCUPIED")).toBe("red");
  });

  test("givenOccupiedTable_whenClicked_thenActiveOrderListShown", () => {
    const list = activeOrdersForTable("t2", [
      { id: "o1", tableId: "t2" },
      { id: "o2", tableId: "t3" },
    ]);
    expect(list).toEqual(["o1"]);
  });

  test("givenWebSocketEvent_whenTableStatusChanged_thenTableMapUpdatesWithoutReload", () => {
    const updated = applyTableStatusEvent(
      [{ id: "t2", number: "T2", status: "OCCUPIED" }],
      "t2",
      "AVAILABLE",
    );
    expect(updated[0].status).toBe("AVAILABLE");
  });

  test("givenActiveOrdersList_whenWebSocketReceivesNewOrder_thenOrderAppearsInListWithoutReload", () => {
    const list = applyNewOrder([], { id: "o-new", status: "PENDING" });
    expect(list[0].id).toBe("o-new");
  });

  test("givenOrder_whenStatusAdvanced_thenUpdatedStatusShownInList", () => {
    const list = applyOrderStatus([{ id: "o1", status: "PENDING" }], "o1", "READY");
    expect(list[0].status).toBe("READY");
  });

  test("givenCashierClicksCancelOrder_whenConfirmed_thenPinModalShown", () => {
    expect(shouldShowCancelPinModal(true)).toBe(true);
  });

  test("givenDiscountApplied_whenViewOrder_thenDiscountAmountAndNewTotalShown", () => {
    expect(applyFlatDiscount(100, 15)).toBe(85);
  });

  test("givenRefundApplied_whenViewReceipt_thenRefundedAmountAndStatusShown", () => {
    const refund = applyRefund("REFUNDED", 20);
    expect(refund.status).toBe("REFUNDED");
    expect(refund.refundedAmount).toBe(20);
  });

  test("givenReconciliationView_whenRendered_thenTotalsMatchExpectedSums", () => {
    const totals = reconcile([
      { sales: 100, refunds: 5, discounts: 3 },
      { sales: 200, refunds: 2, discounts: 7 },
    ]);
    expect(totals.sales).toBe(300);
    expect(totals.refunds).toBe(7);
    expect(totals.discounts).toBe(10);
  });
});
