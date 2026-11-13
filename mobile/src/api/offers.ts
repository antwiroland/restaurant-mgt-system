import { apiClient } from './client';

export type PublicOffer = {
  id: string;
  name: string;
  description?: string | null;
  offerType: 'BUY_X_GET_Y' | 'BUY_X_GET_OTHER' | 'PERCENT_OFF' | 'COMBO';
  /** e.g. "Buy 2 Kelewele, get 1 free". */
  summary: string;
  /** e.g. "Mon, Fri · 16:00–18:00"; null = all day, every day. */
  schedule?: string | null;
  /** False outside its days or hours. */
  runningNow: boolean;
  menuItemIds: string[];
  branchName?: string | null;
};

export async function fetchPublicOffers(): Promise<PublicOffer[]> {
  const { data } = await apiClient.get<PublicOffer[]>('/promotions/offers/public');
  return data;
}

/** The offers that involve a dish, running ones first. */
export function offersFor(menuItemId: string, offers: PublicOffer[]): PublicOffer[] {
  return offers
    .filter((offer) => offer.menuItemIds.includes(menuItemId))
    .sort((a, b) => Number(b.runningNow) - Number(a.runningNow));
}
