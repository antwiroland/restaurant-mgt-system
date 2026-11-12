"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Icon } from "@/components/Icon";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { formatMoney } from "@/lib/brand";
import {
  getPayment,
  initiatePayment,
  lookupOrder,
  retryPayment,
  type OrderRecord,
  type PaymentInitiateRecord,
  type PaymentMethod,
  type PaymentRecord,
  type PaymentStatus,
  verifyPayment,
} from "@/lib/apiClient";

/** What the cashier sees after taking a payment: plain words, never the raw response. */
type PaymentOutcome = {
  result: PaymentInitiateRecord;
  orderNumber: string;
  orderLabel: string;
  amount: string;
  method: PaymentMethod;
};

function outcomeTitle(status: PaymentStatus, method: PaymentMethod): { title: string; tone: "success" | "warning" | "danger" } {
  if (status === "SUCCESS") return { title: method === "CASH" ? "Cash received" : "Payment received", tone: "success" };
  if (status === "FAILED") return { title: "Payment failed", tone: "danger" };
  if (status === "REFUNDED" || status === "VOIDED") return { title: humanize(status), tone: "warning" };
  return { title: method === "CARD" ? "Waiting for the card payment" : "Waiting for the customer to approve", tone: "warning" };
}

const METHODS: Array<{ value: PaymentMethod; label: string }> = [
  { value: "CASH", label: "Cash" },
  { value: "MOBILE_MONEY", label: "Mobile Money" },
  { value: "CARD", label: "Card" },
];

