import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import type { PublicOrderTrackingRecord } from "@/lib/apiClient";

const api = vi.hoisted(() => ({
  submitGuestFeedback: vi.fn(),
  getGuestSplits: vi.fn(),
  createGuestSplits: vi.fn(),
  payGuestSplit: vi.fn(),
  verifyGuestPayment: vi.fn(),
}));

vi.mock("@/lib/apiClient", () => api);

import { GuestOrderActions } from "@/components/GuestOrderActions";

function order(status: PublicOrderTrackingRecord["status"]): PublicOrderTrackingRecord {
  const now = new Date().toISOString();
  return { orderId: "order-1", tableNumber: "T1", status, createdAt: now, updatedAt: now };
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

    await user.click(screen.getByRole("button", { name: /Split & pay/ }));
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

  test("givenCancelledOrder_whenRendered_thenNoActions", () => {
    const { container } = render(<GuestOrderActions tableToken="tok-1" order={order("CANCELLED")} />);
    expect(container).toBeEmptyDOMElement();
  });
});
