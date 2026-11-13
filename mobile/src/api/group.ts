import { apiClient } from './client';
import type { GroupSession, GroupSessionDetail, CreateOrderRequest, Order } from '../types/api';

type GroupCartItemResponse = {
  itemId: string;
  menuItemId: string;
  name: string;
  price: string;
  quantity: number;
  notes?: string;
  modifiers?: { id: string; groupName: string; optionName: string; priceDelta: string }[];
};

type GroupParticipantResponse = {
  participantId: string;
  userId?: string | null;
  displayName: string;
  subtotal: string;
  items: GroupCartItemResponse[];
};

type GroupSessionDetailResponse = {
  sessionId: string;
  sessionCode: string;
  status: GroupSession['status'];
  groupTotal: string;
  participants: GroupParticipantResponse[];
  orderId?: string | null;
  hostUserId?: string | null;
};

export async function createGroupSession(displayName: string): Promise<GroupSession> {
  const { data } = await apiClient.post<GroupSession>('/orders/group/sessions', { displayName });
  return data;
}

export async function joinGroupSession(code: string, displayName: string) {
  const { data } = await apiClient.post(`/orders/group/sessions/${code}/join`, { displayName });
  return data;
}

function toDetail(data: GroupSessionDetailResponse): GroupSessionDetail {
  return {
    sessionCode: data.sessionCode,
    status: data.status,
    groupTotal: String(data.groupTotal),
    orderId: data.orderId ?? null,
    hostUserId: data.hostUserId ?? null,
    participants: data.participants.map((participant) => ({
      participantId: participant.participantId,
      userId: participant.userId ?? null,
      displayName: participant.displayName,
      subtotal: String(participant.subtotal),
      items: participant.items.map((item) => ({
        id: item.itemId,
        menuItemId: item.menuItemId,
        menuItemName: item.name,
        quantity: item.quantity,
        unitPrice: String(item.price),
        totalPrice: (parseFloat(String(item.price)) * item.quantity).toFixed(2),
        notes: item.notes,
        modifiers: (item.modifiers ?? []).map((m) => ({ ...m, priceDelta: String(m.priceDelta) })),
      })),
    })),
  };
}

export async function fetchGroupSession(code: string): Promise<GroupSessionDetail> {
  const { data } = await apiClient.get<GroupSessionDetailResponse>(`/orders/group/sessions/${code}`);
  return toDetail(data);
}

export async function addItemsToGroup(
  code: string,
  participantId: string,
  items: { menuItemId: string; quantity: number; notes?: string; modifierOptionIds?: string[] }[],
): Promise<GroupSessionDetail> {
  const { data } = await apiClient.post<GroupSessionDetailResponse>(`/orders/group/sessions/${code}/items`, { participantId, items });
  return toDetail(data);
}

export async function updateGroupItem(code: string, itemId: string, quantity: number, notes?: string): Promise<GroupSessionDetail> {
  const { data } = await apiClient.patch<GroupSessionDetailResponse>(`/orders/group/sessions/${code}/items/${itemId}`, { quantity, notes });
  return toDetail(data);
}

export async function removeGroupItem(code: string, itemId: string): Promise<GroupSessionDetail> {
  const { data } = await apiClient.delete<GroupSessionDetailResponse>(`/orders/group/sessions/${code}/items/${itemId}`);
  return toDetail(data);
}

export async function leaveGroupSession(code: string): Promise<void> {
  await apiClient.post(`/orders/group/sessions/${code}/leave`);
}

export async function cancelGroupSession(code: string): Promise<GroupSessionDetail> {
  const { data } = await apiClient.post<GroupSessionDetailResponse>(`/orders/group/sessions/${code}/cancel`);
  return toDetail(data);
}

export async function finalizeGroupSession(code: string, req: Pick<CreateOrderRequest, 'type' | 'tableId'>): Promise<Order> {
  const { data } = await apiClient.post<Order>(`/orders/group/sessions/${code}/finalize`, req);
  return data;
}
