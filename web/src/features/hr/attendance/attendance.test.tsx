import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";

const session = { accessToken: "t", refreshToken: "r", expiresAtEpochMs: 0, user: { id: "u", name: "Till", role: "CASHIER" } };

vi.mock("@/components/SessionProvider", () => ({
  useStaffSession: () => ({ session, authenticatedFetch: (run: (s: typeof session) => Promise<unknown>) => run(session) }),
}));

const api = vi.hoisted(() => ({
  getPosRoster: vi.fn(),
  posClock: vi.fn(),
  posSummary: vi.fn(),
}));

vi.mock("@/lib/hrApi", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/lib/hrApi")>()), ...api }));

import { StaffClockPanel } from "@/features/hr/attendance/StaffClockPanel";
import { metricsLine } from "@/features/hr/attendance/AttendancePanels";
import { newRequestKey, type AttendanceEntry } from "@/lib/hrApi";

const roster = [
  { employeeId: "e1", displayName: "Kwesi C.", positionTitle: "Line Cook", hasPin: true, state: "OFF" },
  { employeeId: "e2", displayName: "Gifty A.", positionTitle: "Cleaner", hasPin: false, state: "OFF" },
  { employeeId: "e3", displayName: "Dzifa T.", positionTitle: "Waiter", hasPin: true, state: "ON_SHIFT" },
];

async function typePin(digits: string) {
  for (const d of digits) await userEvent.click(screen.getByRole("button", { name: d }));
}

describe("POS staff clock", () => {
  beforeEach(() => {
    api.getPosRoster.mockReset().mockResolvedValue(roster);
    api.posClock.mockReset();
    api.posSummary.mockReset();
  });

  test("givenRoster_whenPersonPicksNameAndPin_thenClockInSentWithKeyAndPinNotShown", async () => {
    api.posClock.mockResolvedValue({ message: "Kwesi clocked in at 09:02", duplicate: false });
    render(<StaffClockPanel onClose={() => undefined} />);

    expect(await screen.findByText("Kwesi C.")).toBeInTheDocument();
    expect(screen.getByText("Gifty A.").closest("button")).toBeDisabled(); // no PIN set
    await userEvent.click(screen.getByText("Kwesi C."));

    const clockIn = screen.getByRole("button", { name: "Clock in" });
    expect(clockIn).toBeDisabled();
    await typePin("258");
    expect(clockIn).toBeDisabled();
    await typePin("0");
    expect(screen.queryByText("2580")).not.toBeInTheDocument();
    await userEvent.click(clockIn);

    expect(await screen.findByText("Kwesi clocked in at 09:02")).toBeInTheDocument();
    expect(api.posClock).toHaveBeenCalledTimes(1);
    const body = api.posClock.mock.calls[0][1];
    expect(body).toMatchObject({ employeeId: "e1", pin: "2580", action: "CLOCK_IN" });
    expect(body.idempotencyKey).toMatch(/^[0-9a-f-]{32,36}$/);
  });

  test("givenPersonOnShift_whenPinEntered_thenBreakAndClockOutOffered", async () => {
    render(<StaffClockPanel onClose={() => undefined} />);
    await userEvent.click(await screen.findByText("Dzifa T."));
    expect(screen.queryByRole("button", { name: "Clock in" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Meal break" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Clock out" })).toBeInTheDocument();
  });

  test("givenNetworkDrop_whenClocking_thenRetriedWithTheSameKey", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    api.posClock.mockRejectedValueOnce(new TypeError("Failed to fetch")).mockResolvedValueOnce({ message: "Kwesi clocked in at 09:02" });
    render(<StaffClockPanel onClose={() => undefined} />);
    await userEvent.click(await screen.findByText("Kwesi C."));
    await typePin("2580");
    await userEvent.click(screen.getByRole("button", { name: "Clock in" }));
    await act(async () => { vi.advanceTimersByTime(1000); });
    await waitFor(() => expect(api.posClock).toHaveBeenCalledTimes(2));
    expect(api.posClock.mock.calls[0][1].idempotencyKey).toBe(api.posClock.mock.calls[1][1].idempotencyKey);
    expect(await screen.findByText("Kwesi clocked in at 09:02")).toBeInTheDocument();
    vi.useRealTimers();
  });

  test("givenServerRefusal_whenClocking_thenMessageShownAndNotRetried", async () => {
    api.posClock.mockRejectedValue(new Error("Incorrect PIN"));
    render(<StaffClockPanel onClose={() => undefined} />);
    await userEvent.click(await screen.findByText("Kwesi C."));
    await typePin("1111");
    await userEvent.click(screen.getByRole("button", { name: "Clock in" }));
    expect(await screen.findByText("Incorrect PIN")).toBeInTheDocument();
    expect(api.posClock).toHaveBeenCalledTimes(1);
  });
});

describe("attendance helpers", () => {
  test("givenRequestKeys_whenGenerated_thenUnique", () => {
    const keys = new Set(Array.from({ length: 50 }, () => newRequestKey()));
    expect(keys.size).toBe(50);
  });

  test("givenMetrics_whenSummarised_thenOnlyNonZeroFactsShown", () => {
    const entry = { metrics: { paidMinutes: 450, lateMinutes: 12, earlyLeaveMinutes: 0, overtimeMinutes: 30 } } as unknown as AttendanceEntry;
    expect(metricsLine(entry)).toBe("7h 30m paid · 12 min late · 0h 30m over");
  });
});
