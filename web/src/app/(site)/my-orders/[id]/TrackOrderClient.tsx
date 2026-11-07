"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { useBrand } from "@/components/BrandProvider";
import { useStaffSession } from "@/components/SessionProvider";
import { Spinner } from "@/components/Spinner";
import {
  getDeliveryTracking,
  getOrderById,
  getPayment,
  initiatePayment,
  verifyPayment,
  type DeliveryRecord,
  type OrderRecord,
  type PaymentRecord,
} from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";
import { cart } from "@/lib/cart";
import { orderStatusLabel, orderSteps } from "@/lib/orderStatus";

const ACTIVE = new Set(["PENDING", "CONFIRMED", "PREPARING", "READY", "OUT_FOR_DELIVERY"]);

export function TrackOrderClient({ orderId }: { orderId: string }) {
  const brand = useBrand();
  const paymentIdParam = useSearchParams().get("paymentId");
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [order, setOrder] = useState<OrderRecord | null>(null);
  const [delivery, setDelivery] = useState<DeliveryRecord | null>(null);
  const [payment, setPayment] = useState<PaymentRecord | null>(null);
  const [paymentId, setPaymentId] = useState(paymentIdParam);
  const [momoPhone, setMomoPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const next = await authenticatedFetch((s) => getOrderById(s, orderId));
      setOrder(next);
      if (next.type === "DELIVERY" && next.riderUserId) {
        setDelivery(await authenticatedFetch((s) => getDeliveryTracking(s, orderId)).catch(() => null));
      }
      if (paymentId) setPayment(await authenticatedFetch((s) => getPayment(s, paymentId)).catch(() => null));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the order");
    }
  }, [authenticatedFetch, orderId, paymentId]);

  useEffect(() => {
    if (!session) return;
    const first = window.setTimeout(() => void load(), 0);
    const poll = window.setInterval(() => void load(), 15000);
    return () => {
      window.clearTimeout(first);
      window.clearInterval(poll);
    };
  }, [load, session]);

  async function checkPayment() {
    if (!paymentId) return;
    setBusy(true);
    try {
      setPayment(await authenticatedFetch((s) => verifyPayment(s, paymentId)));
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not check payment");
    } finally {
      setBusy(false);
    }
  }

  async function payAgain() {
    if (!momoPhone.trim()) return setError("Enter the mobile money number to charge.");
    setBusy(true);
    setError("");
    try {
      const started = await authenticatedFetch((s) => initiatePayment(s, { orderId, method: "MOBILE_MONEY", momoPhone: momoPhone.trim(), idempotencyKey: `web-${orderId}-${Date.now()}` }));
      if (started.authorizationUrl) window.open(started.authorizationUrl, "_blank", "noopener");
      setPaymentId(started.paymentId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start payment");
    } finally {
      setBusy(false);
    }
  }

  function orderAgain() {
    if (!order) return;
    for (const item of order.items) {
      cart.add({
        menuItemId: item.menuItemId,
        name: item.name,
        unitPrice: Number(item.price) + (item.modifiers ?? []).reduce((sum, m) => sum + Number(m.priceDelta), 0),
        quantity: item.quantity,
        notes: item.notes,
        modifierOptionIds: [],
        modifierLabels: [],
      });
    }
    window.location.href = "/cart";
  }

  if (loading) return <Spinner size={28} />;
  if (!session) {
    return (
      <section className="mx-auto max-w-md rounded-card border border-line bg-surface p-8 text-center">
        <p>Sign in to see this order.</p>
        <Link href={`/account?next=/my-orders/${orderId}`} className="btn btn-primary mt-4">Sign in</Link>
      </section>
    );
  }
  if (error && !order) return <p className="alert alert-danger">{error}</p>;
  if (!order) return <Spinner size={28} />;

  const steps = orderSteps(order.type);
  const cancelled = order.status === "CANCELLED" || order.status === "VOIDED";
  const currentIndex = Math.max(0, steps.findIndex((s) => s.status === order.status));
  const unpaid = order.status === "PENDING";
  const mapsLink = delivery?.riderLatitude != null && delivery.riderLongitude != null
    ? `https://www.google.com/maps?q=${delivery.riderLatitude},${delivery.riderLongitude}`
    : null;

  return (
    <div className="mx-auto grid max-w-3xl gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <Link href="/account" className="text-sm font-semibold text-brand hover:underline">← Your orders</Link>
          <h1 className="heading-serif mt-2 text-4xl">Order #{order.id.slice(0, 8).toUpperCase()}</h1>
          <p className="mt-1 text-ink-soft">
            {order.type === "PICKUP" ? "Pickup" : order.type === "DELIVERY" ? "Delivery" : `Table ${order.tableNumber ?? ""}`} · {new Date(order.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}
          </p>
        </div>
        <span className={`badge ${cancelled ? "badge-danger" : ACTIVE.has(order.status) ? "badge-warning" : "badge-success"}`}>{orderStatusLabel(order)}</span>
      </div>

      {!cancelled ? (
        <section className="rounded-card border border-line bg-surface p-5" aria-label="Progress">
          <ol className="grid gap-3 sm:grid-cols-[repeat(var(--n),minmax(0,1fr))]" style={{ ["--n" as string]: steps.length }}>
            {steps.map((step, index) => {
              const done = index < currentIndex || (index === currentIndex && !ACTIVE.has(order.status));
              const current = index === currentIndex && ACTIVE.has(order.status);
              return (
                <li key={step.status} className="flex items-center gap-3 sm:flex-col sm:text-center">
                  <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 ${done ? "border-success bg-success text-white" : current ? "border-brand bg-brand-subtle text-brand" : "border-line text-ink-muted"}`}>
                    {done ? <Icon name="check" size={16} strokeWidth={3} /> : index + 1}
                  </span>
                  <span className={`text-sm ${current ? "font-bold text-ink" : "text-ink-soft"}`}>{step.label}</span>
                </li>
              );
            })}
          </ol>
          {order.type === "PICKUP" && order.pickupCode && !unpaid ? (
            <div className="mt-5 rounded-xl bg-cream p-4 text-center">
              <p className="text-xs font-bold uppercase tracking-widest text-ink-muted">Pickup code</p>
              <p className="font-mono text-4xl font-bold tracking-[0.2em] text-ink">{order.pickupCode}</p>
              <p className="mt-1 text-sm text-ink-soft">Show this at the counter{brand.address ? ` · ${brand.address}` : ""}.</p>
            </div>
          ) : null}
          {order.status === "OUT_FOR_DELIVERY" ? (
            <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-cream p-4">
              <p className="flex items-center gap-2 font-semibold"><Icon name="scooter" className="text-brand" /> {delivery?.riderName ? `${delivery.riderName} is on the way` : "Your rider is on the way"}</p>
              {mapsLink ? <a href={mapsLink} target="_blank" rel="noreferrer" className="btn btn-secondary btn-sm"><Icon name="pin" size={16} /> Rider location</a> : null}
            </div>
          ) : null}
          <p className="mt-4 text-xs text-ink-muted">Updates every 15 seconds.</p>
        </section>
      ) : (
        <p className="alert alert-danger">This order was cancelled{order.cancelReason ? `: ${order.cancelReason}` : "."}</p>
      )}

      {unpaid && !cancelled ? (
        <section className="rounded-card border border-warning-border bg-warning-subtle p-5">
          <h2 className="font-display text-lg font-bold">Waiting for payment</h2>
          {payment ? (
            <>
              <p className="mt-1 text-sm">
                {payment.status === "FAILED" ? "The last payment attempt failed." : "Approve the prompt on your phone (or finish the card page), then check the payment."}
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void checkPayment()}>{busy ? <Spinner /> : null} Check payment</button>
                {payment.authorizationUrl && payment.status !== "FAILED" ? <a className="btn btn-secondary" href={payment.authorizationUrl} target="_blank" rel="noreferrer">Open payment page</a> : null}
              </div>
            </>
          ) : null}
          {!payment || payment.status === "FAILED" ? (
            <div className="mt-3 flex flex-wrap gap-2">
              <input className="input max-w-xs" type="tel" placeholder="Mobile money number" value={momoPhone} onChange={(e) => setMomoPhone(e.target.value)} aria-label="Mobile money number" />
              <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void payAgain()}>Pay {formatMoney(order.total)}</button>
            </div>
          ) : null}
          {error ? <p className="mt-2 text-sm text-danger-on" role="alert">{error}</p> : null}
        </section>
      ) : null}

      <section className="rounded-card border border-line bg-surface p-5">
        <h2 className="font-display text-lg font-bold">Items</h2>
        <ul className="mt-3 divide-y divide-line-subtle">
          {order.items.map((item) => (
            <li key={item.id} className="flex justify-between gap-3 py-2.5">
              <div>
                <p className="font-semibold">{item.quantity} × {item.name}</p>
                {item.modifiers?.length ? <p className="text-sm text-ink-soft">{item.modifiers.map((m) => m.optionName).join(" · ")}</p> : null}
                {item.notes ? <p className="text-sm italic text-ink-muted">“{item.notes}”</p> : null}
              </div>
              <span className="money">{formatMoney(Number(item.price) * item.quantity)}</span>
            </li>
          ))}
        </ul>
        <dl className="mt-3 grid gap-1 border-t border-line pt-3 text-sm">
          {Number(order.discountTotal ?? 0) > 0 ? <div className="flex justify-between"><dt className="text-ink-soft">Discounts</dt><dd className="money">- {formatMoney(order.discountTotal)}</dd></div> : null}
          {Number(order.deliveryFee ?? 0) > 0 ? <div className="flex justify-between"><dt className="text-ink-soft">Delivery</dt><dd className="money">{formatMoney(order.deliveryFee)}</dd></div> : null}
          {Number(order.tipAmount ?? 0) > 0 ? <div className="flex justify-between"><dt className="text-ink-soft">Tip</dt><dd className="money">{formatMoney(order.tipAmount)}</dd></div> : null}
          <div className="flex justify-between font-display text-base font-bold"><dt>Total</dt><dd className="money">{formatMoney(order.total)}</dd></div>
        </dl>
        {order.deliveryAddress ? <p className="mt-3 flex gap-2 text-sm text-ink-soft"><Icon name="pin" size={16} className="mt-0.5" /> {order.deliveryAddress}</p> : null}
      </section>

      {!ACTIVE.has(order.status) ? (
        <button type="button" className="btn btn-secondary w-fit" onClick={orderAgain}><Icon name="refresh" size={16} /> Order again</button>
      ) : null}
    </div>
  );
}
