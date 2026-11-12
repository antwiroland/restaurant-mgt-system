import { formatMoney } from "@/lib/brand";
import type { PayrollRunStatus } from "@/lib/hrApi";

export const runTone: Record<PayrollRunStatus, string> = {
  DRAFT: "badge-neutral",
  CALCULATED: "badge-info",
  APPROVED: "badge-pending",
  FINALIZED: "badge-gold",
  PAID: "badge-success",
  CANCELLED: "badge-danger",
};

/** Issue codes that block approval (mirrors PayrollService.ERRORS). */
export const BLOCKING_ISSUES = new Set(["UNLOCKED_TIMESHEETS", "NEGATIVE_NET"]);

export const issueLabel: Record<string, string> = {
  UNLOCKED_TIMESHEETS: "Timesheets in this period are not locked",
  NEGATIVE_NET: "Deductions exceed pay",
  UNLOCKED_OVERTIME: "Overtime from unlocked timesheets left out",
  PARTIAL_PERIOD: "Employed for part of the period",
  MANUAL_PAY_TYPE: "Pay type needs a manual adjustment",
  TIMESHEET_OUTSIDE_PERIOD: "A timesheet extends outside this period and was left out",
};

export function money(value: number | null | undefined, currency?: string): string {
  return formatMoney(value ?? 0, currency);
}
