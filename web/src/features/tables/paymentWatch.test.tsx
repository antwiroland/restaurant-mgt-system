import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import type { TablePaymentWatchRecord } from "@/lib/apiClient";

const session = { accessToken: "t", user: { id: "u1", name: "Esi", role: "CASHIER" } };

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));

const api = vi.hoisted(() => ({ getTablePaymentWatch: vi.fn() }));
vi.mock("@/lib/apiClient", () => api);
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

import { StaffAlerts } from "@/components/StaffAlerts";
import { isAtRisk, watchAlert, watchStatus } from "@/features/tables/paymentWatch";

function watch(overrides: Partial<TablePaymentWatchRecord>): TablePaymentWatchRecord {
  return {
    tableId: "t1", tableNumber: "5", outstanding: "80.00", openOrders: 2, allServed: false,
    risk: "NONE", minutesWaiting: 0, ...overrides,
  };
}

beforeEach(() => vi.clearAllMocks());

describe("Table payment watch wording", () => {
  test("says what is happening at the table", () => {
    expect(watchStatus(watch({}))).toBe("2 orders in progress");
    expect(watchStatus(watch({ allServed: true }))).toBe("All served · not paid");
    expect(watchStatus(watch({ risk: "SERVED_UNPAID", allServed: true, minutesWaiting: 25 }))).toBe("Served 25 min ago · not paid");
    expect(watchStatus(watch({ risk: "BILL_WAITING", minutesWaiting: 75 }))).toBe("Asked for the bill 1 h 15 min ago");
    expect(isAtRisk(watch({}))).toBe(false);
    expect(watchAlert(watch({ risk: "SERVED_UNPAID", minutesWaiting: 22 }))).toBe(
      "Table 5 was served 22 min ago and hasn't paid. Check in with the bill.");
  });
});

describe("Staff alerts for unpaid tables", () => {
  test("a waiter is told which table needs them; a dismissed alert stays hidden", async () => {
    api.getTablePaymentWatch.mockResolvedValue([
      watch({ tableId: "t1", tableNumber: "5", risk: "BILL_WAITING", minutesWaiting: 12 }),
      watch({ tableId: "t2", tableNumber: "6" }),
    ]);
    const user = userEvent.setup();
    const { rerender } = render(<StaffAlerts role="CASHIER" />);

    expect(await screen.findByText("Table 5 asked for the bill 12 min ago. Take it over.")).toBeInTheDocument();
    expect(screen.queryByText(/Table 6/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Dismiss alert" }));
    expect(screen.queryByText(/Table 5/)).not.toBeInTheDocument();
    rerender(<StaffAlerts role="CASHIER" />);
    await waitFor(() => expect(api.getTablePaymentWatch).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/Table 5/)).not.toBeInTheDocument();
  });

  test("roles that cannot act see nothing", () => {
    render(<StaffAlerts role="RIDER" />);
    expect(api.getTablePaymentWatch).not.toHaveBeenCalled();
  });
});
