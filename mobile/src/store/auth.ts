import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { fetchCurrentUser } from '../api/user';
import { logoutCustomer } from '../api/auth';

type AuthState = {
  userId: string | null;
  name: string | null;
  phone: string | null;
  email: string | null;
  role: string | null;
  isAuthenticated: boolean;
  isGuest: boolean;
  setUser: (id: string, name: string, role: string, extras?: { phone?: string | null; email?: string | null }) => void;
  setGuest: () => void;
  clear: () => void;
  restore: () => Promise<void>;
};

export const useAuthStore = create<AuthState>((set) => ({
  userId: null,
  name: null,
  phone: null,
  email: null,
  role: null,
  isAuthenticated: false,
  isGuest: false,

  setUser: (id, name, role, extras) =>
    set({
      userId: id,
      name,
      phone: extras?.phone ?? null,
      email: extras?.email ?? null,
      role,
      isAuthenticated: true,
      isGuest: false,
    }),

  setGuest: () =>
    set({ userId: null, name: 'Guest', phone: null, email: null, role: null, isAuthenticated: false, isGuest: true }),

  clear: () =>
    set({ userId: null, name: null, phone: null, email: null, role: null, isAuthenticated: false, isGuest: false }),

  restore: async () => {
    const accessToken = await AsyncStorage.getItem('accessToken');
    const refreshToken = await AsyncStorage.getItem('refreshToken');
    if (!accessToken && !refreshToken) {
      return;
    }

    try {
      const currentUser = await fetchCurrentUser();
      await AsyncStorage.setItem('userId', currentUser.id);
      await AsyncStorage.setItem('userName', currentUser.name);
      await AsyncStorage.setItem('userRole', currentUser.role);
      set({
        userId: currentUser.id,
        name: currentUser.name,
        phone: currentUser.phone,
        email: currentUser.email ?? null,
        role: currentUser.role,
        isAuthenticated: true,
        isGuest: false,
      });
    } catch {
      await logoutCustomer().catch(() => {});
      set({ userId: null, name: null, phone: null, email: null, role: null, isAuthenticated: false, isGuest: false });
    }
  },
}));
