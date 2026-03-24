export type OrderSummary = { id: string; status: string };

export function applyNewOrder(current: OrderSummary[], incoming: OrderSummary): OrderSummary[] {
  return [incoming, ...current];
}

export function applyOrderStatus(current: OrderSummary[], orderId: string, status: string): OrderSummary[] {
  return current.map((order) => (order.id === orderId ? { ...order, status } : order));
}

export function shouldShowCancelPinModal(confirming: boolean): boolean {
  return confirming;
}
