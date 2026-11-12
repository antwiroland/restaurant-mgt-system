"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { BillBreakdown } from "@/components/BillBreakdown";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { getReceiptByOrderId, getReceiptByPaymentId, lookupOrder, type ReceiptRecord } from "@/lib/apiClient";

const FULL_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function ReceiptsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orderNumber, setOrderNumber] = useState("");
  const [paymentId, setPaymentId] = useState("");
  const [receipt, setReceipt] = useState<ReceiptRecord | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  /** Finds the receipt from the number on the ticket (e.g. 7D63D7F5) or a full order id. */
  async function loadByOrder(value: string) {
    const input = value.trim();
    if (!session || !input) return;
    setBusy(true);
    setError("");
    try {
      const orderId = FULL_ID.test(input)
        ? input
        : (await authenticatedFetch((activeSession) => lookupOrder(activeSession, input))).id;
      setReceipt(await authenticatedFetch((activeSession) => getReceiptByOrderId(activeSession, orderId)));
    } catch (err) {
      setReceipt(null);
      const text = err instanceof Error ? err.message : "Could not load the receipt";
      setError(/not found|no .*payment|unpaid/i.test(text) && !/order with number/i.test(text)
        ? `No receipt for ${input.toUpperCase()} yet: the order has not been paid.`
        : text);
    } finally {
      setBusy(false);
    }
  }

  async function byPayment(event: FormEvent) {
    event.preventDefault();
    if (!session || !paymentId.trim()) return;
    setBusy(true);
    setError("");
    try {
      setReceipt(await authenticatedFetch((activeSession) => getReceiptByPaymentId(activeSession, paymentId.trim())));
    } catch (err) {
      setReceipt(null);
      setError(err instanceof Error ? err.message : "Could not load the receipt for that payment");
    } finally {
      setBusy(false);
    }
  }

  // Opened from a payment ("Receipt" button): /receipts?order=7D63D7F5
  useEffect(() => {
    if (!session) return;
    const fromLink = new URLSearchParams(window.location.search).get("order");
    if (fromLink) {
      setOrderNumber(fromLink);
      void loadByOrder(fromLink);
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
        <p className="mt-2 text-ink-soft">Find a paid order&apos;s receipt by the number on its ticket.</p>

        <form className="mt-4 grid gap-2" onSubmit={(e) => { e.preventDefault(); void loadByOrder(orderNumber); }}>
          <label className="field">
            <span className="field-label">Order number</span>
            <div className="flex gap-2">
              <input className="input min-w-0 flex-1 font-mono uppercase" placeholder="e.g. 7D63D7F5" value={orderNumber} onChange={(e) => setOrderNumber(e.target.value)} />
              <button type="submit" className="btn btn-primary btn-md" disabled={busy || !orderNumber.trim()}>Find receipt</button>
            </div>
            <span className="text-xs text-ink-muted">With or without #. A full order id also works.</span>
          </label>
        </form>

        <details className="mt-4 text-sm">
          <summary className="cursor-pointer text-ink-soft">Find by payment ID instead</summary>
          <form className="mt-2 flex gap-2" onSubmit={(e) => void byPayment(e)}>
            <input className="input min-w-0 flex-1 font-mono text-sm" placeholder="Payment ID" value={paymentId} onChange={(e) => setPaymentId(e.target.value)} />
            <button type="submit" className="btn btn-secondary btn-md" disabled={busy || !paymentId.trim()}>Find</button>
          </form>
        </details>

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
