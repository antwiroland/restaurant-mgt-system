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
  NO_PAYE_RULE: "No PAYE income tax rule applies: tax was not deducted",
  NO_SSNIT_RULE: "No SSNIT rule applies: social security was not deducted",
  NO_SSNIT_NUMBER: "SSNIT was deducted but the employee has no SSNIT number on record",
  NO_TIN: "PAYE was deducted but the employee has no TIN or Ghana Card number on record",
  FINAL_PAY: "Final pay: unused annual leave paid out and loans recovered in full",
};

export function money(value: number | null | undefined, currency?: string): string {
  return formatMoney(value ?? 0, currency);
}
