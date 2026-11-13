import { beforeEach, describe, expect, test, vi } from 'vitest';

const post = vi.hoisted(() => vi.fn());
vi.mock('./client', () => ({ apiClient: { post } }));

import { quoteCart } from './quote';

const serverQuote = {
  subtotal: '135.00', offerDiscount: '45.00', promoCode: 'WELCOME10', promoDiscount: '9.00', loyaltyDiscount: '0',
  deliveryFee: '0', serviceCharge: '0', taxTotal: '0', taxInclusive: false, taxLines: [], tipAmount: '0', total: '81.00',
  appliedOffers: [{ offerId: 'o1', name: 'Kelewele 2 + 1', discount: '45.00' }],
  offerHints: [],
};

const request = { type: 'PICKUP' as const, items: [{ menuItemId: 'k', quantity: 3 }], promoCode: 'WELCOME10', loyaltyPointsToRedeem: 50 };

describe('quoteCart', () => {
  beforeEach(() => post.mockReset());

  test('a signed-in customer gets the full quote, promo applied after offers', async () => {
    post.mockResolvedValue({ data: serverQuote });
    const quote = await quoteCart(request, true);
    expect(post).toHaveBeenCalledWith('/orders/quote', expect.objectContaining({ promoCode: 'WELCOME10', loyaltyPointsToRedeem: 50 }));
    expect(quote.total).toBe(81);
    expect(quote.promoDiscount).toBe(9);
    expect(quote.appliedOffers[0].discount).toBe(45);
    expect(quote.promoRejected).toBe(false);
  });

  test('a guest uses the public quote, without loyalty points', async () => {
    post.mockResolvedValue({ data: serverQuote });
    await quoteCart(request, false);
    expect(post).toHaveBeenCalledWith('/orders/public/quote', expect.objectContaining({ loyaltyPointsToRedeem: undefined }));
  });

  test('a refused promo code is dropped and flagged, so the rest is still priced', async () => {
    post.mockRejectedValueOnce(new Error('Invalid promo code')).mockResolvedValueOnce({ data: { ...serverQuote, promoCode: null, promoDiscount: '0', total: '90.00' } });
    const quote = await quoteCart(request, true);
    expect(post).toHaveBeenLastCalledWith('/orders/quote', expect.objectContaining({ promoCode: undefined }));
    expect(quote.promoRejected).toBe(true);
    expect(quote.total).toBe(90);
  });
});
