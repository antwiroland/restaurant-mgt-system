import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { IssuesList } from "@/features/hr/schedule/ShiftDialog";
import { dayCode, hhmm, paidMinutesByEmployee, shiftDay, shiftInstants, trimTime } from "@/features/hr/schedule/scheduleUtils";
import type { Shift } from "@/lib/hrApi";

const base: Omit<Shift, "id" | "employeeId" | "status" | "paidMinutes"> = {
  branchId: "b", startsAt: "", endsAt: "", breakMinutes: 0, version: 0,
};

describe("schedule time helpers", () => {
  test("givenDaytimeTimes_whenConverted_thenSameDayAndNotOvernight", () => {
    const { startsAt, endsAt, overnight } = shiftInstants("2026-11-16", "09:00", "17:30");
    expect(overnight).toBe(false);
    expect(hhmm(startsAt)).toBe("09:00");
    expect(hhmm(endsAt)).toBe("17:30");
    expect(shiftDay({ startsAt })).toBe("2026-11-16");
    expect(new Date(endsAt).getTime() - new Date(startsAt).getTime()).toBe(8.5 * 3600 * 1000);
  });

  test("givenEndBeforeStart_whenConverted_thenEndsNextDay", () => {
    const { startsAt, endsAt, overnight } = shiftInstants("2026-11-16", "22:00", "06:00");
    expect(overnight).toBe(true);
    expect(shiftDay({ startsAt })).toBe("2026-11-16");
    expect(shiftDay({ startsAt: endsAt })).toBe("2026-11-17");
    expect(new Date(endsAt).getTime() - new Date(startsAt).getTime()).toBe(8 * 3600 * 1000);
  });

  test("givenDates_whenAskingDayCode_thenMondayFirstCodes", () => {
    expect(dayCode(new Date("2026-11-16T12:00:00"))).toBe("MON");
    expect(dayCode(new Date("2026-11-22T12:00:00"))).toBe("SUN");
    expect(trimTime("08:00:00")).toBe("08:00");
  });

  test("givenShifts_whenTotalling_thenCancelledAndOpenShiftsIgnored", () => {
    const shifts: Shift[] = [
      { ...base, id: "1", employeeId: "a", status: "PUBLISHED", paidMinutes: 450 },
      { ...base, id: "2", employeeId: "a", status: "DRAFT", paidMinutes: 240 },
      { ...base, id: "3", employeeId: "a", status: "CANCELLED", paidMinutes: 480 },
      { ...base, id: "4", employeeId: null, status: "PUBLISHED", paidMinutes: 480 },
    ];
    const totals = paidMinutesByEmployee(shifts);
    expect(totals.get("a")).toBe(690);
    expect(totals.size).toBe(1);
  });
});

describe("Issues list", () => {
  test("givenBlockingAndWarnings_whenRendered_thenGroupedSeparately", () => {
    render(
      <IssuesList
        issues={[
          { code: "OVERLAP", severity: "BLOCKING", message: "Overlaps another shift (MON 09:00–17:00)" },
          { code: "SHORT_REST", severity: "WARNING", message: "Only 7h rest between shifts; the minimum is 8h" },
        ]}
      />,
    );
    expect(screen.getByText("This can't be saved:")).toBeInTheDocument();
    expect(screen.getByText(/Overlaps another shift/)).toBeInTheDocument();
    expect(screen.getByText("Please check:")).toBeInTheDocument();
    expect(screen.getByText(/Only 7h rest/)).toBeInTheDocument();
  });
});
