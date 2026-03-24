import { apiClient } from './client';
import type { Payment, PaymentInitiateResponse, PaymentMethod } from '../types/api';

function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export async function initiatePayment(orderId: string, method: PaymentMethod, momoPhone?: string): Promise<PaymentInitiateResponse> {
  const { data } = await apiClient.post<PaymentInitiateResponse>('/payments/initiate', {
    orderId,
    method,
    momoPhone,
    idempotencyKey: generateUUID(),
  });
  return data;
}

export async function fetchPayment(id: string): Promise<Payment> {
  const { data } = await apiClient.get<Payment>(`/payments/${id}`);
  return data;
}

export async function retryPayment(id: string, momoPhone: string): Promise<PaymentInitiateResponse> {
  const { data } = await apiClient.post<PaymentInitiateResponse>(`/payments/${id}/retry`, {
    momoPhone,
    idempotencyKey: generateUUID(),
  });
  return data;
}
