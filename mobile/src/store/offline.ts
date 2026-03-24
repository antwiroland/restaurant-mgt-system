import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { CreateOrderRequest } from '../types/api';

const QUEUE_KEY = 'offline_queue';

export type QueueEntry = {
  id: string;
  action: 'CREATE_ORDER' | 'CREATE_RESERVATION';
  payload: CreateOrderRequest | Record<string, unknown>;
  createdAt: string;
  retryCount: number;
  status: 'QUEUED' | 'SYNCED' | 'FAILED';
};

type OfflineState = {
  queue: QueueEntry[];
  isOnline: boolean;
  setOnline: (online: boolean) => void;
  enqueue: (action: QueueEntry['action'], payload: QueueEntry['payload']) => Promise<void>;
  markSynced: (id: string) => Promise<void>;
  markFailed: (id: string) => Promise<void>;
  loadQueue: () => Promise<void>;
  pendingCount: () => number;
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export const useOfflineStore = create<OfflineState>((set, get) => ({
  queue: [],
  isOnline: true,

  setOnline: (online) => set({ isOnline: online }),

  enqueue: async (action, payload) => {
    const entry: QueueEntry = {
      id: uuid(),
      action,
      payload,
      createdAt: new Date().toISOString(),
      retryCount: 0,
      status: 'QUEUED',
    };
    const next = [...get().queue, entry];
    set({ queue: next });
    await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(next));
  },

  markSynced: async (id) => {
    const next = get().queue.map((e) => (e.id === id ? { ...e, status: 'SYNCED' as const } : e));
    set({ queue: next });
    await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(next));
  },

  markFailed: async (id) => {
    const next = get().queue.map((e) =>
      e.id === id ? { ...e, status: 'FAILED' as const, retryCount: e.retryCount + 1 } : e
    );
    set({ queue: next });
    await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(next));
  },

  loadQueue: async () => {
    const raw = await AsyncStorage.getItem(QUEUE_KEY);
    if (raw) set({ queue: JSON.parse(raw) });
  },

  pendingCount: () => get().queue.filter((e) => e.status === 'QUEUED').length,
}));
