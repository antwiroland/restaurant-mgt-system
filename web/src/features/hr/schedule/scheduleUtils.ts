import { isoDate } from "@/features/workforce/time";
import type { Shift, WeekDay } from "@/lib/hrApi";

/** ISO instant for a local date (yyyy-mm-dd) and time (hh:mm). */
export function localInstant(date: string, time: string): string {
  return new Date(`${date}T${time}:00`).toISOString();
}

/** Start/end instants for a shift entered as date + times; an end at or before the start means the next day. */
export function shiftInstants(date: string, start: string, end: string): { startsAt: string; endsAt: string; overnight: boolean } {
  const overnight = end <= start;
  const endDate = overnight ? isoDate(new Date(new Date(`${date}T00:00:00`).getTime() + 36 * 3600 * 1000)) : date;
  return { startsAt: localInstant(date, start), endsAt: localInstant(endDate, end), overnight };
}

/** Local yyyy-mm-dd of the day a shift starts. */
export function shiftDay(shift: Pick<Shift, "startsAt">): string {
  return isoDate(new Date(shift.startsAt));
}

export function hhmm(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function shiftLabel(shift: Pick<Shift, "startsAt" | "endsAt">): string {
  return `${hhmm(shift.startsAt)}–${hhmm(shift.endsAt)}`;
}

export const DAY_CODES: WeekDay[] = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];

export function dayCode(date: Date): WeekDay {
  return DAY_CODES[(date.getDay() + 6) % 7];
}

/** "08:00:00" or "08:00" → "08:00". */
export function trimTime(value?: string | null): string {
  return value ? value.slice(0, 5) : "";
}

/** Paid hours per employee for the visible shifts (cancelled shifts excluded). */
export function paidMinutesByEmployee(shifts: Shift[]): Map<string, number> {
  const totals = new Map<string, number>();
  shifts.forEach((s) => {
    if (s.status === "CANCELLED" || !s.employeeId) return;
    totals.set(s.employeeId, (totals.get(s.employeeId) ?? 0) + s.paidMinutes);
  });
  return totals;
}
