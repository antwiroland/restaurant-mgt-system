import type { OrderPublicStatusView } from "@/lib/apiClient";

export type PickupStatus = OrderPublicStatusView["status"];
export type BoardEntry = { number: string; type: OrderPublicStatusView["type"] };

/**
 * Board columns, in the kitchen's order (same flow as the KDS), with customer-facing wording. Pickup is paid before
 * it reaches the kitchen, so the board starts at "In queue" (accepted, not started). A pickup leaves the board when
 * it is collected, a delivery when the rider takes it.
 */
export const PICKUP_COLUMNS: Array<{ status: PickupStatus; label: string; empty: string }> = [
  { status: "CONFIRMED", label: "In queue", empty: "Nothing waiting" },
  { status: "PREPARING", label: "Preparing", empty: "Nothing cooking" },
  { status: "READY", label: "Ready", empty: "Nothing ready yet" },
];

export const TYPE_LABEL: Record<OrderPublicStatusView["type"], string> = {
  PICKUP: "Pickup",
  DELIVERY: "Delivery",
};

/**
 * Groups pickup and delivery orders by status. Each column lists order numbers (with their type), oldest first,
 * except Ready, which puts the most recently ready on top so the newest call stands out.
 */
export function groupPickupBoard(orders: OrderPublicStatusView[]): Record<PickupStatus, BoardEntry[]> {
  const groups: Record<PickupStatus, OrderPublicStatusView[]> = { CONFIRMED: [], PREPARING: [], READY: [] };
  for (const order of orders) {
    groups[order.status]?.push(order);
  }
  const byTime = (a: OrderPublicStatusView, b: OrderPublicStatusView) => a.updatedAt.localeCompare(b.updatedAt);
  const entry = (o: OrderPublicStatusView): BoardEntry => ({ number: o.orderNumber, type: o.type ?? "PICKUP" });
  return {
    CONFIRMED: groups.CONFIRMED.sort(byTime).map(entry),
    PREPARING: groups.PREPARING.sort(byTime).map(entry),
    READY: groups.READY.sort((a, b) => byTime(b, a)).map(entry),
  };
}
