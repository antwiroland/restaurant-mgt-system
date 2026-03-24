import { useEffect } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { useOfflineStore } from '../store/offline';
import { createOrder } from '../api/orders';
import type { CreateOrderRequest } from '../types/api';

export function useConnectivity() {
  const { setOnline, queue, markSynced, markFailed, isOnline } = useOfflineStore();

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
          await createOrder(entry.payload as CreateOrderRequest);
        }
        await markSynced(entry.id);
      } catch {
        await markFailed(entry.id);
      }
    }
  }

  return { isOnline };
}
