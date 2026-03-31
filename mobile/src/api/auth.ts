import { apiClient } from './client';
import type { AuthResponse, LoginResponse } from '../types/api';
import AsyncStorage from '@react-native-async-storage/async-storage';

const SESSION_KEYS = ['accessToken', 'refreshToken', 'userId', 'userName', 'userRole'] as const;

export async function registerCustomer(name: string, phone: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/auth/register', { name, phone, password });
  await AsyncStorage.setItem('accessToken', data.accessToken);
  await AsyncStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

export async function loginCustomer(phone: string, password: string): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/login', { phone, password });
  await AsyncStorage.setItem('accessToken', data.accessToken);
  await AsyncStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

export async function logoutCustomer(): Promise<void> {
  const refreshToken = await AsyncStorage.getItem('refreshToken');
  if (refreshToken) {
    await apiClient.post('/auth/logout', { refreshToken }).catch(() => {});
  }
  await AsyncStorage.multiRemove([...SESSION_KEYS]);
}
