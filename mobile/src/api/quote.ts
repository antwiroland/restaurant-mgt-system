import { apiClient } from './client';

/** What the server will charge for a cart: the same rules as placing the order (offers, then promo, then points). */
export type CartQuote = {
  subtotal: number;
  offerDiscount: number;
  promoCode: string | null;
  promoDiscount: number;
  loyaltyDiscount: number;
  deliveryFee: number;
  serviceCharge: number;
  taxTotal: number;
  taxInclusive: boolean;
  taxLines: { code: string; name: string; amount: number }[];
  tipAmount: number;
  total: number;
  appliedOffers: { offerId: string; name: string; discount: number }[];
  /** Nudges to complete a deal, e.g. "Add 1 more Kelewele: it's free". */
  offerHints: { offerId: string; offerName: string; menuItemId: string; quantity: number; message: string }[];
  /** A promo code was sent but the server did not accept it (the quote is without it). */
  promoRejected: boolean;
};

export type QuoteRequest = {
  type: 'DINE_IN' | 'PICKUP' | 'DELIVERY';
  tableId?: string;
  tableToken?: string;
  items: { menuItemId: string; quantity: number; notes?: string; modifierOptionIds?: string[] }[];
  promoCode?: string;
  loyaltyPointsToRedeem?: number;
  deliveryAddress?: string;
  deliveryZoneId?: string;
  tipAmount?: number;
};

const num = (value: unknown) => Number(value ?? 0);

function toQuote(data: any, promoRejected: boolean): CartQuote {
  return {
    subtotal: num(data.subtotal),
    offerDiscount: num(data.offerDiscount),
    promoCode: data.promoCode ?? null,
    promoDiscount: num(data.promoDiscount),
    loyaltyDiscount: num(data.loyaltyDiscount),
    deliveryFee: num(data.deliveryFee),
    serviceCharge: num(data.serviceCharge),
    taxTotal: num(data.taxTotal),
    taxInclusive: !!data.taxInclusive,
    taxLines: (data.taxLines ?? []).map((tax: any) => ({ code: tax.code, name: tax.name, amount: num(tax.amount) })),
    tipAmount: num(data.tipAmount),
    total: num(data.total),
    appliedOffers: (data.appliedOffers ?? []).map((offer: any) => ({ ...offer, discount: num(offer.discount) })),
    offerHints: data.offerHints ?? [],
    promoRejected,
  };
}

/**
 * Prices a cart on the server. Signed-in customers use /orders/quote (loyalty points count); others the public
 * quote. If the promo code is refused, prices again without it and flags {@code promoRejected}.
 */
export async function quoteCart(request: QuoteRequest, signedInCustomer: boolean): Promise<CartQuote> {
  const path = signedInCustomer ? '/orders/quote' : '/orders/public/quote';
  const body = {
    ...request,
    loyaltyPointsToRedeem: signedInCustomer ? request.loyaltyPointsToRedeem : undefined,
    // The server needs an address to price a delivery; the real one is sent when the order is placed.
    deliveryAddress: request.type === 'DELIVERY' ? request.deliveryAddress || '-' : undefined,
  };
  try {
    const { data } = await apiClient.post(path, body);
    return toQuote(data, false);
  } catch (error) {
    if (!request.promoCode) throw error;
    const { data } = await apiClient.post(path, { ...body, promoCode: undefined });
    return toQuote(data, true);
  }
}
