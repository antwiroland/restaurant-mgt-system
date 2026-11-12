import type { TablePaymentWatchRecord, WalkoutReason } from "@/lib/apiClient";

/** Short status line for a table that owes money, e.g. "Served 25 min ago · not paid". */
export function watchStatus(watch: TablePaymentWatchRecord): string {
  if (watch.risk === "BILL_WAITING") return `Asked for the bill ${minutes(watch.minutesWaiting)} ago`;
  if (watch.risk === "SERVED_UNPAID") return `Served ${minutes(watch.minutesWaiting)} ago · not paid`;
  if (watch.billRequestedAt) return "Asked for the bill";
  if (watch.allServed) return "All served · not paid";
  return `${watch.openOrders} order${watch.openOrders === 1 ? "" : "s"} in progress`;
}

/** Alert text for staff: what to do, not just what is wrong. */
export function watchAlert(watch: TablePaymentWatchRecord): string {
  return watch.risk === "BILL_WAITING"
    ? `Table ${watch.tableNumber} asked for the bill ${minutes(watch.minutesWaiting)} ago. Take it over.`
    : `Table ${watch.tableNumber} was served ${minutes(watch.minutesWaiting)} ago and hasn't paid. Check in with the bill.`;
}

export function isAtRisk(watch: TablePaymentWatchRecord): boolean {
  return watch.risk !== "NONE";
}

export const WALKOUT_REASONS: Array<{ value: WalkoutReason; label: string }> = [
  { value: "WALKED_OUT", label: "Left without paying" },
  { value: "REFUSED_TO_PAY", label: "Refused to pay" },
  { value: "OTHER", label: "Something else" },
];

function minutes(value: number): string {
  if (value < 60) return `${value} min`;
  const h = Math.floor(value / 60);
  const m = value % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}
