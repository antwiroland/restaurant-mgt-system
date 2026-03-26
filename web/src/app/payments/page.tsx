"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  getPayment,
  initiatePayment,
  retryPayment,
  type PaymentInitiateRecord,
  type PaymentMethod,
  type PaymentRecord,
  verifyPayment,
} from "@/lib/apiClient";

export default function PaymentsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orderId, setOrderId] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("MOBILE_MONEY");
  const [momoPhone, setMomoPhone] = useState("");
  const [idem, setIdem] = useState("");
  const [lookupPaymentId, setLookupPaymentId] = useState("");
  const [payment, setPayment] = useState<PaymentRecord | null>(null);
  const [initResult, setInitResult] = useState<PaymentInitiateRecord | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function onInitiate(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    try {
      setError("");
      setMessage("");
      const result = await authenticatedFetch((activeSession) =>
        initiatePayment(activeSession, {
          orderId,
          method,
          momoPhone,
          idempotencyKey: idem || `${orderId}-${Date.now()}`,
        }),
      );
      setInitResult(result);
      setLookupPaymentId(result.paymentId);
      setMessage("Payment initiated");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not initiate payment");
    }
  }

  async function onLookup() {
    if (!session || !lookupPaymentId) return;
    try {
      setError("");
      const result = await authenticatedFetch((activeSession) => getPayment(activeSession, lookupPaymentId));
      setPayment(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load payment");
    }
  }

  async function onVerify() {
    if (!session || !lookupPaymentId) return;
    try {
      setError("");
      const result = await authenticatedFetch((activeSession) => verifyPayment(activeSession, lookupPaymentId));
      setPayment(result);
      setMessage("Payment verification completed");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not verify payment");
    }
  }

  async function onRetry() {
    if (!session || !lookupPaymentId) return;
    try {
      setError("");
      const result = await authenticatedFetch((activeSession) =>
        retryPayment(activeSession, lookupPaymentId, {
          momoPhone,
          idempotencyKey: `${lookupPaymentId}-retry-${Date.now()}`,
        }),
      );
      setInitResult(result);
      setMessage("Payment retry initiated");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not retry payment");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading payments...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Payments</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4 md:grid-cols-2">
      <section className="panel">
        <p className="kicker">Payments</p>
        <h1 className="text-2xl font-semibold">Initiate Payment</h1>
        <form className="mt-3 grid gap-2" onSubmit={(event) => void onInitiate(event)}>
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Order ID" value={orderId} onChange={(e) => setOrderId(e.target.value)} required />
          <select className="rounded-xl border border-[#cfe0c8] p-2" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            <option value="MOBILE_MONEY">MOBILE_MONEY</option>
            <option value="CARD">CARD</option>
            <option value="CASH">CASH</option>
          </select>
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="MoMo Phone" value={momoPhone} onChange={(e) => setMomoPhone(e.target.value)} required />
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Idempotency key (optional)" value={idem} onChange={(e) => setIdem(e.target.value)} />
          <button className="rounded-full bg-[#132018] px-4 py-2 text-white">Initiate</button>
        </form>

        {initResult ? <pre className="mt-3 overflow-auto rounded-xl border border-[#cfe0c8] p-3 text-xs">{JSON.stringify(initResult, null, 2)}</pre> : null}
      </section>

      <section className="panel">
        <h2 className="text-xl font-semibold">Lookup / Verify / Retry</h2>
        <div className="mt-3 flex flex-wrap gap-2">
          <input className="min-w-[320px] rounded-xl border border-[#cfe0c8] p-2" placeholder="Payment ID" value={lookupPaymentId} onChange={(e) => setLookupPaymentId(e.target.value)} />
          <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void onLookup()}>Load</button>
          <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void onVerify()}>Verify</button>
          <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void onRetry()}>Retry</button>
        </div>

        {payment ? (
          <div className="mt-4 rounded-xl border border-[#cfe0c8] p-3 text-sm">
            <p><span className="font-semibold">Status:</span> {payment.status}</p>
            <p><span className="font-semibold">Order:</span> {payment.orderId}</p>
            <p><span className="font-semibold">Amount:</span> {payment.currency} {payment.amount}</p>
            <p><span className="font-semibold">Method:</span> {payment.method}</p>
            <p><span className="font-semibold">Reference:</span> {payment.paystackReference || "-"}</p>
            <div className="mt-3">
              <Link className="rounded-full border border-[#132018] px-3 py-1 text-xs" href="/receipts">Open Receipt Center</Link>
            </div>
          </div>
        ) : null}

        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        {message ? <p className="mt-3 rounded-xl bg-[#dcfce7] px-4 py-3 text-sm text-[#166534]">{message}</p> : null}
      </section>
    </main>
  );
}
