import { apiClient } from './client';

export type DeliveryZone = {
  id: string;
  name: string;
  description?: string | null;
  fee: string;
  minOrderAmount: string;
  estimatedMinutes: number;
};

export type DeliveryTracking = {
  orderId: string;
  status: string;
  deliveryAddress?: string | null;
  deliveryFee: string;
  estimatedDeliveryTime?: string | null;
  riderName?: string | null;
  riderPhone?: string | null;
  riderLatitude?: number | null;
  riderLongitude?: number | null;
  riderLocationAt?: string | null;
  deliveredAt?: string | null;
};

export async function fetchDeliveryZones(): Promise<DeliveryZone[]> {
  const { data } = await apiClient.get<DeliveryZone[]>('/delivery/zones');
  return data;
}

export async function fetchDeliveryTracking(orderId: string): Promise<DeliveryTracking> {
  const { data } = await apiClient.get<DeliveryTracking>(`/delivery/orders/${orderId}/tracking`);
  return data;
}

export function mapUrl(latitude: number, longitude: number): string {
  return `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;
}
