import { apiClient } from './client';

export type RiderDelivery = {
  orderId: string;
  status: string;
  deliveryAddress?: string | null;
  total: string;
  deliveryFee: string;
  estimatedDeliveryTime?: string | null;
  riderName?: string | null;
  riderLatitude?: number | null;
  riderLongitude?: number | null;
  riderLocationAt?: string | null;
  deliveredAt?: string | null;
  customerPhone?: string | null;
  notes?: string | null;
  createdAt: string;
};

export async function fetchRiderQueue(): Promise<RiderDelivery[]> {
  const { data } = await apiClient.get<RiderDelivery[]>('/delivery/rider/orders');
  return data;
}

export async function markPickedUp(orderId: string): Promise<RiderDelivery> {
  const { data } = await apiClient.post<RiderDelivery>(`/delivery/orders/${orderId}/pickup`);
  return data;
}

export async function markDelivered(orderId: string): Promise<RiderDelivery> {
  const { data } = await apiClient.post<RiderDelivery>(`/delivery/orders/${orderId}/delivered`);
  return data;
}

export async function sendLocation(orderId: string, latitude: number, longitude: number): Promise<void> {
  await apiClient.post(`/delivery/orders/${orderId}/location`, { latitude, longitude });
}

/** Opens turn-by-turn navigation in Google Maps (falls back to the browser). */
export function navigationUrl(destination: string): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(destination)}&travelmode=driving`;
}
