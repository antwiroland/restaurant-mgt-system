import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "Clerk", role: "CASHIER" } };
const permissions = vi.hoisted(() => ({ list: [] as string[] }));

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));
vi.mock("@/features/hr/HrAccessContext", () => ({
  useHrAccess: () => ({ access: { allBranches: true, branchIds: [] }, loading: false, can: (...codes: string[]) => codes.some((c) => permissions.list.includes(c)) }),
}));

const api = vi.hoisted(() => ({ getTipPool: vi.fn(), tipPoolAction: vi.fn() }));
vi.mock("@/lib/hrApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/hrApi")>()), ...api }));

import { TipPoolDrawer } from "@/features/hr/tips/TipPoolDrawer";
import type { TipPoolDetail } from "@/lib/hrApi";

function pool(overrides: Partial<TipPoolDetail> = {}, status: TipPoolDetail["summary"]["status"] = "CALCULATED"): TipPoolDetail {
  return {
    summary: {
      id: "t1", poolNumber: "TIP-000001", branchId: "b1", branchName: "Osu", source: "TIPS", periodStart: "2026-10-01", periodEnd: "2026-10-31",
      status, orderCount: 2, collectedAmount: 100, houseAmount: 10, distributableAmount: 90, calculatedAt: "2026-11-01T10:00:00Z", version: 1,
    },
    rule: { scope: "ORGANISATION", method: "HOURS", housePercent: 10, taxable: true, minMinutes: 0, positionPoints: {} },
    allocations: [
      { id: "a1", employeeId: "e1", employeeName: "Ama Waiter", positionTitle: "Waiter", minutes: 480, points: 1, amount: 60, inPayroll: false },
      { id: "a2", employeeId: "e2", employeeName: "Kofi Cook", positionTitle: "Cook", minutes: 240, points: 1, amount: 30, inPayroll: false },
      { id: "a3", employeeId: "e3", employeeName: "Late Joiner", positionTitle: null, minutes: 20, points: 1, amount: 0, inPayroll: false },
    ],
    calculatedByMe: false,
    ...overrides,
  };
}

describe("tips", () => {
  beforeEach(() => {
    permissions.list = ["hr.tips.manage"];
    api.getTipPool.mockReset();
    api.tipPoolAction.mockReset();
  });

  test("givenCalculatedPool_whenOpened_thenSharesAndHouseShownAndUnsharedExplained", async () => {
    api.getTipPool.mockResolvedValue(pool());
    render(<TipPoolDrawer id="t1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByText("Ama Waiter")).toBeInTheDocument();
    expect(screen.getByText("Kept by the house")).toBeInTheDocument();
    expect(screen.getByText("1 person gets no share (below the minimum time or no points for their position).")).toBeInTheDocument();
  });

  test("givenPoolCalculatedByMe_whenOpened_thenApproveDisabled", async () => {
    api.getTipPool.mockResolvedValue(pool({ calculatedByMe: true }));
    render(<TipPoolDrawer id="t1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByRole("button", { name: "Approve" })).toBeDisabled();
  });

  test("givenServiceChargePool_whenUserOnlyManagesTips_thenNoActions", async () => {
    api.getTipPool.mockResolvedValue(pool({ summary: { ...pool().summary, source: "SERVICE_CHARGE" } }));
    render(<TipPoolDrawer id="t1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByText("Ama Waiter")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  test("givenCalculatedPool_whenApproved_thenActionSent", async () => {
    api.getTipPool.mockResolvedValue(pool());
    api.tipPoolAction.mockResolvedValue(pool({}, "APPROVED"));
    render(<TipPoolDrawer id="t1" onClose={() => undefined} onChanged={() => undefined} />);
    await userEvent.click(await screen.findByRole("button", { name: "Approve" }));
    expect(await screen.findByText("Approved. Shares will be paid with each person's next payroll.")).toBeInTheDocument();
    expect(api.tipPoolAction.mock.calls[0][2]).toBe("approve");
  });
});
