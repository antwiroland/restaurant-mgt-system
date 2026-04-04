import { useEffect, useRef } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { useOfflineStore, MAX_RETRY_COUNT } from '../store/offline';
import { createOrder, createPublicTableOrder } from '../api/orders';
import { createReservation } from '../api/reservation';
import { scanQrToken } from '../api/tables';
import type { CreateOrderRequest, CreateReservationRequest } from '../types/api';
import type { QueueEntry } from '../store/offline';

export function useConnectivity() {
  const { setOnline, queue, isOnline } = useOfflineStore();
  const syncInFlightRef = useRef(false);
  const lastAttemptedSignatureRef = useRef<string | null>(null);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      const online = !!(state.isConnected && state.isInternetReachable);
      setOnline(online);
      if (online) {
        void syncQueue();
      }
    });
    return () => unsubscribe();
  }, [setOnline]);

  useEffect(() => {
    if (!isOnline) {
      lastAttemptedSignatureRef.current = null;
      return;
    }

    const signature = queueSignature(queue);
    if (!signature || signature === lastAttemptedSignatureRef.current) {
      return;
    }

    lastAttemptedSignatureRef.current = signature;
    void syncQueue(signature);
  }, [isOnline, queue]);

  async function syncQueue(expectedSignature?: string) {
    if (syncInFlightRef.current) {
      return;
    }

    const state = useOfflineStore.getState();
    const eligible = state.queue.filter(
      (e) => e.status === 'QUEUED' || (e.status === 'FAILED' && e.retryCount < MAX_RETRY_COUNT)
    );
    const currentSignature = queueSignature(state.queue);

    if (!eligible.length || (expectedSignature && currentSignature !== expectedSignature)) {
      return;
    }

    syncInFlightRef.current = true;

    const { markSynced, markFailed, markConflict, cleanupSynced } = state;

    try {
      for (const entry of eligible) {
        try {
          if (entry.action === 'CREATE_ORDER') {
            const orderPayload = entry.payload as CreateOrderRequest;

            if (orderPayload.type === 'DINE_IN') {
              const conflictReason = await resolveTableConflict(entry);
              if (conflictReason) {
                await markConflict(entry.id, conflictReason);
                continue;
              }
            }

            await createOrder(orderPayload);
          } else if (entry.action === 'CREATE_PUBLIC_TABLE_ORDER') {
            const conflictReason = await resolveTableConflict(entry);
            if (conflictReason) {
              await markConflict(entry.id, conflictReason);
              continue;
            }
            await createPublicTableOrder(entry.payload as { tableToken: string; items: CreateOrderRequest['items']; notes?: string });
          } else if (entry.action === 'CREATE_RESERVATION') {
            await createReservation(entry.payload as CreateReservationRequest);
          }

          await markSynced(entry.id);
        } catch (error) {
          if (isOrderConflictError(error)) {
            await markConflict(entry.id, extractConflictReason(error));
            continue;
          }
          await markFailed(entry.id);
        }
      }

      await cleanupSynced();
    } finally {
      syncInFlightRef.current = false;
    }
  }

  return { isOnline };
}

function queueSignature(queue: QueueEntry[]): string | null {
  const eligible = queue
    .filter((entry) => entry.status === 'QUEUED' || (entry.status === 'FAILED' && entry.retryCount < MAX_RETRY_COUNT))
    .map((entry) => `${entry.id}:${entry.status}:${entry.retryCount}`);

  return eligible.length ? eligible.join('|') : null;
}

async function resolveTableConflict(entry: QueueEntry): Promise<string | null> {
  if (!entry.meta?.tableToken) {
    return 'Missing table token for queued dine-in order';
  }

  try {
    const table = await scanQrToken(entry.meta.tableToken);
    if (entry.meta.expectedTableId && table.tableId !== entry.meta.expectedTableId) {
      return 'Table identity changed while offline';
    }

    const currentStatus = String(table.status).toUpperCase();
    if (currentStatus === 'CLOSED' || currentStatus === 'RESERVED') {
      return `Table ${table.tableNumber} is ${currentStatus} and cannot accept queued dine-in orders`;
    }

    return null;
  } catch {
    return 'Could not re-validate table state before sync';
  }
}

function isOrderConflictError(error: unknown): boolean {
  const status = (error as any)?.response?.status;
  if (![400, 404, 409].includes(status)) {
    return false;
  }
  const message = String((error as any)?.response?.data?.message ?? '').toLowerCase();
  return message.includes('table') || message.includes('token') || message.includes('dine-in');
}

function extractConflictReason(error: unknown): string {
  const message = (error as any)?.response?.data?.message;
  if (typeof message === 'string' && message.trim()) {
    return message.trim();
  }
  return 'Queued order conflicts with current table state';
}
