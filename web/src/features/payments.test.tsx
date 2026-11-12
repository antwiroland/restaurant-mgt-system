import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", user: { id: "u1", name: "Kofi", role: "CASHIER" } };

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, loading: false, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

const api = vi.hoisted(() => ({
  getPayment: vi.fn(),
  initiatePayment: vi.fn(),
  lookupOrder: vi.fn(),
  retryPayment: vi.fn(),
  verifyPayment: vi.fn(),
  findReceipt: vi.fn(),
}));
vi.mock("@/components/BillBreakdown", () => ({ BillBreakdown: ({ total }: { total: string }) => <p>Total {total}</p> }));
vi.mock("@/lib/apiClient", () => api);

import PaymentsPage from "@/app/(staff)/payments/page";
import ReceiptsPage from "@/app/(staff)/receipts/page";

const order = {
  id: "7d63d7f5-1111-4222-8333-944445555666", type: "DINE_IN", status: "PREPARING", tableNumber: "4",
  total: "415.38", items: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  api.lookupOrder.mockResolvedValue(order);
});

describe("Payments page result", () => {
  test("cash payment shows a readable result with the change, not the raw response", async () => {
    api.initiatePayment.mockResolvedValue({
      paymentId: "2fe7d111-b99b-4438-81e2-d0d3f9bf6a37", status: "SUCCESS", paystackReference: "CASH-DADB89D6",
      authorizationUrl: null, message: "Cash received", changeDue: 84.62,
    });
    const user = userEvent.setup();
    const { container } = render(<PaymentsPage />);

    await user.type(screen.getByPlaceholderText("e.g. 7D63D7F5"), "7D63D7F5");
    await user.type(screen.getByLabelText(/Amount received/), "500");
    await user.click(screen.getByRole("button", { name: "Record cash payment" }));

    const result = await screen.findByRole("status", { name: "Payment result" });
    expect(result).toHaveTextContent("Cash received");
    expect(result).toHaveTextContent("#7D63D7F5 · Table 4");
    expect(result).toHaveTextContent("Change to give back");
    expect(result).toHaveTextContent("84.62");
    expect(result).toHaveTextContent("CASH-DADB89D6");
    expect(container.querySelector("pre")).toBeNull();
    expect(container).not.toHaveTextContent("paymentId");
    expect(api.initiatePayment).toHaveBeenCalledWith(session, expect.objectContaining({ orderId: order.id, method: "CASH", momoPhone: undefined, amountTendered: 500 }));

    await user.click(screen.getByRole("button", { name: "Take another payment" }));
    expect(screen.queryByRole("status", { name: "Payment result" })).toBeNull();
    expect(screen.getByPlaceholderText("e.g. 7D63D7F5")).toHaveValue("");
  });

  test("mobile money waits for approval and can be checked", async () => {
    api.initiatePayment.mockResolvedValue({ paymentId: "p2", status: "INITIATED", paystackReference: "PSK-1" });
    api.verifyPayment.mockResolvedValue({ id: "p2", status: "SUCCESS", orderId: order.id, amount: "415.38", method: "MOBILE_MONEY" });
    const user = userEvent.setup();
    render(<PaymentsPage />);

    await user.type(screen.getByPlaceholderText("e.g. 7D63D7F5"), "7D63D7F5");
    await user.click(screen.getByRole("button", { name: "Mobile Money" }));
    await user.type(screen.getByLabelText("MoMo number"), "0244000001");
    await user.click(screen.getByRole("button", { name: "Start payment" }));

    expect(await screen.findByText("Waiting for the customer to approve")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Check payment" }));
    await waitFor(() => expect(screen.getByRole("status", { name: "Payment result" })).toHaveTextContent("Payment received"));
  });
});

describe("Receipt Center", () => {
  const receipt = { receiptNumber: "RCP-20261112-2FE7D111", paymentId: "p1", orderId: order.id, subtotal: "415.38", total: "415.38", currency: "GHS", paymentMethod: "CASH" };

  test.each(["7D63D7F5", "RCP-20261112-2FE7D111", "CASH-DADB89D6"])("finds a receipt from %s", async (q) => {
    api.findReceipt.mockResolvedValue(receipt);
    const user = userEvent.setup();
    render(<ReceiptsPage />);

    await user.type(screen.getByPlaceholderText("e.g. 7D63D7F5"), `${q}{Enter}`);

    expect(await screen.findByText("RCP-20261112-2FE7D111")).toBeInTheDocument();
    expect(api.findReceipt).toHaveBeenCalledWith(session, q);
    expect(screen.getByText("#7D63D7F5")).toBeInTheDocument();
  });

  test("an unpaid order says so", async () => {
    api.findReceipt.mockRejectedValue(new Error("Receipt not found"));
    const user = userEvent.setup();
    render(<ReceiptsPage />);

    await user.type(screen.getByPlaceholderText("e.g. 7D63D7F5"), "90F95A27");
    await user.click(screen.getByRole("button", { name: "Find receipt" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("No receipt for 90F95A27 yet: it has not been paid.");
  });
});