export default function PaymentsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orderNumber, setOrderNumber] = useState("");
  const [order, setOrder] = useState<OrderRecord | null>(null);
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [momoPhone, setMomoPhone] = useState("");
  const [tendered, setTendered] = useState("");
  const [busy, setBusy] = useState(false);
  const [idem, setIdem] = useState("");
  const [splitId, setSplitId] = useState("");
  const [lookupPaymentId, setLookupPaymentId] = useState("");
  const [payment, setPayment] = useState<PaymentRecord | null>(null);
  const [outcome, setOutcome] = useState<PaymentOutcome | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  /** Finds the order from the number on the ticket (e.g. 7D63D7F5) so staff never paste an id. */
  async function findOrder(): Promise<OrderRecord | null> {
    if (!session || !orderNumber.trim()) return null;
    if (order && (order.id === orderNumber.trim() || order.id.slice(0, 8).toUpperCase() === orderNumber.trim().replace(/^#/, "").toUpperCase())) {
      return order;
    }
    const found = await authenticatedFetch((activeSession) => lookupOrder(activeSession, orderNumber));
    setOrder(found);
    return found;
  }

  async function onFindOrder() {
    setError("");
    setMessage("");
    try {
      await findOrder();
    } catch (err) {
      setOrder(null);
      setError(err instanceof Error ? err.message : "Could not find the order");
    }
  }

  async function onInitiate(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    setError("");
    setMessage("");
    const cash = method === "CASH";
    if (method === "MOBILE_MONEY" && !momoPhone.trim()) {
      setError("Enter the customer's MoMo number");
      return;
    }
    const amountTendered = cash && tendered.trim() ? Number(tendered) : undefined;
    setBusy(true);
    try {
      const target = await findOrder();
      if (!target) return;
      if (amountTendered !== undefined && amountTendered < Number(target.total)) {
        setError(`Amount received is less than ${formatMoney(target.total)}`);
        return;
      }
      const result = await authenticatedFetch((activeSession) =>
        initiatePayment(activeSession, {
          orderId: target.id,
          method,
          momoPhone: cash ? undefined : momoPhone.trim() || undefined,
          amountTendered,
          idempotencyKey: idem || `${target.id}-${Date.now()}`,
          splitId: splitId.trim() || undefined,
        }),
      );
      setOutcome({
        result,
        orderNumber: target.id.slice(0, 8).toUpperCase(),
        orderLabel: target.type === "DINE_IN" ? `Table ${target.tableNumber ?? "—"}` : target.type === "PICKUP" ? "Pickup" : "Delivery",
        amount: target.total,
        method,
      });
      setLookupPaymentId(result.paymentId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not initiate payment");
    } finally {
      setBusy(false);
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
      setOutcome((current) => (current && current.result.paymentId === result.id ? { ...current, result: { ...current.result, status: result.status } } : current));
      setMessage(result.status === "SUCCESS" ? "Payment confirmed." : result.status === "FAILED" ? "The payment failed." : "Still waiting for the customer.");
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
      setOutcome((current) => (current ? { ...current, result } : current));
      setMessage("Payment started again. Ask the customer to approve it.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not retry payment");
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-44" /><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /></section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Payments</p><h1 className="heading-lg mt-1">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4 md:grid-cols-2">
      <section className="panel">
        <p className="kicker">Payments</p>
        <h1 className="heading-lg mt-1">Initiate Payment</h1>
        <form className="mt-3 grid gap-3" onSubmit={(event) => void onInitiate(event)}>
          <label className="field">
            <span className="field-label">Order number</span>
            <div className="flex gap-2">
              <input className="input min-w-0 flex-1 font-mono uppercase" placeholder="e.g. 7D63D7F5" value={orderNumber} required
                onChange={(e) => { setOrderNumber(e.target.value); setOrder(null); }} onBlur={() => { if (orderNumber.trim() && !order) void onFindOrder(); }} />
              <button type="button" className="btn btn-secondary btn-md" onClick={() => void onFindOrder()} disabled={!orderNumber.trim()}>Find</button>
            </div>
            <span className="text-xs text-ink-muted">The number on the ticket or receipt. A full order id also works.</span>
          </label>
          {order ? (
            <p className="rounded-xl bg-ivory p-3 text-sm" role="status">
              <span className="font-mono font-semibold">#{order.id.slice(0, 8).toUpperCase()}</span>
              {" · "}{order.type === "DINE_IN" ? `Table ${order.tableNumber ?? "—"}` : order.type === "PICKUP" ? "Pickup" : "Delivery"}
              {" · "}<span className="money font-semibold">{formatMoney(order.total)}</span>
              {" · "}{order.status.toLowerCase()}
            </p>
          ) : null}
          <fieldset className="grid gap-2">
            <legend className="field-label">Method</legend>
            <div className="segmented grid grid-cols-3" role="group" aria-label="Payment method">
              {METHODS.map((m) => (
                <button key={m.value} type="button" aria-pressed={method === m.value} onClick={() => setMethod(m.value)}>{m.label}</button>
              ))}
            </div>
          </fieldset>
          {method === "CASH" ? (
            <label className="field">
              <span className="field-label">Amount received <span className="font-normal text-ink-muted">(optional, to work out change)</span></span>
              <input className="input" type="number" min={0} step="0.01" inputMode="decimal" value={tendered} onChange={(e) => setTendered(e.target.value)} />
            </label>
          ) : (
            <label className="field">
              <span className="field-label">{method === "MOBILE_MONEY" ? "MoMo number" : <>Customer phone <span className="font-normal text-ink-muted">(optional)</span></>}</span>
              <input className="input" inputMode="tel" autoComplete="tel" placeholder="024 123 4567" value={momoPhone} onChange={(e) => setMomoPhone(e.target.value)} required={method === "MOBILE_MONEY"} />
            </label>
          )}
          <details className="text-sm">
            <summary className="cursor-pointer text-ink-soft">More options</summary>
            <div className="mt-2 grid gap-2">
              <input className="input" placeholder="Idempotency key (optional)" value={idem} onChange={(e) => setIdem(e.target.value)} />
              <input className="input" placeholder="Split share ID (only for split bills)" value={splitId} onChange={(e) => setSplitId(e.target.value)} />
            </div>
          </details>
          <button className="btn btn-primary btn-lg" disabled={busy}>{method === "CASH" ? "Record cash payment" : "Start payment"}</button>
        </form>

        {outcome ? (
          <PaymentResult
            outcome={outcome}
            onVerify={() => void onVerify()}
            onNext={() => {
              setOutcome(null);
              setOrder(null);
              setOrderNumber("");
              setTendered("");
              setMomoPhone("");
              setIdem("");
              setSplitId("");
              setMessage("");
            }}
          />
        ) : null}
      </section>

      <section className="panel">
        <h2 className="heading-md">Lookup / Verify / Retry</h2>
        <div className="mt-3 flex flex-wrap gap-2">
          <input className="input min-w-0 flex-1 basis-64" placeholder="Payment ID" value={lookupPaymentId} onChange={(e) => setLookupPaymentId(e.target.value)} />
          <button className="btn btn-secondary btn-sm" onClick={() => void onLookup()}>Load</button>
          <button className="btn btn-secondary btn-sm" onClick={() => void onVerify()}>Verify</button>
          <button className="btn btn-secondary btn-sm" onClick={() => void onRetry()}>Retry</button>
        </div>

        {payment ? (
          <div className="mt-4 grid gap-1 rounded-xl border border-line p-4 text-sm">
            <p><span className="font-semibold">Status:</span> {humanize(payment.status)}</p>
            <p><span className="font-semibold">Order:</span> <span className="font-mono">#{payment.orderId.slice(0, 8).toUpperCase()}</span></p>
            <p><span className="font-semibold">Amount:</span> <span className="money">{formatMoney(payment.amount)}</span></p>
            <p><span className="font-semibold">Method:</span> {humanize(payment.method)}</p>
            <p><span className="font-semibold">Reference:</span> {payment.paystackReference || "—"}</p>
            <div className="mt-3">
              <Link className="btn btn-secondary btn-sm" href="/receipts">Open Receipt Center</Link>
            </div>
          </div>
        ) : null}

        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>
    </main>
  );
}


function PaymentResult({ outcome, onVerify, onNext }: { outcome: PaymentOutcome; onVerify: () => void; onNext: () => void }) {
  const { result, method } = outcome;
  const { title, tone } = outcomeTitle(result.status, method);
  const change = result.changeDue == null ? 0 : Number(result.changeDue);
  const waiting = result.status === "INITIATED" || result.status === "PENDING" || result.status === "PAYMENT_PENDING";
  return (
    <section className={`mt-4 grid gap-3 rounded-xl border p-4 ${tone === "success" ? "border-success/40 bg-success-subtle/50" : tone === "danger" ? "border-danger/40 bg-danger-subtle/50" : "border-warning/40 bg-warning-subtle/50"}`}
      role="status" aria-label="Payment result">
      <p className="flex items-center gap-2 font-display text-lg font-bold">
        <Icon name={tone === "success" ? "checkCircle" : "alert"} size={20} /> {title}
      </p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="text-ink-soft">Order</dt>
        <dd><span className="font-mono font-semibold">#{outcome.orderNumber}</span> · {outcome.orderLabel}</dd>
        <dt className="text-ink-soft">Amount</dt>
        <dd className="money font-semibold">{formatMoney(outcome.amount)}</dd>
        <dt className="text-ink-soft">Method</dt>
        <dd>{method === "MOBILE_MONEY" ? "Mobile Money" : humanize(method)}</dd>
        {result.paystackReference ? (
          <>
            <dt className="text-ink-soft">Reference</dt>
            <dd className="font-mono text-xs leading-5">{result.paystackReference}</dd>
          </>
        ) : null}
      </dl>
      {method === "CASH" && change > 0 ? (
        <p className="flex items-baseline justify-between rounded-lg bg-surface px-3 py-2">
          <span className="font-semibold">Change to give back</span>
          <span className="money font-display text-2xl font-extrabold">{formatMoney(change)}</span>
        </p>
      ) : null}
      {waiting ? (
        <p className="text-sm text-ink-soft">
          {method === "CARD" ? "Finish the card payment on the payment page, then check it." : "Ask the customer to approve the prompt on their phone, then check it."}
        </p>
      ) : null}
      {result.status === "FAILED" && result.message ? <p className="text-sm">{result.message}</p> : null}
      <div className="flex flex-wrap gap-2">
        {waiting && result.authorizationUrl ? (
          <a className="btn btn-secondary btn-sm" href={result.authorizationUrl} target="_blank" rel="noreferrer">Open card payment page</a>
        ) : null}
        {waiting ? <button type="button" className="btn btn-primary btn-sm" onClick={onVerify}>Check payment</button> : null}
        {result.status === "SUCCESS" ? <Link className="btn btn-secondary btn-sm" href="/receipts">Receipt</Link> : null}
        <button type="button" className="btn btn-secondary btn-sm" onClick={onNext}>Take another payment</button>
      </div>
    </section>
  );
}
