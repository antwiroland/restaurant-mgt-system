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

export type CartQuote = {
  offerDiscount: number;
  appliedOffers: { offerId: string; name: string; discount: number }[];
  /** Nudges to complete a deal, e.g. "Add 1 more Kelewele: it's free". */
  offerHints: { offerId: string; offerName: string; menuItemId: string; quantity: number; message: string }[];
};

export async function fetchPublicOffers(): Promise<PublicOffer[]> {
  const { data } = await apiClient.get<PublicOffer[]>('/promotions/offers/public');
  return data;
}

/** Offers for a cart (prices checked by the server). Dine-in needs the table; otherwise priced as pickup. */
export async function quoteCartOffers(
  lines: { menuItemId: string; quantity: number; modifierOptionIds?: string[] }[],
  tableToken?: string | null,
): Promise<CartQuote> {
  const { data } = await apiClient.post('/orders/public/quote', {
    type: tableToken ? 'DINE_IN' : 'PICKUP',
    tableToken: tableToken ?? undefined,
    items: lines,
  });
  return {
    offerDiscount: Number(data.offerDiscount ?? 0),
    appliedOffers: (data.appliedOffers ?? []).map((offer: any) => ({ ...offer, discount: Number(offer.discount) })),
    offerHints: data.offerHints ?? [],
  };
}

/** The offers that involve a dish, running ones first. */
export function offersFor(menuItemId: string, offers: PublicOffer[]): PublicOffer[] {
  return offers
    .filter((offer) => offer.menuItemIds.includes(menuItemId))
    .sort((a, b) => Number(b.runningNow) - Number(a.runningNow));
}
