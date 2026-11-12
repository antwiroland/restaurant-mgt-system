import type { OrderPublicStatusView } from "@/lib/apiClient";

export type PickupStatus = OrderPublicStatusView["status"];

/**
 * Board columns, in the kitchen's order (same flow as the KDS), with customer-facing wording. Pickup is paid before
 * it reaches the kitchen, so the board starts at "In queue" (paid, not started).
 */
export const PICKUP_COLUMNS: Array<{ status: PickupStatus; label: string; empty: string }> = [
  { status: "CONFIRMED", label: "In queue", empty: "Nothing waiting" },
  { status: "PREPARING", label: "Preparing", empty: "Nothing cooking" },
  { status: "READY", label: "Ready to collect", empty: "Nothing ready yet" },
];

/**
 * Groups pickup orders by status. Each column lists order numbers only, oldest first, except Ready, which puts
 * the most recently ready on top so the newest call stands out.
 */
export function groupPickupBoard(orders: OrderPublicStatusView[]): Record<PickupStatus, string[]> {
  const groups: Record<PickupStatus, OrderPublicStatusView[]> = { CONFIRMED: [], PREPARING: [], READY: [] };
  for (const order of orders) {
    groups[order.status]?.push(order);
  }
  const byTime = (a: OrderPublicStatusView, b: OrderPublicStatusView) => a.updatedAt.localeCompare(b.updatedAt);
  return {
    CONFIRMED: groups.CONFIRMED.sort(byTime).map((o) => o.orderNumber),
    PREPARING: groups.PREPARING.sort(byTime).map((o) => o.orderNumber),
    READY: groups.READY.sort((a, b) => byTime(b, a)).map((o) => o.orderNumber),
  };
}
