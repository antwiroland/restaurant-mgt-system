import { apiClient } from './client';
import type { GroupSession, GroupSessionDetail, CreateOrderRequest, Order } from '../types/api';

export async function createGroupSession(displayName: string): Promise<GroupSession> {
  const { data } = await apiClient.post<GroupSession>('/orders/group/sessions', { displayName });
  return data;
}

export async function joinGroupSession(code: string, displayName: string) {
  const { data } = await apiClient.post(`/orders/group/sessions/${code}/join`, { displayName });
  return data;
}

export async function fetchGroupSession(code: string): Promise<GroupSessionDetail> {
  const { data } = await apiClient.get<GroupSessionDetail>(`/orders/group/sessions/${code}`);
  return data;
}

export async function addItemsToGroup(code: string, participantId: string, items: { menuItemId: string; quantity: number }[]) {
  const { data } = await apiClient.post(`/orders/group/sessions/${code}/items`, { participantId, items });
  return data;
}

export async function finalizeGroupSession(code: string, req: Pick<CreateOrderRequest, 'type' | 'tableId'>): Promise<Order> {
  const { data } = await apiClient.post<Order>(`/orders/group/sessions/${code}/finalize`, req);
  return data;
}
