import { useEffect } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { useOfflineStore } from '../store/offline';
import { createOrder } from '../api/orders';
import { scanQrToken } from '../api/tables';
import type { CreateOrderRequest } from '../types/api';

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

  async function syncQueue() {
    const pending = queue.filter((e) => e.status === 'QUEUED');
    for (const entry of pending) {
      try {
        if (entry.action === 'CREATE_ORDER') {
          const orderPayload = entry.payload as CreateOrderRequest;

          // Conflict strategy: re-validate table state before replaying queued dine-in orders.
          if (orderPayload.type === 'DINE_IN' && entry.meta?.tableToken) {
            try {
              const table = await scanQrToken(entry.meta.tableToken);
              if (entry.meta.expectedTableStatus && table.status !== entry.meta.expectedTableStatus) {
                await markConflict(
                  entry.id,
                  `Table ${table.tableNumber} changed from ${entry.meta.expectedTableStatus} to ${table.status} while offline`
                );
                continue;
              }
              if (entry.meta.expectedTableId && table.tableId !== entry.meta.expectedTableId) {
                await markConflict(entry.id, 'Table identity mismatch while offline');
                continue;
              }
            } catch {
              await markConflict(entry.id, 'Could not re-validate table state before sync');
              continue;
            }
          }

          await createOrder(orderPayload);
        }
        await markSynced(entry.id);
      } catch {
        await markFailed(entry.id);
      }
    }
  }

  return { isOnline };
}
