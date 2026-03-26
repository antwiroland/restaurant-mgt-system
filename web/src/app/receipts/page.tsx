"use client";

import Link from "next/link";
import { useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { getReceiptByOrderId, getReceiptByPaymentId, type ReceiptRecord } from "@/lib/apiClient";

export default function ReceiptsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orderId, setOrderId] = useState("");
  const [paymentId, setPaymentId] = useState("");
  const [receipt, setReceipt] = useState<ReceiptRecord | null>(null);
  const [error, setError] = useState("");

  async function byOrder() {
    if (!session || !orderId) return;
    try {
      setError("");
      const data = await authenticatedFetch((activeSession) => getReceiptByOrderId(activeSession, orderId));
      setReceipt(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load receipt by order");
    }
  }

  async function byPayment() {
    if (!session || !paymentId) return;
    try {
      setError("");
      const data = await authenticatedFetch((activeSession) => getReceiptByPaymentId(activeSession, paymentId));
      setReceipt(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load receipt by payment");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading receipts...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Receipts</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4 md:grid-cols-2">
      <section className="panel">
        <p className="kicker">Receipts</p>
        <h1 className="text-2xl font-semibold">Receipt Center</h1>
        <p className="mt-2 text-[#35523d]">Fetch receipts by order ID or payment ID.</p>

        <div className="mt-4 grid gap-2">
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Order ID" value={orderId} onChange={(e) => setOrderId(e.target.value)} />
          <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void byOrder()}>Load by Order</button>
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Payment ID" value={paymentId} onChange={(e) => setPaymentId(e.target.value)} />
          <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void byPayment()}>Load by Payment</button>
        </div>

        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
      </section>

      <section className="panel">
        <h2 className="text-xl font-semibold">Receipt Details</h2>
        {receipt ? (
          <div className="mt-3 rounded-xl border border-[#cfe0c8] p-3 text-sm">
            <p><span className="font-semibold">Receipt:</span> {receipt.receiptNumber}</p>
            <p><span className="font-semibold">Order:</span> {receipt.orderId}</p>
            <p><span className="font-semibold">Payment:</span> {receipt.paymentId}</p>
            <p><span className="font-semibold">Subtotal:</span> {receipt.currency} {receipt.subtotal}</p>
            <p><span className="font-semibold">Total:</span> {receipt.currency} {receipt.total}</p>
            <p><span className="font-semibold">Method:</span> {receipt.paymentMethod}</p>
            <p><span className="font-semibold">Paid At:</span> {receipt.paidAt ? new Date(receipt.paidAt).toLocaleString() : "-"}</p>
          </div>
        ) : (
          <p className="mt-3 text-sm text-[#35523d]">No receipt loaded.</p>
        )}
      </section>
    </main>
  );
}
