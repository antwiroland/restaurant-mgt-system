"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { BillBreakdown } from "@/components/BillBreakdown";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { findReceipt, type ReceiptRecord } from "@/lib/apiClient";

export default function ReceiptsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [query, setQuery] = useState("");
  const [receipt, setReceipt] = useState<ReceiptRecord | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  /**
   * Finds a receipt from anything the cashier has: the order number on the ticket (7D63D7F5), the receipt number
   * (RCP-20261112-2FE7D111), the payment reference (CASH-DADB89D6) or a full order or payment id.
   */
  async function search(value: string) {
    const input = value.trim();
    if (!session || !input) return;
    setBusy(true);
    setError("");
    try {
      setReceipt(await authenticatedFetch((activeSession) => findReceipt(activeSession, input)));
    } catch (err) {
      setReceipt(null);
      const text = err instanceof Error ? err.message : "Could not load the receipt";
      setError(text === "Receipt not found" ? `No receipt for ${input.toUpperCase()} yet: it has not been paid.` : text);
    } finally {
      setBusy(false);
    }
  }

  // Opened from a payment ("Receipt" button): /receipts?order=7D63D7F5
  useEffect(() => {
    if (!session) return;
    const params = new URLSearchParams(window.location.search);
    const fromLink = params.get("order") ?? params.get("q");
    if (fromLink) {
      setQuery(fromLink);
      void search(fromLink);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.user.id]);

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /></section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Receipts</p><h1 className="heading-lg mt-1">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4 md:grid-cols-2">
      <section className="panel">
        <p className="kicker">Receipts</p>
        <h1 className="heading-lg mt-1">Receipt Center</h1>
        <p className="mt-2 text-ink-soft">Find a receipt from anything on the ticket or the payment.</p>

        <form className="mt-4 grid gap-2" onSubmit={(e: FormEvent) => { e.preventDefault(); void search(query); }}>
          <label className="field">
            <span className="field-label">Order number, receipt number or payment reference</span>
            <div className="flex gap-2">
              <input className="input min-w-0 flex-1 font-mono uppercase" placeholder="e.g. 7D63D7F5" value={query} onChange={(e) => setQuery(e.target.value)} />
              <button type="submit" className="btn btn-primary btn-md" disabled={busy || !query.trim()}>Find receipt</button>
            </div>
            <span className="text-xs text-ink-muted">For example 7D63D7F5, RCP-20261112-2FE7D111 or CASH-DADB89D6. A full order or payment id also works.</span>
          </label>
        </form>

        {error ? <p className="mt-3 alert alert-danger" role="alert">{error}</p> : null}
      </section>

      <section className="panel">
        <h2 className="heading-md">Receipt details</h2>
        {receipt ? (
          <div className="mt-3 grid gap-1 rounded-xl border border-line p-4 text-sm">
            <p><span className="font-semibold">Receipt:</span> {receipt.receiptNumber}</p>
            <p><span className="font-semibold">Order:</span> <span className="font-mono">#{receipt.orderId.slice(0, 8).toUpperCase()}</span></p>
            <p><span className="font-semibold">Method:</span> {receipt.paymentMethod === "MOBILE_MONEY" ? "Mobile Money" : humanize(receipt.paymentMethod)}</p>
            <p><span className="font-semibold">Paid:</span> {receipt.paidAt ? new Date(receipt.paidAt).toLocaleString() : "—"}</p>
            <div className="mt-3">
              <BillBreakdown
                currency={receipt.currency}
                subtotal={receipt.subtotal}
                discountTotal={receipt.discountTotal}
                deliveryFee={receipt.deliveryFee}
                serviceCharge={receipt.serviceCharge}
                taxLines={receipt.taxLines}
                taxTotal={receipt.taxTotal}
                taxInclusive={receipt.taxInclusive}
                tipAmount={receipt.tipAmount}
                total={receipt.total}
                amountPaid={receipt.amountPaid}
                refundedAmount={receipt.refundedAmount}
              />
            </div>
          </div>
        ) : (
          <p className="mt-3 text-sm text-ink-soft">No receipt loaded.</p>
        )}
      </section>
    </main>
  );
}
