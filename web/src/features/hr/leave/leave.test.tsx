import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "Kwesi", role: "CASHIER" } };
const permissions = vi.hoisted(() => ({ list: [] as string[] }));

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));
vi.mock("@/features/hr/HrAccessContext", () => ({
  useHrAccess: () => ({ access: null, loading: false, can: (...codes: string[]) => codes.some((c) => permissions.list.includes(c)) }),
}));

const api = vi.hoisted(() => ({
  getLeaveTypes: vi.fn(),
  quoteMyLeave: vi.fn(),
  requestMyLeave: vi.fn(),
  getLeaveRequest: vi.fn(),
  decideLeave: vi.fn(),
}));
vi.mock("@/lib/hrApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/hrApi")>()), ...api }));

import { LeaveRequestForm } from "@/features/hr/leave/LeaveRequestForm";
import { LeaveRequestDrawer } from "@/features/hr/leave/LeaveRequestDrawer";
import { formatDays, leaveDates } from "@/features/hr/leave/leaveUtils";
import type { LeaveRequest } from "@/lib/hrApi";

const annual = {
  id: "t1", code: "ANNUAL", name: "Annual leave", paid: true, deductsBalance: true, requiresDocument: false, allowNegative: false,
  countBasis: "WORKDAYS", approvalSteps: ["BRANCH_MANAGER"], maxDaysPerRequest: null, active: true, displayOrder: 1,
};

const pending: LeaveRequest = {
  id: "r1", employeeId: "e1", employeeName: "Kwesi Cook", leaveTypeId: "t1", leaveTypeCode: "ANNUAL", leaveTypeName: "Annual leave", paid: true,
  startDate: "2027-03-01", endDate: "2027-03-05", startHalf: false, endHalf: true, days: 4.5, status: "PENDING",
  steps: [{ step: 0, code: "BRANCH_MANAGER" }], pendingStep: "BRANCH_MANAGER", canDecide: true, documentRequired: false,
  createdAt: "2026-11-12T08:00:00Z", conflictingShifts: [{ scheduleId: "s1", startsAt: "2027-03-02T09:00:00Z", endsAt: "2027-03-02T17:00:00Z", status: "PUBLISHED" }],
};

function setDates(from: string, to: string) {
  fireEvent.change(screen.getByLabelText("First day"), { target: { value: from } });
  fireEvent.change(screen.getByLabelText("Last day"), { target: { value: to } });
}

describe("leave", () => {
  beforeEach(() => {
    permissions.list = [];
    Object.values(api).forEach((fn) => fn.mockReset());
    api.getLeaveTypes.mockResolvedValue([annual, { ...annual, id: "t2", code: "OLD", name: "Retired type", active: false }]);
  });

  test("givenHalfDayRange_whenFormatted_thenShowsHalfDayAndTrimmedNumbers", () => {
    expect(formatDays(4.5)).toBe("4.5");
    expect(formatDays(5)).toBe("5");
    expect(leaveDates(pending)).toContain("half day on");
    expect(leaveDates({ ...pending, endHalf: false })).not.toContain("half day");
  });

  test("givenDates_whenQuoteHasNoProblems_thenRequestSentWithDays", async () => {
    api.quoteMyLeave.mockResolvedValue({ days: 5, availableDays: 20, deductsBalance: true, requiresDocument: false, problems: [] });
    api.requestMyLeave.mockResolvedValue({ ...pending, days: 5 });
    const onSaved = vi.fn();
    render(<LeaveRequestForm onClose={() => undefined} onSaved={onSaved} />);

    await waitFor(() => expect(screen.getByRole("option", { name: "Annual leave" })).toBeInTheDocument());
    expect(screen.queryByRole("option", { name: "Retired type" })).not.toBeInTheDocument();
    setDates("2027-03-01", "2027-03-07");

    expect(await screen.findByText(/^5 days/)).toBeInTheDocument();
    expect(screen.getByText(/20 days available on the first day; 15 left afterwards/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Send request" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(api.requestMyLeave.mock.calls[0][1]).toMatchObject({ leaveTypeId: "t1", startDate: "2027-03-01", endDate: "2027-03-07" });
  });

  test("givenQuoteProblem_whenShown_thenSubmitDisabled", async () => {
    api.quoteMyLeave.mockResolvedValue({ days: 5, availableDays: 3, deductsBalance: true, requiresDocument: false, problems: ["Only 3 days available"] });
    render(<LeaveRequestForm onClose={() => undefined} onSaved={() => undefined} />);
    await waitFor(() => expect(screen.getByRole("option", { name: "Annual leave" })).toBeInTheDocument());
    setDates("2027-03-01", "2027-03-05");
    expect(await screen.findByText("Only 3 days available")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send request" })).toBeDisabled();
  });

  test("givenRequestAwaitingMe_whenDeclining_thenNoteRequired_andApproveSendsDecision", async () => {
    permissions.list = ["hr.leave.approve"];
    api.getLeaveRequest.mockResolvedValue(pending);
    api.decideLeave.mockResolvedValue({ ...pending, status: "APPROVED", canDecide: false, pendingStep: null });
    render(<LeaveRequestDrawer id="r1" onClose={() => undefined} onChanged={() => undefined} />);

    expect(await screen.findByText("1 shift during this leave")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Decline" })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Note (required to decline)"), "Short-staffed");
    expect(screen.getByRole("button", { name: "Decline" })).toBeEnabled();

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(await screen.findByText("Leave approved.")).toBeInTheDocument();
    expect(api.decideLeave.mock.calls[0][2]).toEqual({ approve: true, note: "Short-staffed" });
  });
});
