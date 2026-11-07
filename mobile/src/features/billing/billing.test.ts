import { describe, expect, test } from 'vitest';
import { billingNote, billRows, canSplitByItems, myPendingShare, tipAmountFor } from './billing';
import type { BillingConfig, Order, PaymentSplit } from '../../types/api';

const config: BillingConfig = {
  serviceChargePercent: '10.00',
  serviceChargeDineInOnly: true,
  pricesIncludeTax: false,
  taxDeliveryFee: false,
  tipsEnabled: true,
  taxes: [
    { id: '1', code: 'NHIL', name: 'NHIL', rate: '2.50', compound: false, displayOrder: 1, active: true },
    { id: '2', code: 'VAT', name: 'VAT', rate: '15.00', compound: true, displayOrder: 2, active: true },
    { id: '3', code: 'OLD', name: 'Old levy', rate: '1.00', compound: false, displayOrder: 3, active: false },
  ],
};

function order(overrides: Partial<Order> = {}): Order {
  return {
    id: 'o1',
    status: 'PENDING',
    type: 'DINE_IN',
    subtotal: '100.00',
    total: '100.00',
    createdAt: new Date().toISOString(),
    items: [
      { id: 'i1', menuItemId: 'm1', menuItemName: 'Jollof', quantity: 1, unitPrice: '50', totalPrice: '50', participantId: 'p1' },
      { id: 'i2', menuItemId: 'm2', menuItemName: 'Waakye', quantity: 1, unitPrice: '50', totalPrice: '50', participantId: 'p2' },
    ],
    ...overrides,
  };
}

function split(overrides: Partial<PaymentSplit>): PaymentSplit {
  return { id: 's', orderId: 'o1', displayName: 'Ama', amount: '50.00', status: 'PENDING', ...overrides };
}

describe('Tips', () => {
  test('givenPercentChoice_whenTipComputed_thenRoundedToPesewas', () => {
    expect(tipAmountFor('10', 87.35, '')).toBe(8.74);
    expect(tipAmountFor('NONE', 87.35, '')).toBe(0);
  });

  test('givenCustomTip_whenInvalidOrNegative_thenZero', () => {
    expect(tipAmountFor('CUSTOM', 50, '7.5')).toBe(7.5);
    expect(tipAmountFor('CUSTOM', 50, 'abc')).toBe(0);
    expect(tipAmountFor('CUSTOM', 50, '-3')).toBe(0);
  });
});

describe('Billing note', () => {
  test('givenDineInOnlyServiceCharge_whenPickup_thenOnlyActiveTaxesMentioned', () => {
    const note = billingNote(config, 'PICKUP') ?? '';
    expect(note).not.toContain('service charge');
    expect(note).toContain('NHIL 2.5%');
    expect(note).toContain('VAT 15%');
    expect(note).not.toContain('Old levy');
  });

  test('givenDineIn_whenNoteBuilt_thenServiceChargeIncluded', () => {
    expect(billingNote(config, 'DINE_IN')).toContain('10% service charge');
  });

  test('givenNoChargesConfigured_whenNoteBuilt_thenNull', () => {
    expect(billingNote({ ...config, serviceChargePercent: '0', taxes: [] }, 'DINE_IN')).toBeNull();
  });
});

describe('Bill rows', () => {
  test('givenTaxesAndTip_whenRowsBuilt_thenZeroRowsSkipped', () => {
    const rows = billRows({
      subtotal: '100',
      discountTotal: '0',
      serviceCharge: '10',
      taxLines: [{ code: 'VAT', name: 'VAT', rate: '15.00', amount: '16.50' }],
      tipAmount: '5',
      total: '131.50',
    });
    expect(rows.map((r) => r.label)).toEqual(['Subtotal', 'Service charge', 'VAT 15%', 'Tip']);
  });
});

describe('Split shares', () => {
  test('givenGroupOrderWithNoPaidShares_whenPending_thenCanSplitByItems', () => {
    expect(canSplitByItems(order(), [])).toBe(true);
    expect(canSplitByItems(order(), [split({ status: 'PAID' })])).toBe(false);
    expect(canSplitByItems(order({ status: 'CONFIRMED' }), [])).toBe(false);
  });

  test('givenSoloOrder_whenChecked_thenCannotSplitByItems', () => {
    const solo = order({ items: [{ id: 'i1', menuItemId: 'm1', menuItemName: 'Jollof', quantity: 1, unitPrice: '50', totalPrice: '50' }] });
    expect(canSplitByItems(solo, [])).toBe(false);
  });

  test('givenShares_whenFindingMine_thenOnlyMyPendingShare', () => {
    const shares = [split({ id: 'a', userId: 'u1', status: 'PAID' }), split({ id: 'b', userId: 'u2' }), split({ id: 'c', userId: 'u1' })];
    expect(myPendingShare(shares, 'u1')?.id).toBe('c');
    expect(myPendingShare(shares, null)).toBeUndefined();
  });
});
