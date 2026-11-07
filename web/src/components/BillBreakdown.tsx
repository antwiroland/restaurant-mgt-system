import type { TaxLineRecord } from "@/lib/apiClient";

type BillBreakdownProps = {
  currency?: string;
  subtotal: string | number;
  discountTotal?: string | number | null;
  deliveryFee?: string | number | null;
  serviceCharge?: string | number | null;
  taxLines?: TaxLineRecord[] | null;
  taxTotal?: string | number | null;
  taxInclusive?: boolean;
  tipAmount?: string | number | null;
  total: string | number;
  amountPaid?: string | number | null;
  refundedAmount?: string | number | null;
};

function num(value: string | number | null | undefined): number {
  if (value === null || value === undefined || value === "") return 0;
  const parsed = typeof value === "number" ? value : Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function percent(rate: string): string {
  const value = Number.parseFloat(rate);
  if (!Number.isFinite(value)) return rate;
  // Rates are stored as percentages (e.g. 15.00); trim trailing zeros for display.
  return `${Number(value.toFixed(3))}%`;
}

/** Itemised bill: subtotal, discounts, service charge, each tax, delivery fee, tip and total. */
export function BillBreakdown({
  currency = "GHS",
  subtotal,
  discountTotal,
  deliveryFee,
  serviceCharge,
  taxLines,
  taxTotal,
  taxInclusive,
  tipAmount,
  total,
  amountPaid,
  refundedAmount,
}: BillBreakdownProps) {
  const money = (value: number) => `${currency} ${value.toFixed(2)}`;
  const rows: { label: string; value: string; muted?: boolean }[] = [{ label: "Subtotal", value: money(num(subtotal)) }];

  if (num(discountTotal) > 0) rows.push({ label: "Discounts", value: `- ${money(num(discountTotal))}` });
  if (num(serviceCharge) > 0) rows.push({ label: "Service charge", value: money(num(serviceCharge)) });
  if (num(deliveryFee) > 0) rows.push({ label: "Delivery fee", value: money(num(deliveryFee)) });
  for (const line of taxLines ?? []) {
    rows.push({
      label: `${line.name} (${percent(line.rate)})${taxInclusive ? " incl." : ""}`,
      value: money(num(line.amount)),
      muted: taxInclusive,
    });
  }
  if ((taxLines?.length ?? 0) === 0 && num(taxTotal) > 0) {
    rows.push({ label: taxInclusive ? "Tax (included)" : "Tax", value: money(num(taxTotal)), muted: taxInclusive });
  }
  if (num(tipAmount) > 0) rows.push({ label: "Tip", value: money(num(tipAmount)) });

  return (
    <dl className="grid gap-1 text-sm" aria-label="Bill breakdown">
      {rows.map((row) => (
        <div key={row.label} className={`flex justify-between gap-3 ${row.muted ? "text-ink-soft" : ""}`}>
          <dt>{row.label}</dt>
          <dd>{row.value}</dd>
        </div>
      ))}
      <div className="mt-1 flex justify-between gap-3 border-t border-line pt-2 text-base font-semibold">
        <dt>Total</dt>
        <dd>{money(num(total))}</dd>
      </div>
      {taxInclusive && num(taxTotal) > 0 ? (
        <p className="text-xs text-ink-soft">Prices include {money(num(taxTotal))} of tax.</p>
      ) : null}
      {amountPaid !== undefined && amountPaid !== null ? (
        <div className="flex justify-between gap-3 text-ink-soft">
          <dt>Paid</dt>
          <dd>{money(num(amountPaid))}</dd>
        </div>
      ) : null}
      {num(refundedAmount) > 0 ? (
        <div className="flex justify-between gap-3 text-ink-soft">
          <dt>Refunded</dt>
          <dd>- {money(num(refundedAmount))}</dd>
        </div>
      ) : null}
    </dl>
  );
}
