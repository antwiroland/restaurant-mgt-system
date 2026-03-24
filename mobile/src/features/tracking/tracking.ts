export type OrderTrack = { id: string; status: string; items: string[] };

export function applyStatusUpdate(order: OrderTrack, nextStatus: string): OrderTrack {
  return { ...order, status: nextStatus };
}

export function appearsInHistory(history: OrderTrack[], orderId: string): boolean {
  return history.some((h) => h.id === orderId);
}

export function reorderFromHistory(order: OrderTrack): { prepopulated: string[] } {
  return { prepopulated: [...order.items] };
}
