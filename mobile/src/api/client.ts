import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';

const env = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env;

export function normalizeBaseUrl(url: string): string {
  return url
    .replace(/\/+$/, '')
    .replace(/\/api\/v1$/, '');
}

export const BASE_URL = normalizeBaseUrl(env?.EXPO_PUBLIC_API_BASE_URL ?? 'http://10.0.2.2:8080');

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

apiClient.interceptors.request.use(async (config) => {
  const token = await AsyncStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config as (typeof error.config & { _retry?: boolean }) | undefined;
    if (error.response?.status === 401 && original && !original._retry) {
      original._retry = true;
      try {
        const refreshToken = await AsyncStorage.getItem('refreshToken');
        if (!refreshToken) throw error;
        const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
        await AsyncStorage.setItem('accessToken', data.accessToken);
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${data.accessToken}`;
        return apiClient(original);
      } catch {
        await AsyncStorage.multiRemove(['accessToken', 'refreshToken']);
      }
    }
    return Promise.reject(error);
  }
);
