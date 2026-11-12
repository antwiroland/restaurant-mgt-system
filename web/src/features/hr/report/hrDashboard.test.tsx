import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "HR Officer", role: "HR" } };
const can = vi.hoisted(() => ({ value: true }));

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));
vi.mock("@/features/hr/HrAccessContext", () => ({
  useHrAccess: () => ({ access: { allBranches: true, branchIds: [] }, loading: false, can: () => can.value }),
}));
const api = vi.hoisted(() => ({ getHrDashboard: vi.fn(), updateMyProfile: vi.fn(), getMyContracts: vi.fn(), getMyDocuments: vi.fn() }));
vi.mock("@/lib/hrApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/hrApi")>()), ...api }));

import HrDashboardPage from "@/app/(staff)/hr/page";
import { routeByRole } from "@/features/auth/auth";
import { MyContactDetails } from "@/features/hr/MySelfService";
import { canAccessPath, navForRole } from "@/lib/navigation";
import type { EmployeeDetail, HrDashboard } from "@/lib/hrApi";

function dashboard(overrides: Partial<HrDashboard> = {}): HrDashboard {
  return {
    asOf: "2026-11-12", branchId: null,
    branches: [{ id: "b1", name: "Osu" }, { id: "b2", name: "East Legon" }],
    headcount: { employed: 22, startingSoon: 1, byStatus: { ACTIVE: 20, ON_LEAVE: 1, SUSPENDED: 1 },
      byDepartment: [{ label: "Kitchen", count: 8 }], byEmploymentType: [{ label: "FULL_TIME", count: 15 }], byBranch: [{ label: "Osu", count: 14 }] },
    movement: { joiners: [{ employeeId: "e1", name: "Comfort Adu", date: "2026-11-12", detail: "Line Cook" }], leavers: [], leaversLast12Months: 1, turnoverPercent: 4.8 },
    trend: [{ month: "2026-10", headcount: 21, joined: 0, left: 1 }, { month: "2026-11", headcount: 22, joined: 1, left: 0 }],
    today: { counts: { ON_SHIFT: 2, ON_BREAK: 1, UNSCHEDULED: 1, ABSENT: 2 }, lateArrivals: 1 },
    actions: [{ key: "LEAVE_PENDING", label: "Leave requests waiting", count: 2, href: "/hr/leave" },
      { key: "SWAPS_PENDING", label: "Shift swaps to decide", count: 0, href: "/hr/schedule" }],
    expiring: [{ kind: "CONTRACT_END", employeeId: "e2", employeeName: "Edem Kpodo", detail: "Contract CON-1 ends", date: "2026-11-22", overdue: false }],
    recruitment: { openVacancies: 2, published: 1, openingsLeft: 3, pipeline: { APPLIED: 1, OFFER: 1 }, interviewsNext7Days: 2 },
    labour: [], payVisible: false,
    ...overrides,
  };
}

describe("HR role in the web app", () => {
  test("givenHrRole_whenNavigating_thenOnlyHrAndOwnAccountAreReachable", () => {
    expect(routeByRole("HR")).toBe("/hr");
    const groups = navForRole("HR", () => true).map((g) => g.title);
    expect(groups).toEqual(["Human Resources", "You"]);
    for (const path of ["/dashboard", "/pos", "/orders", "/kds", "/reports", "/users", "/schedule", "/somewhere-new"]) {
      expect(canAccessPath("HR", path)).toBe(false);
    }
    for (const path of ["/hr", "/hr/payroll", "/hr/employees/123", "/hr/me", "/account"]) {
      expect(canAccessPath("HR", path)).toBe(true);
    }
    expect(canAccessPath("MANAGER", "/hr")).toBe(true);
    expect(canAccessPath("MANAGER", "/somewhere-new")).toBe(true); // unchanged for other roles
  });
});

describe("HR dashboard", () => {
  beforeEach(() => {
    can.value = true;
    api.getHrDashboard.mockReset();
  });

  test("givenDashboard_whenShown_thenHeadcountTodayActionsAndExpiriesAppear_payHidden", async () => {
    api.getHrDashboard.mockResolvedValue(dashboard());
    render(<HrDashboardPage />);
    const tile = (await screen.findAllByText("Employed")).map((el) => el.closest("article")).find(Boolean);
    expect(tile).toHaveTextContent("22");
    expect(screen.getByText("4.8% turnover")).toBeInTheDocument();
    const today = screen.getByRole("heading", { name: "Today" }).closest("section")!;
    expect(within(today).getByText("On shift now").nextSibling).toHaveTextContent("4");
    expect(screen.getByRole("link", { name: /Leave requests waiting/ })).toHaveAttribute("href", "/hr/leave");
    expect(screen.getByRole("link", { name: /Edem Kpodo/ })).toHaveAttribute("href", "/hr/employees/e2");
    expect(screen.getByText("Comfort Adu")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Labour cost" })).not.toBeInTheDocument();
  });

  test("givenPayVisible_whenShown_thenLabourCostTableAppears_andBranchFilterReloads", async () => {
    api.getHrDashboard.mockResolvedValue(dashboard({
      payVisible: true,
      labour: [{ branchId: "b1", branchName: "Osu", runNumber: "PAY-000001", periodStart: "2026-10-01", periodEnd: "2026-10-31", status: "PAID",
        employees: 10, grossPay: 25000, employerCost: 3000, totalCost: 28000, sales: 100000, labourPercent: 28, overtimeMinutes: 300 }],
    }));
    render(<HrDashboardPage />);
    expect(await screen.findByRole("heading", { name: "Labour cost" })).toBeInTheDocument();
    expect(screen.getByText("28%")).toBeInTheDocument();
    expect(screen.getByText("5.0 h")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Branch"), "b2");
    expect(api.getHrDashboard).toHaveBeenLastCalledWith(expect.anything(), "b2");
  });

  test("givenNoReportsPermission_whenOpened_thenAccessMessageInsteadOfData", () => {
    can.value = false;
    render(<HrDashboardPage />);
    expect(screen.getByText("HR reports access required")).toBeInTheDocument();
    expect(api.getHrDashboard).not.toHaveBeenCalled();
  });
});

describe("Self-service contact details", () => {
  test("givenOwnProfile_whenPhoneChanged_thenOnlyContactFieldsAreSentWithVersion", async () => {
    const employee = {
      summary: { id: "e1", phone: "+233201000001", email: null },
      firstName: "Kofi", address: "1 Ring Road", emergencyContacts: [{ name: "Ama", relationship: "Sister", phone: "+233201111111" }],
      version: 3,
    } as unknown as EmployeeDetail;
    api.updateMyProfile.mockResolvedValue({ ...employee, summary: { ...employee.summary, phone: "+233209999999" }, version: 4 });
    const onSaved = vi.fn();
    render(<MyContactDetails employee={employee} onSaved={onSaved} />);
    await userEvent.click(screen.getByRole("button", { name: /Update my details/ }));
    const form = screen.getByRole("form", { name: "Update my contact details" });
    const phone = within(form).getAllByLabelText("Phone")[0];
    await userEvent.clear(phone);
    await userEvent.type(phone, "+233209999999");
    await userEvent.click(within(form).getByRole("button", { name: "Save" }));
    expect(api.updateMyProfile).toHaveBeenCalledWith(expect.anything(), {
      phone: "+233209999999", email: null, address: "1 Ring Road",
      emergencyContacts: [{ name: "Ama", relationship: "Sister", phone: "+233201111111", email: null }], version: 3,
    });
    expect(onSaved).toHaveBeenCalled();
  });
});
