import { useEffect } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { useOfflineStore } from '../store/offline';
import { createOrder, createPublicTableOrder } from '../api/orders';
import { scanQrToken } from '../api/tables';
import type { CreateOrderRequest } from '../types/api';
import type { QueueEntry } from '../store/offline';

export function useConnectivity() {
  const { setOnline, queue, markSynced, markFailed, markConflict, isOnline } = useOfflineStore();

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      const online = !!(state.isConnected && state.isInternetReachable);
      setOnline(online);
      if (online) {
        syncQueue();
      }
    });
    return () => unsubscribe();
  }, [queue]);

  useEffect(() => {
    if (isOnline && queue.some((entry) => entry.status === 'QUEUED')) {
      syncQueue();
    }
  }, [isOnline, queue]);

  async function syncQueue() {
    const pending = queue.filter((e) => e.status === 'QUEUED');
    for (const entry of pending) {
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
  }

  return { isOnline };
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
