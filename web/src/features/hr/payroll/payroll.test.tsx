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

const api = vi.hoisted(() => ({
  getPayrollRun: vi.fn(),
  payrollRunAction: vi.fn(),
  getPayslip: vi.fn(),
  listEmployees: vi.fn(),
}));
vi.mock("@/lib/hrApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/hrApi")>()), ...api }));

import { PayrollRunDrawer } from "@/features/hr/payroll/PayrollRunDrawer";
import { PayslipDrawer } from "@/features/hr/payroll/PayslipDrawer";
import type { PayrollRunDetail, PayslipDetail } from "@/lib/hrApi";

const slip = {
  id: "p1", runId: "r1", employeeId: "e1", employeeName: "Kwesi Chef", employeeNumber: "EMP-000001", salaryType: "MONTHLY" as const, rate: 3000,
  paidMinutes: 0, overtimeMinutes: 0, workedDays: 0, unpaidLeaveDays: 0, basic: 3000, gross: 3000, taxable: 2835, employeeDeductions: 399.5,
  net: 2600.5, employerContributions: 390, issues: [] as string[],
};

function run(overrides: Partial<PayrollRunDetail["summary"]> = {}, extra: Partial<PayrollRunDetail> = {}): PayrollRunDetail {
  return {
    summary: {
      id: "r1", runNumber: "PAY-000001", branchId: "b1", branchName: "Osu", periodStart: "2026-10-01", periodEnd: "2026-10-31", status: "CALCULATED",
      currency: "GHS", employeeCount: 1, grossTotal: 3000, deductionsTotal: 399.5, netTotal: 2600.5, employerTotal: 390, paidCount: 0,
      calculatedAt: "2026-11-01T10:00:00Z", version: 1, ...overrides,
    },
    payslips: [slip], adjustments: [], skipped: [], blockingPayslips: 0, calculatedByMe: false, ...extra,
  };
}

describe("payroll", () => {
  beforeEach(() => {
    permissions.list = [];
    Object.values(api).forEach((fn) => fn.mockReset());
    api.listEmployees.mockResolvedValue({ items: [], page: 0, size: 0, total: 0 });
  });

  test("givenRunCalculatedByMe_whenApproverOpensIt_thenApproveDisabled", async () => {
    permissions.list = ["hr.payroll.process", "hr.payroll.approve"];
    api.getPayrollRun.mockResolvedValue(run({}, { calculatedByMe: true }));
    render(<PayrollRunDrawer id="r1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByText("Kwesi Chef")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();
  });

  test("givenBlockingPayslip_whenShown_thenApproveDisabledAndProblemListed", async () => {
    permissions.list = ["hr.payroll.approve"];
    api.getPayrollRun.mockResolvedValue(run({}, { blockingPayslips: 1, payslips: [{ ...slip, issues: ["UNLOCKED_TIMESHEETS"] }] }));
    render(<PayrollRunDrawer id="r1" onClose={() => undefined} onChanged={() => undefined} />);
    expect(await screen.findByText("1 payslip must be fixed before approval.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();
  });

  test("givenApprovedRun_whenFinalized_thenActionSent", async () => {
    permissions.list = ["hr.payroll.finalize"];
    api.getPayrollRun.mockResolvedValue(run({ status: "APPROVED" }));
    api.payrollRunAction.mockResolvedValue(run({ status: "FINALIZED" }));
    render(<PayrollRunDrawer id="r1" onClose={() => undefined} onChanged={() => undefined} />);
    await userEvent.click(await screen.findByRole("button", { name: "Finalize" }));
    expect(await screen.findByText("Finalized. Employees can now see their payslips.")).toBeInTheDocument();
    expect(api.payrollRunAction.mock.calls[0][2]).toBe("finalize");
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  test("givenPayslip_whenOpened_thenEarningsDeductionsAndEmployerPartShown", async () => {
    const detail: PayslipDetail = {
      summary: slip, runNumber: "PAY-000001", runStatus: "FINALIZED", periodStart: "2026-10-01", periodEnd: "2026-10-31", currency: "GHS",
      lines: [
        { kind: "EARNING", code: "BASIC", description: "Monthly salary", quantity: 31, rate: 3000, amount: 3000 },
        { kind: "DEDUCTION", code: "PENSION", description: "Pension", rate: 5.5, amount: 165 },
        { kind: "DEDUCTION", code: "TAX", description: "Income tax", amount: 234.5 },
        { kind: "EMPLOYER", code: "ER", description: "Employer pension", rate: 13, amount: 390 },
      ],
    };
    api.getPayslip.mockResolvedValue(detail);
    render(<PayslipDrawer id="p1" onClose={() => undefined} />);
    expect(await screen.findByText("Monthly salary")).toBeInTheDocument();
    expect(screen.getByText("Income tax")).toBeInTheDocument();
    expect(screen.getByText("Paid by the employer (not deducted)")).toBeInTheDocument();
    expect(screen.getByText("Net pay")).toBeInTheDocument();
  });
});
