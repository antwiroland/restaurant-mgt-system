export type Reconciliation = {
  sales: number;
  refunds: number;
  discounts: number;
};

export function applyFlatDiscount(total: number, amount: number): number {
  return Math.max(0, total - amount);
}

export function applyRefund(status: string, amount: number): { status: string; refundedAmount: number } {
  return { status, refundedAmount: amount };
}

export function reconcile(records: Reconciliation[]): Reconciliation {
  return records.reduce(
    (acc, item) => ({
      sales: acc.sales + item.sales,
      refunds: acc.refunds + item.refunds,
      discounts: acc.discounts + item.discounts,
    }),
    { sales: 0, refunds: 0, discounts: 0 },
  );
}
