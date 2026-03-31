import { useOfflineStore, MAX_RETRY_COUNT } from '../../store/offline';
import type { QueueEntry } from '../../store/offline';

export { MAX_RETRY_COUNT };

export function openMenuOffline(cachedItems: number): { shownCachedMenu: boolean; staleWarning: boolean } {
  return { shownCachedMenu: cachedItems > 0, staleWarning: true };
}

export async function queueOrderWhenOffline(
  action: QueueEntry['action'],
  payload: QueueEntry['payload'],
  meta?: QueueEntry['meta'],
): Promise<void> {
  await useOfflineStore.getState().enqueue(action, payload, meta);
}

export function canAttemptPaymentOffline(isOnline: boolean): { allowed: boolean; message?: string } {
  if (isOnline) return { allowed: true };
  return { allowed: false, message: 'Internet required for payment' };
}

export function shouldRetryEntry(entry: Pick<QueueEntry, 'status' | 'retryCount'>): boolean {
  return entry.status === 'FAILED' && entry.retryCount < MAX_RETRY_COUNT;
}
