import { apiClient } from './client';
import type { LoyaltyBalance, LoyaltyTransaction, PromoValidation } from '../types/api';

type CurrentUserResponse = {
  id: string;
};

type LoyaltyBalanceResponse = {
  points: number;
};

async function currentCustomerId(): Promise<string> {
  const { data } = await apiClient.get<CurrentUserResponse>('/users/me');
  return data.id;
}

export async function fetchLoyaltyBalance(): Promise<LoyaltyBalance> {
  const customerId = await currentCustomerId();
  const { data } = await apiClient.get<LoyaltyBalanceResponse>(`/phase8/loyalty/${customerId}/balance`);
  return { customerId, points: data.points };
}

export async function fetchLoyaltyTransactions(): Promise<LoyaltyTransaction[]> {
  const customerId = await currentCustomerId();
  const { data } = await apiClient.get<LoyaltyTransaction[]>(`/phase8/loyalty/${customerId}/history`);
  return data;
}

export async function validatePromoCode(code: string, subtotal: number): Promise<PromoValidation> {
  const normalizedCode = code.trim().toUpperCase();
  if (!normalizedCode) {
    throw new Error('Promo code is required');
  }
  const { data } = await apiClient.get<PromoValidation>(`/phase8/promo-codes/validate/${normalizedCode}`, {
    params: { subtotal: subtotal.toFixed(2) },
  });
  return data;
}
