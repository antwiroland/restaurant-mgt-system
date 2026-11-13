import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "Till", role: "CASHIER" } };

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, loading: false, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));

const api = vi.hoisted(() => ({ getOrderByPickupCode: vi.fn(), updateOrderStatus: vi.fn() }));
vi.mock("@/lib/apiClient", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/apiClient")>()), ...api }));

import PickupLookupPage from "@/app/(staff)/orders/pickup/page";

const readyOrder = {
  id: "ac724f02-1111-2222-3333-444455556666",
  type: "PICKUP",
  status: "READY",
  pickupCode: "PK123456",
  subtotal: "45.00",
  total: "45.00",
  createdAt: "2026-11-13T10:00:00Z",
  items: [{ id: "i1", menuItemId: "m1", name: "Kelewele", price: "45.00", quantity: 1 }],
};

describe("Pickup lookup", () => {
  beforeEach(() => {
    api.getOrderByPickupCode.mockReset();
    api.updateOrderStatus.mockReset();
  });

  test("found by the number on the board (with #), it is handed over only with the customer's code", async () => {
    api.getOrderByPickupCode.mockResolvedValue(readyOrder);
    api.updateOrderStatus.mockResolvedValue({ ...readyOrder, status: "COMPLETED" });
    render(<PickupLookupPage />);

    await userEvent.type(screen.getByLabelText("Pickup code or order number"), "#AC724F02");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(api.getOrderByPickupCode).toHaveBeenCalledWith(session, "AC724F02"); // "#" removed before sending
    expect(await screen.findByText("Ready to hand over")).toBeInTheDocument();
    // The code is not shown to staff: the customer has to present it.
    expect(screen.queryByText(/PK123456/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Mark collected" }));
    expect(api.updateOrderStatus).not.toHaveBeenCalled();
    expect(await screen.findByText(/Ask the customer for their pickup code/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("Customer's pickup code"), "pk123456");
    await userEvent.click(screen.getByRole("button", { name: "Mark collected" }));
    expect(api.updateOrderStatus).toHaveBeenCalledWith(session, readyOrder.id, "COMPLETED", "pk123456");
    expect(await screen.findByText(/handed over/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark collected" })).not.toBeInTheDocument();
  });

  test("searching by the pickup code counts as the customer showing it", async () => {
    api.getOrderByPickupCode.mockResolvedValue(readyOrder);
    api.updateOrderStatus.mockResolvedValue({ ...readyOrder, status: "COMPLETED" });
    render(<PickupLookupPage />);

    await userEvent.type(screen.getByLabelText("Pickup code or order number"), "PK123456");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText("Pickup code checked")).toBeInTheDocument();
    expect(screen.queryByLabelText("Customer's pickup code")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Mark collected" }));
    expect(api.updateOrderStatus).toHaveBeenCalledWith(session, readyOrder.id, "COMPLETED", "PK123456");
  });

  test("a cashier gets no override to hand over without the code", async () => {
    api.getOrderByPickupCode.mockResolvedValue(readyOrder);
    render(<PickupLookupPage />);
    await userEvent.type(screen.getByLabelText("Pickup code or order number"), "AC724F02");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("Ready to hand over")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hand over without code" })).not.toBeInTheDocument();
  });

  test("an order that is not ready cannot be handed over", async () => {
    api.getOrderByPickupCode.mockResolvedValue({ ...readyOrder, status: "PREPARING" });
    render(<PickupLookupPage />);
    await userEvent.type(screen.getByLabelText("Pickup code or order number"), "AC724F02");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("Being prepared")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark collected" })).not.toBeInTheDocument();
  });
});
