export type QueuedOrder = { id: string; submitted: boolean; status: "QUEUED" | "SYNCED" };

export function openMenuOffline(cachedItems: number): { shownCachedMenu: boolean; staleWarning: boolean } {
  return { shownCachedMenu: cachedItems > 0, staleWarning: true };
}

export function queueOrderWhenOffline(orderId: string): QueuedOrder {
  return { id: orderId, submitted: false, status: "QUEUED" };
}

export function syncQueuedOrderOnReconnect(order: QueuedOrder): QueuedOrder {
  return { ...order, submitted: true, status: "SYNCED" };
}

export function canAttemptPaymentOffline(isOnline: boolean): { allowed: boolean; message?: string } {
  if (isOnline) return { allowed: true };
  return { allowed: false, message: "Internet required" };
}
