"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import { getOrderByPickupCode, updateOrderStatus, type OrderRecord } from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";

const STATUS_TEXT: Partial<Record<OrderRecord["status"], string>> = {
  PENDING: "Waiting for payment: not sent to the kitchen yet",
  CONFIRMED: "In queue: the kitchen has not started it",
  PREPARING: "Being prepared",
  READY: "Ready to hand over",
  COMPLETED: "Already collected",
  CANCELLED: "Cancelled",
};

const PICKUP_CODE = /^PK\d{6}$/i;

/**
 * Customer at the counter: find their pickup by pickup code or the order number on the board/receipt, then hand it
 * over. Handing over needs the customer's pickup code (the order number is on the public board, so it is not proof);
 * admins and managers can override for a customer who lost theirs.
 */
export default function PickupLookupPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [code, setCode] = useState("");
  const [order, setOrder] = useState<OrderRecord | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  /** The pickup code the customer gave: the search itself when they searched by code, otherwise typed at handover. */
  const [handoverCode, setHandoverCode] = useState("");
  const [codeFromSearch, setCodeFromSearch] = useState(false);
  const canOverride = session?.user.role === "ADMIN" || session?.user.role === "MANAGER";

  async function onLookup(event: FormEvent) {
    event.preventDefault();
    // "#" would end the URL path, so send the number without it
    const typed = code.trim().replace(/^#/, "");
    if (!session || !typed) return;
    setError("");
    setMessage("");
    try {
      const next = await authenticatedFetch((activeSession) => getOrderByPickupCode(activeSession, typed));
      setOrder(next);
      const byCode = PICKUP_CODE.test(typed);
      setCodeFromSearch(byCode);
      setHandoverCode(byCode ? typed.toUpperCase() : "");
    } catch (err) {
      setOrder(null);
      setError(err instanceof Error ? err.message : "Could not find order");
    }
  }

  async function markCollected(withoutCode = false) {
    if (!order) return;
    const pickupCode = withoutCode ? undefined : handoverCode.trim().replace(/^#/, "");
    if (!withoutCode && !pickupCode) {
      setError("Ask the customer for their pickup code (PK followed by 6 digits).");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const next = await authenticatedFetch((activeSession) => updateOrderStatus(activeSession, order.id, "COMPLETED", pickupCode));
      setOrder(next);
      setMessage(`Order #${order.id.slice(0, 8).toUpperCase()} handed over. It has left the order board.`);
      setCode("");
      setHandoverCode("");
      setCodeFromSearch(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not mark the order collected");
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-10 w-full" /></section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Pickup Lookup</p><h1 className="heading-lg mt-1">Staff access required</h1><Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link></section></main>;

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Pickup</p>
        <h1 className="heading-lg mt-1">Pickup Lookup</h1>
        <p className="mt-1 text-sm text-ink-soft">Enter the pickup code or the order number from the receipt or the order board.</p>
        <form className="mt-4 flex gap-2" onSubmit={(event) => void onLookup(event)}>
          <label className="sr-only" htmlFor="pickup-code">Pickup code or order number</label>
          <input
            id="pickup-code"
            className="input font-mono uppercase"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="AC724F02 or PK000001"
            autoComplete="off"
            autoCapitalize="characters"
          />
          <button className="btn btn-primary">Search</button>
        </form>
        {error ? <p className="mt-3 alert alert-danger" role="alert">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success" role="status">{message}</p> : null}
        {order ? (
          <div className="mt-4 rounded-xl border border-line p-4">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <p className="font-mono text-xl font-bold">#{order.id.slice(0, 8).toUpperCase()}</p>
              {codeFromSearch ? <p className="text-sm font-semibold text-success-on">Pickup code checked</p> : null}
            </div>
            <p className={`mt-1 font-semibold ${order.status === "READY" ? "text-success-on" : "text-ink"}`}>
              {STATUS_TEXT[order.status] ?? order.status}
            </p>
            {order.guestName ? <p className="text-sm text-ink-soft">For {order.guestName}</p> : null}
            <ul className="mt-3 grid gap-1 text-sm">
              {order.items.map((item) => (
                <li key={item.id} className="flex justify-between gap-3">
                  <span>{item.quantity} × {item.name}</span>
                </li>
              ))}
            </ul>
            <p className="mt-2 text-sm text-ink-soft">Total {formatMoney(order.total)}</p>
            {order.status === "READY" && !codeFromSearch ? (
              <div className="mt-4">
                <label className="text-sm font-semibold text-ink" htmlFor="handover-code">Customer&apos;s pickup code</label>
                <p className="text-xs text-ink-soft">Ask the customer to show it in their app or on their receipt.</p>
                <input
                  id="handover-code"
                  className="input mt-1 font-mono uppercase"
                  value={handoverCode}
                  onChange={(e) => setHandoverCode(e.target.value)}
                  placeholder="PK000000"
                  autoComplete="off"
                  autoCapitalize="characters"
                />
              </div>
            ) : null}
            <div className="mt-4 flex flex-wrap gap-2">
              {order.status === "READY" ? (
                <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void markCollected()}>
                  Mark collected
                </button>
              ) : null}
              {order.status === "READY" && canOverride && !codeFromSearch ? (
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={busy}
                  onClick={() => {
                    if (window.confirm("Hand over without the pickup code? Only do this after checking who the customer is.")) {
                      void markCollected(true);
                    }
                  }}
                >
                  Hand over without code
                </button>
              ) : null}
              <Link className="btn btn-secondary" href={`/orders/${order.id}`}>Open order</Link>
            </div>
          </div>
        ) : null}
      </section>
    </main>
  );
}
