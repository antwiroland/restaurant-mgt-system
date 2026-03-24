import { apiClient } from './client';
import type { LoyaltyBalance, LoyaltyTransaction, PromoValidation } from '../types/api';

export async function fetchLoyaltyBalance(): Promise<LoyaltyBalance> {
  const { data } = await apiClient.get<LoyaltyBalance>('/loyalty/balance');
  return data;
}

export async function fetchLoyaltyTransactions(): Promise<LoyaltyTransaction[]> {
  const { data } = await apiClient.get('/loyalty/transactions');
  return data.content as LoyaltyTransaction[];
}

export async function validatePromoCode(code: string): Promise<PromoValidation> {
  const { data } = await apiClient.get<PromoValidation>(`/promo-codes/validate/${code}`);
  return data;
}
