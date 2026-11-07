import AsyncStorage from '@react-native-async-storage/async-storage';
import { apiClient } from './client';
import type { TableScanResult } from '../types/api';

const SESSION_KEY = 'tableSessionId';

export async function scanQrToken(qrToken: string): Promise<TableScanResult> {
  const { data } = await apiClient.post<TableScanResult>('/tables/scan', { qrToken });
  return data;
}

async function deviceSessionId(): Promise<string> {
  const existing = await AsyncStorage.getItem(SESSION_KEY);
  if (existing) return existing;
  const created = `app-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  await AsyncStorage.setItem(SESSION_KEY, created);
  return created;
}

/** Registers this device as ordering at the scanned table so staff can see active guest sessions. */
export async function linkTableSession(tableToken: string): Promise<void> {
  const sessionId = await deviceSessionId();
  await apiClient.post('/qr/sessions/link', { tableToken, sessionId });
}
