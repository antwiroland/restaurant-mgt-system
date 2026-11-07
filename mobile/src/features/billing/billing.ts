import type { BillingConfig, Order, OrderType, PaymentSplit, TaxLine } from '../../types/api';

export type TipChoice = 'NONE' | '5' | '10' | '15' | 'CUSTOM';

export const TIP_CHOICES: TipChoice[] = ['NONE', '5', '10', '15', 'CUSTOM'];

/** Tip in GHS for a choice, rounded to pesewas; never negative. */
export function tipAmountFor(choice: TipChoice, subtotal: number, custom: string): number {
  if (choice === 'NONE') return 0;
  const raw = choice === 'CUSTOM' ? Number.parseFloat(custom) : (subtotal * Number(choice)) / 100;
  if (!Number.isFinite(raw) || raw <= 0) return 0;
  return Math.round(raw * 100) / 100;
}

/** Plain-language note about what the restaurant adds to the bill for this order type. */
export function billingNote(config: BillingConfig | null, orderType: OrderType): string | null {
  if (!config) return null;
  const parts: string[] = [];
  const service = Number(config.serviceChargePercent);
  if (service > 0 && (!config.serviceChargeDineInOnly || orderType === 'DINE_IN')) {
    parts.push(`a ${trim(service)}% service charge`);
  }
  const taxes = config.taxes.filter((tax) => tax.active);
  if (taxes.length > 0) {
    const names = taxes.map((tax) => `${tax.name} ${trim(Number(tax.rate))}%`).join(', ');
    parts.push(config.pricesIncludeTax ? `taxes (${names}), already included in menu prices` : `taxes (${names})`);
  }
  if (parts.length === 0) return null;
  return `Your bill includes ${parts.join(' and ')}. The exact amounts appear once the order is placed.`;
}

function trim(value: number): string {
  return String(Number(value.toFixed(3)));
}

export type BillProps = {
  subtotal: string;
  discountTotal?: string;
  deliveryFee?: string;
  serviceCharge?: string;
  taxLines?: TaxLine[];
  taxTotal?: string;
  taxInclusive?: boolean;
  tipAmount?: string;
  total: string;
};

const n = (value?: string) => {
  const parsed = Number.parseFloat(value ?? '');
  return Number.isFinite(parsed) ? parsed : 0;
};

/** Rows for subtotal, discounts, service charge, each tax, delivery, tip and total. */
export function billRows(props: BillProps): { label: string; value: string; muted?: boolean }[] {
  const money = (v: number) => `GHS ${v.toFixed(2)}`;
  const rows: { label: string; value: string; muted?: boolean }[] = [{ label: 'Subtotal', value: money(n(props.subtotal)) }];
  if (n(props.discountTotal) > 0) rows.push({ label: 'Discounts', value: `- ${money(n(props.discountTotal))}` });
  if (n(props.serviceCharge) > 0) rows.push({ label: 'Service charge', value: money(n(props.serviceCharge)) });
  if (n(props.deliveryFee) > 0) rows.push({ label: 'Delivery fee', value: money(n(props.deliveryFee)) });
  for (const line of props.taxLines ?? []) {
    rows.push({
      label: `${line.name} ${Number(Number(line.rate).toFixed(3))}%${props.taxInclusive ? ' (incl.)' : ''}`,
      value: money(n(line.amount)),
      muted: props.taxInclusive,
    });
  }
  if ((props.taxLines?.length ?? 0) === 0 && n(props.taxTotal) > 0) {
    rows.push({ label: props.taxInclusive ? 'Tax (incl.)' : 'Tax', value: money(n(props.taxTotal)), muted: props.taxInclusive });
  }
  if (n(props.tipAmount) > 0) rows.push({ label: 'Tip', value: money(n(props.tipAmount)) });
  return rows;
}

/** A group order can be split by items while it is unpaid and no share has been paid. */
export function canSplitByItems(order: Order, splits: PaymentSplit[]): boolean {
  const isGroup = order.items.some((item) => !!item.participantId);
  const anyPaid = splits.some((split) => split.status === 'PAID');
  return isGroup && !anyPaid && order.status === 'PENDING';
}

/** The share this customer should pay: their own pending share. */
export function myPendingShare(splits: PaymentSplit[], userId: string | null): PaymentSplit | undefined {
  if (!userId) return undefined;
  return splits.find((split) => split.status === 'PENDING' && split.userId === userId);
}
