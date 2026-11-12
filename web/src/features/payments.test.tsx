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
  getReceiptByOrderId: vi.fn(),
  getReceiptByPaymentId: vi.fn(),
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
  test("finds a receipt by the number on the ticket", async () => {
    api.getReceiptByOrderId.mockResolvedValue({
      receiptNumber: "RCT-0001", paymentId: "p1", orderId: order.id, subtotal: "415.38", total: "415.38", currency: "GHS", paymentMethod: "CASH",
    });
    const user = userEvent.setup();
    render(<ReceiptsPage />);

    await user.type(screen.getByPlaceholderText("e.g. 7D63D7F5"), "#7d63d7f5{Enter}");

    expect(await screen.findByText("RCT-0001")).toBeInTheDocument();
    expect(api.lookupOrder).toHaveBeenCalledWith(session, "#7d63d7f5");
    expect(api.getReceiptByOrderId).toHaveBeenCalledWith(session, order.id);
    expect(screen.getByText("#7D63D7F5")).toBeInTheDocument();
  });

  test("a full order id skips the lookup; an unpaid order says so", async () => {
    api.getReceiptByOrderId.mockRejectedValue(new Error("Receipt not found"));
    const user = userEvent.setup();
    render(<ReceiptsPage />);

    await user.type(screen.getByPlaceholderText("e.g. 7D63D7F5"), order.id);
    await user.click(screen.getByRole("button", { name: "Find receipt" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("has not been paid");
    expect(api.lookupOrder).not.toHaveBeenCalled();
  });
});
