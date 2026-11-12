import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "Clerk", role: "CASHIER" } };

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a>,
}));

const api = vi.hoisted(() => ({
  getPayout: vi.fn(),
  updatePayout: vi.fn(),
  getMyPayout: vi.fn(),
  updateMyPayout: vi.fn(),
  getPaymentFiles: vi.fn(),
  downloadPaymentFile: vi.fn(),
  getPayslip: vi.fn(),
  downloadPayslipPdf: vi.fn(),
}));
vi.mock("@/lib/hrApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/hrApi")>()), ...api }));
const saved = vi.hoisted(() => ({ files: [] as unknown[] }));
vi.mock("@/lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/apiClient")>()),
  saveDownloadedFile: (file: unknown) => saved.files.push(file),
}));

import { PayoutPanel } from "@/features/hr/payroll/PayoutPanel";
import { PaymentFilesPanel } from "@/features/hr/payroll/PaymentFilesPanel";
import { PayslipDrawer } from "@/features/hr/payroll/PayslipDrawer";

beforeEach(() => {
  vi.clearAllMocks();
  saved.files = [];
});

describe("Payout details", () => {
  test("an employee adds Mobile Money details for themselves", async () => {
    api.getMyPayout.mockResolvedValue({ employeeId: "e1", employeeName: "Kwesi Chef", method: null, masked: false, complete: false });
    api.updateMyPayout.mockResolvedValue({ employeeId: "e1", employeeName: "Kwesi Chef", method: "MOBILE_MONEY", momoNetwork: "MTN", momoNumber: "0244000001", masked: false, complete: true });
    const user = userEvent.setup();
    render(<PayoutPanel self canEdit />);

    await user.click(await screen.findByRole("button", { name: "Mobile Money" }));
    await user.selectOptions(screen.getByLabelText("Network"), "MTN");
    await user.type(screen.getByLabelText("Mobile Money number"), "0244000001");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(api.updateMyPayout).toHaveBeenCalledWith(session, { method: "MOBILE_MONEY", momoNetwork: "MTN", momoNumber: "0244000001" });
    expect(await screen.findByText("Payout details saved.")).toBeInTheDocument();
    expect(screen.getByText("0244000001")).toBeInTheDocument();
  });

  test("a payroll viewer sees masked numbers and cannot edit", async () => {
    api.getPayout.mockResolvedValue({
      employeeId: "e2", employeeName: "Ama Cook", method: "BANK", bankName: "GCB", bankAccountName: "Ama Cook",
      bankAccountNumber: "********0123", masked: true, complete: true,
    });
    render(<PayoutPanel employeeId="e2" canEdit />);
    expect(await screen.findByText("********0123")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Change/ })).not.toBeInTheDocument();
  });
});

describe("Payment files", () => {
  test("downloads a file per method and lists who is missing details", async () => {
    api.getPaymentFiles.mockResolvedValue({
      runId: "r1", runNumber: "PAY-000001", status: "FINALIZED", currency: "GHS", ready: true,
      groups: [{ method: "BANK", people: 1, total: 2600.5 }, { method: "MOBILE_MONEY", people: 2, total: 5000 }],
      missing: [{ employeeId: "e3", employeeName: "Yaw Waiter", employeeNumber: "EMP-000003", net: 2600.5, problem: "No payout method set" }],
    });
    api.downloadPaymentFile.mockResolvedValue({ blob: new Blob(["x"]), filename: "PAY-000001-bank-payments.csv" });
    const user = userEvent.setup();
    render(<PaymentFilesPanel runId="r1" refreshKey={0} />);

    expect(await screen.findByText("Bank transfer")).toBeInTheDocument();
    expect(screen.getByText("Mobile Money")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Yaw Waiter" })).toHaveAttribute("href", "/hr/employees/e3");
    await user.click(screen.getAllByRole("button", { name: "Download CSV" })[0]);
    await waitFor(() => expect(api.downloadPaymentFile).toHaveBeenCalledWith(session, "r1", "BANK"));
    expect(saved.files).toHaveLength(1);
  });
});

describe("Payslip PDF", () => {
  test("the payslip can be downloaded as PDF", async () => {
    api.getPayslip.mockResolvedValue({
      summary: { id: "p1", runId: "r1", employeeId: "e1", employeeName: "Kwesi Chef", employeeNumber: "EMP-000001", salaryType: "MONTHLY", rate: 3000,
        paidMinutes: 0, overtimeMinutes: 0, workedDays: 0, unpaidLeaveDays: 0, basic: 3000, gross: 3000, taxable: 3000, employeeDeductions: 0,
        net: 3000, employerContributions: 0, issues: [] },
      runNumber: "PAY-000001", runStatus: "FINALIZED", periodStart: "2026-10-01", periodEnd: "2026-10-31", currency: "GHS", lines: [],
    });
    api.downloadPayslipPdf.mockResolvedValue({ blob: new Blob(["%PDF"]), filename: "payslip-EMP-000001-2026-10-01.pdf" });
    const user = userEvent.setup();
    render(<PayslipDrawer id="p1" onClose={() => {}} />);

    await user.click(await screen.findByRole("button", { name: "Download PDF" }));
    await waitFor(() => expect(api.downloadPayslipPdf).toHaveBeenCalledWith(session, "p1"));
    expect(saved.files).toHaveLength(1);
  });
});
