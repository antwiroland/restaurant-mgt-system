import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import type { PublicOrderTrackingRecord } from "@/lib/apiClient";

const api = vi.hoisted(() => ({
  submitGuestFeedback: vi.fn(),
  getGuestSplits: vi.fn(),
  createGuestSplits: vi.fn(),
  payGuestSplit: vi.fn(),
  payGuestOrder: vi.fn(),
  verifyGuestPayment: vi.fn(),
}));

vi.mock("@/lib/apiClient", () => api);

import { GuestOrderActions } from "@/components/GuestOrderActions";
import { groupPickupBoard } from "@/features/orders/pickupBoard";
import { isValidGuestPhone, loadGuestContact, saveGuestContact } from "@/features/orders/guestContact";

function order(status: PublicOrderTrackingRecord["status"], amountPaid = "0.00"): PublicOrderTrackingRecord {
  const now = new Date().toISOString();
  return { orderId: "order-1", tableNumber: "T1", status, createdAt: now, updatedAt: now, total: "40.00", amountPaid };
}

beforeEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
});

describe("Guest table order actions", () => {
  test("givenCompletedOrder_whenGuestRates_thenFeedbackSentWithTableToken", async () => {
    api.submitGuestFeedback.mockResolvedValue({});
    const user = userEvent.setup();
    render(<GuestOrderActions tableToken="tok-1" order={order("COMPLETED")} />);

    await user.click(screen.getByRole("radio", { name: "4 stars" }));
    await user.type(screen.getByLabelText("Comment (optional)"), "Lovely jollof");
    await user.click(screen.getByRole("button", { name: "Send rating" }));

    expect(api.submitGuestFeedback).toHaveBeenCalledWith("tok-1", "order-1", {
      rating: 4,
      comment: "Lovely jollof",
      guestName: undefined,
    });
    expect(await screen.findByText("Thanks for rating this order!")).toBeInTheDocument();
  });

  test("givenOpenOrder_whenGuestsSplitAndPayShare_thenMomoPaymentStartedForThatShare", async () => {
    api.getGuestSplits.mockResolvedValue([]);
    api.createGuestSplits.mockResolvedValue([
      { id: "s1", orderId: "order-1", displayName: "Ama", amount: "20.00", status: "PENDING" },
      { id: "s2", orderId: "order-1", displayName: "Kofi", amount: "20.00", status: "PENDING" },
    ]);
    api.payGuestSplit.mockResolvedValue({ paymentId: "p1", status: "INITIATED", message: "Approve on your phone" });
    const user = userEvent.setup();
    render(<GuestOrderActions tableToken="tok-1" order={order("PREPARING")} />);

    await user.click(await screen.findByRole("button", { name: "Split the bill" }));
    await user.type(screen.getByLabelText("Names (optional, comma separated)"), "Ama, Kofi");
    await user.click(screen.getByRole("button", { name: "Split equally" }));
    expect(api.createGuestSplits).toHaveBeenCalledWith("tok-1", "order-1", 2, ["Ama", "Kofi"]);

    api.getGuestSplits.mockResolvedValue([
      { id: "s1", orderId: "order-1", displayName: "Ama", amount: "20.00", status: "PENDING" },
      { id: "s2", orderId: "order-1", displayName: "Kofi", amount: "20.00", status: "PENDING" },
    ]);
    await user.click((await screen.findAllByRole("button", { name: "Pay this share" }))[1]);
    await user.type(screen.getByLabelText("MoMo number"), "0244000001");
    await user.click(screen.getByRole("button", { name: "Pay GHS 20.00" }));

    await waitFor(() => expect(api.payGuestSplit).toHaveBeenCalled());
    const [token, orderId, splitId, phone] = api.payGuestSplit.mock.calls[0];
    expect([token, orderId, splitId, phone]).toEqual(["tok-1", "order-1", "s2", "0244000001"]);
    expect(await screen.findByText("Approve on your phone")).toBeInTheDocument();
  });

  test("givenUnsplitOrder_whenGuestPaysWholeOrder_thenMomoStartedWithTheirPhone", async () => {
    api.getGuestSplits.mockResolvedValue([]);
    api.payGuestOrder.mockResolvedValue({ paymentId: "p9", status: "INITIATED", message: "Approve GHS 40 on your phone" });
    const user = userEvent.setup();
    render(<GuestOrderActions tableToken="tok-1" order={order("PREPARING")} guestPhone="0244000009" />);

    await user.click(await screen.findByRole("button", { name: "Pay GHS 40.00 by MoMo" }));
    expect(screen.getByLabelText("MoMo number")).toHaveValue("0244000009");
    await user.click(screen.getByRole("button", { name: "Pay GHS 40.00" }));

    await waitFor(() => expect(api.payGuestOrder).toHaveBeenCalled());
    expect(api.payGuestOrder.mock.calls[0].slice(0, 3)).toEqual(["tok-1", "order-1", "0244000009"]);
    expect(await screen.findByText("Approve GHS 40 on your phone")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /check payment/ })).toBeInTheDocument();
  });

  test("givenPaidOrder_whenRendered_thenNoPayButtons", async () => {
    api.getGuestSplits.mockResolvedValue([]);
    render(<GuestOrderActions tableToken="tok-1" order={order("PREPARING", "40.00")} />);
    expect(await screen.findByText("Paid. Thank you!")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Pay/ })).not.toBeInTheDocument();
  });

  test("givenSplitBill_whenRendered_thenOnlyShareButtonOffered", async () => {
    api.getGuestSplits.mockResolvedValue([{ id: "s1", orderId: "order-1", displayName: "Ama", amount: "20.00", status: "PENDING" }]);
    render(<GuestOrderActions tableToken="tok-1" order={order("PREPARING")} />);
    expect(await screen.findByRole("button", { name: "Pay my share" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /by MoMo/ })).not.toBeInTheDocument();
  });

  test("givenCancelledOrder_whenRendered_thenNoActions", () => {
    const { container } = render(<GuestOrderActions tableToken="tok-1" order={order("CANCELLED")} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe("Guest contact", () => {
  test("phone must have at least 9 digits", () => {
    expect(isValidGuestPhone("024 123 4567")).toBe(true);
    expect(isValidGuestPhone("+233 24 123 4567")).toBe(true);
    expect(isValidGuestPhone("12345")).toBe(false);
    expect(isValidGuestPhone("call me")).toBe(false);
    expect(isValidGuestPhone("---------")).toBe(false);
  });

  test("contact is remembered on this device", () => {
    expect(loadGuestContact()).toBeNull();
    saveGuestContact({ name: "Ama", phone: "0241234567" });
    expect(loadGuestContact()).toEqual({ name: "Ama", phone: "0241234567" });
    window.localStorage.setItem("rm-guest-contact", "{not json");
    expect(loadGuestContact()).toBeNull();
  });
});

describe("Pickup board", () => {
  test("groups order numbers by status; newest ready first", () => {
    const groups = groupPickupBoard([
      { orderNumber: "AAA11111", status: "PREPARING", updatedAt: "2026-11-12T10:05:00Z" },
      { orderNumber: "BBB22222", status: "CONFIRMED", updatedAt: "2026-11-12T10:01:00Z" },
      { orderNumber: "CCC33333", status: "READY", updatedAt: "2026-11-12T10:02:00Z" },
      { orderNumber: "DDD44444", status: "READY", updatedAt: "2026-11-12T10:09:00Z" },
      { orderNumber: "EEE55555", status: "PREPARING", updatedAt: "2026-11-12T10:00:00Z" },
    ]);
    expect(groups).toEqual({
      CONFIRMED: ["BBB22222"],
      PREPARING: ["EEE55555", "AAA11111"],
      READY: ["DDD44444", "CCC33333"],
    });
  });
});
