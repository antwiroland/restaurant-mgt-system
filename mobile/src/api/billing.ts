import { apiClient } from './client';
import type { BillingConfig, Feedback, FeedbackRequest, PaymentSplit } from '../types/api';

/** Public billing rules so checkout can explain service charge, taxes and tipping. */
export async function fetchBillingConfig(): Promise<BillingConfig> {
  const { data } = await apiClient.get<BillingConfig>('/billing/settings');
  return data;
}

export async function fetchSplits(orderId: string): Promise<PaymentSplit[]> {
  const { data } = await apiClient.get<PaymentSplit[]>(`/orders/${orderId}/splits`);
  return data;
}

/** Splits a group order so each participant pays for their own items (plus a fair part of fees and taxes). */
export async function splitByItems(orderId: string): Promise<PaymentSplit[]> {
  const { data } = await apiClient.post<PaymentSplit[]>(`/orders/${orderId}/splits`, { mode: 'BY_ITEMS' });
  return data;
}

export async function splitEqually(orderId: string, parts: number): Promise<PaymentSplit[]> {
  const { data } = await apiClient.post<PaymentSplit[]>(`/orders/${orderId}/splits`, { mode: 'EQUAL', parts });
  return data;
}

export async function fetchOrderFeedback(orderId: string): Promise<Feedback | null> {
  try {
    const { data } = await apiClient.get<Feedback | ''>(`/orders/${orderId}/feedback`);
    return data || null;
  } catch {
    return null;
  }
}

export async function submitFeedback(orderId: string, req: FeedbackRequest): Promise<Feedback> {
  const { data } = await apiClient.post<Feedback>(`/orders/${orderId}/feedback`, req);
  return data;
}

export async function submitGuestFeedback(tableToken: string, orderId: string, req: FeedbackRequest): Promise<Feedback> {
  const { data } = await apiClient.post<Feedback>(`/orders/public/dine-in/tables/${tableToken}/orders/${orderId}/feedback`, req);
  return data;
}
