import type { ApprovalStep, LeaveRequest, LeaveStatus } from "@/lib/hrApi";

export const leaveTone: Record<LeaveStatus, string> = {
  PENDING: "badge-pending",
  APPROVED: "badge-success",
  REJECTED: "badge-danger",
  CANCELLED: "badge-neutral",
};

export const stepLabel: Record<ApprovalStep, string> = {
  REPORTING_MANAGER: "Reporting manager",
  BRANCH_MANAGER: "Branch manager",
  HR: "HR",
};

/** "5", "3.5" — leave days without trailing zeros. */
export function formatDays(days: number | null | undefined): string {
  if (days == null) return "—";
  return Number(days).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

export function plural(days: number): string {
  return `${formatDays(days)} ${Number(days) === 1 ? "day" : "days"}`;
}

/** "12 Mar – 16 Mar 2027 (half day on 16 Mar)". */
export function leaveDates(r: Pick<LeaveRequest, "startDate" | "endDate" | "startHalf" | "endHalf">): string {
  const fmt = (d: string, year = false) =>
    new Date(`${d}T00:00:00`).toLocaleDateString(undefined, { day: "numeric", month: "short", ...(year ? { year: "numeric" } : {}) });
  const range = r.startDate === r.endDate ? fmt(r.startDate, true) : `${fmt(r.startDate)} – ${fmt(r.endDate, true)}`;
  const halves: string[] = [];
  if (r.startHalf) halves.push(fmt(r.startDate));
  if (r.endHalf && r.endDate !== r.startDate) halves.push(fmt(r.endDate));
  return halves.length ? `${range} (half day on ${halves.join(" and ")})` : range;
}

/** Where a pending request is waiting, for list views. */
export function waitingFor(r: LeaveRequest): string {
  if (r.status !== "PENDING" || !r.pendingStep) return "";
  return `Waiting for ${stepLabel[r.pendingStep].toLowerCase()}${r.steps.length > 1 ? ` (step ${r.steps.findIndex((s) => !s.decision) + 1} of ${r.steps.length})` : ""}`;
}
