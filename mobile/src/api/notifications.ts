import { apiClient } from './client';

export async function registerPushDevice(token: string, platform: string): Promise<void> {
  await apiClient.post('/notifications/devices', { token, platform });
}

export async function unregisterPushDevice(token: string): Promise<void> {
  await apiClient.delete(`/notifications/devices/${encodeURIComponent(token)}`);
}
