import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';

type AuthState = {
  userId: string | null;
  name: string | null;
  role: string | null;
  isAuthenticated: boolean;
  isGuest: boolean;
  setUser: (id: string, name: string, role: string) => void;
  setGuest: () => void;
  clear: () => void;
  restore: () => Promise<void>;
};

export const useAuthStore = create<AuthState>((set) => ({
  userId: null,
  name: null,
  role: null,
  isAuthenticated: false,
  isGuest: false,

  setUser: (id, name, role) =>
    set({ userId: id, name, role, isAuthenticated: true, isGuest: false }),

  setGuest: () =>
    set({ userId: null, name: 'Guest', role: null, isAuthenticated: false, isGuest: true }),

  clear: () =>
    set({ userId: null, name: null, role: null, isAuthenticated: false, isGuest: false }),

  restore: async () => {
    const token = await AsyncStorage.getItem('accessToken');
    const name = await AsyncStorage.getItem('userName');
    const id = await AsyncStorage.getItem('userId');
    const role = await AsyncStorage.getItem('userRole');
    if (token && id && name && role) {
      set({ userId: id, name, role, isAuthenticated: true, isGuest: false });
    }
  },
}));
