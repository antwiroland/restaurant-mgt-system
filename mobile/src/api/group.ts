import { apiClient } from './client';
import type { GroupSession, GroupSessionDetail, CreateOrderRequest, Order } from '../types/api';

type GroupCartItemResponse = {
  itemId: string;
  menuItemId: string;
  name: string;
  price: string;
  quantity: number;
  notes?: string;
};

type GroupParticipantResponse = {
  participantId: string;
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
};

export async function createGroupSession(displayName: string): Promise<GroupSession> {
  const { data } = await apiClient.post<GroupSession>('/orders/group/sessions', { displayName });
  return data;
}

export async function joinGroupSession(code: string, displayName: string) {
  const { data } = await apiClient.post(`/orders/group/sessions/${code}/join`, { displayName });
  return data;
}

export async function fetchGroupSession(code: string): Promise<GroupSessionDetail> {
  const { data } = await apiClient.get<GroupSessionDetailResponse>(`/orders/group/sessions/${code}`);
  return {
    sessionCode: data.sessionCode,
    status: data.status,
    groupTotal: String(data.groupTotal),
    participants: data.participants.map((participant) => ({
      participantId: participant.participantId,
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
      })),
    })),
  };
}

export async function addItemsToGroup(code: string, participantId: string, items: { menuItemId: string; quantity: number }[]) {
  const { data } = await apiClient.post(`/orders/group/sessions/${code}/items`, { participantId, items });
  return data;
}

export async function finalizeGroupSession(code: string, req: Pick<CreateOrderRequest, 'type' | 'tableId'>): Promise<Order> {
  const { data } = await apiClient.post<Order>(`/orders/group/sessions/${code}/finalize`, req);
  return data;
}
