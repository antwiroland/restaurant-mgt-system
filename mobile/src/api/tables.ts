import { apiClient } from './client';
import type { TableScanResult } from '../types/api';

export async function scanQrToken(qrToken: string): Promise<TableScanResult> {
  const { data } = await apiClient.post<TableScanResult>('/tables/scan', { qrToken });
  return data;
}
