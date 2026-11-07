import type { OrderRecord } from "@/lib/apiClient";

/** Customer-facing wording for an order's status. */
export function orderStatusLabel(order: Pick<OrderRecord, "status" | "type">): string {
  switch (order.status) {
    case "PENDING": return "Awaiting payment";
    case "CONFIRMED": return "Confirmed";
    case "PREPARING": return "Cooking";
    case "READY": return order.type === "PICKUP" ? "Ready for pickup" : order.type === "DELIVERY" ? "Waiting for rider" : "Ready";
    case "OUT_FOR_DELIVERY": return "On the way";
    case "DELIVERED": return "Delivered";
    case "COMPLETED": return order.type === "PICKUP" ? "Collected" : "Completed";
    case "CANCELLED": return "Cancelled";
    case "VOIDED": return "Voided";
    default: return order.status;
  }
}

/** Progress steps for the tracking page. */
export function orderSteps(type: OrderRecord["type"]): { status: OrderRecord["status"]; label: string }[] {
  const base: { status: OrderRecord["status"]; label: string }[] = [
    { status: "PENDING", label: "Placed" },
    { status: "CONFIRMED", label: "Confirmed" },
    { status: "PREPARING", label: "Cooking" },
    { status: "READY", label: type === "PICKUP" ? "Ready for pickup" : "Ready" },
  ];
  if (type === "DELIVERY") {
    base.push({ status: "OUT_FOR_DELIVERY", label: "On the way" }, { status: "DELIVERED", label: "Delivered" });
  } else if (type === "PICKUP") {
    base.push({ status: "COMPLETED", label: "Collected" });
  }
  return base;
}
