"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import { BillBreakdown } from "@/components/BillBreakdown";
import { SplitBillPanel } from "@/components/SplitBillPanel";
import { cancelOrder, getOrderById, sendOrderToPrinter, getOrderFeedback, type FeedbackRecord, type OrderRecord } from "@/lib/apiClient";

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [order, setOrder] = useState<OrderRecord | null>(null);
  const [printNotice, setPrintNotice] = useState("");
  const [feedback, setFeedback] = useState<FeedbackRecord | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [cancelReason, setCancelReason] = useState("");
  const [cancelFieldError, setCancelFieldError] = useState("");
  const [busyCancel, setBusyCancel] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const subtotal = useMemo(() => {
    if (!order) return 0;
    return order.items.reduce((sum, item) => sum + Number.parseFloat(item.price) * item.quantity, 0);
  }, [order]);

  async function loadOrder() {
    if (!session || !params?.id) return;
    setRefreshing(true);
    try {
      const next = await authenticatedFetch((activeSession) => getOrderById(activeSession, params.id));
      setOrder(next);
      const rating = await authenticatedFetch((activeSession) => getOrderFeedback(activeSession, next.id)).catch(() => undefined);
      setFeedback(rating ?? null);
    } finally {
      setRefreshing(false);
    }
  }

  useEffect(() => {
    loadOrder().catch((err) => setError(err instanceof Error ? err.message : "Could not load order"));
  }, [session, params?.id]);

  async function handleCancel() {
    if (!session || !order) return;
    if (!cancelReason.trim()) {
      setCancelFieldError("Provide a cancellation reason");
      return;
    }
    setBusyCancel(true);
    setError("");
    setMessage("");
    setCancelFieldError("");
    try {
      await authenticatedFetch((activeSession) => cancelOrder(activeSession, order.id, { reason: cancelReason.trim() }));
      setMessage("Order cancelled.");
      await loadOrder();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not cancel order");
    } finally {
      setBusyCancel(false);
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-32 w-full" /><Skeleton className="h-24 w-full" /></section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Order</p><h1 className="heading-lg mt-1">Staff access required</h1><Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link></section></main>;

  async function sendToPrinter(kind: "receipt" | "kitchen") {
    if (!order) return;
    setPrintNotice("");
    try {
      const result = await authenticatedFetch((activeSession) => sendOrderToPrinter(activeSession, order.id, kind));
      setPrintNotice(result.message);
    } catch (err) {
      setPrintNotice(err instanceof Error ? err.message : "Could not print");
    }
  }

  return (
    <main className="shell grid gap-4">
      {printNotice ? <p className="alert alert-info" role="status">{printNotice}</p> : null}
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="kicker">Order Detail</p>
            <h1 className="heading-lg mt-1">{order ? `#${order.id.slice(0, 8).toUpperCase()}` : "Order not found"}</h1>
            {order ? (
              <p className="mt-1 text-sm text-ink-soft">
                {order.type} - {order.status} - Created {new Date(order.createdAt).toLocaleString()}
              </p>
            ) : null}
          </div>
          <div className="flex flex-wrap gap-2">
            {order ? (
              <>
                <a className="btn btn-secondary" href={`/print/receipt/${order.id}`} target="_blank" rel="noreferrer">Print receipt</a>
                <a className="btn btn-secondary" href={`/print/kitchen/${order.id}`} target="_blank" rel="noreferrer">Print kitchen ticket</a>
                <button type="button" className="btn btn-secondary" onClick={() => void sendToPrinter("receipt")}>Send receipt to printer</button>
              </>
            ) : null}
            <button
              type="button"
              className="btn btn-secondary disabled:opacity-60"
              onClick={() => void loadOrder()}
              disabled={refreshing}
            >
              {refreshing ? "Refreshing..." : "Refresh"}
            </button>
            <Link className="btn btn-secondary" href="/orders">Back</Link>
          </div>
        </div>

        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>

      {order ? (
        <>
          <section className="panel grid gap-2 text-sm text-ink-soft md:grid-cols-2">
            <p><span className="font-semibold text-ink">Order ID:</span> {order.id}</p>
            <p><span className="font-semibold text-ink">Status:</span> {order.status}</p>
            <p><span className="font-semibold text-ink">Type:</span> {order.type}</p>
            <p><span className="font-semibold text-ink">Table:</span> {order.tableNumber ?? "-"}</p>
            {order.pickupCode ? <p><span className="font-semibold text-ink">Pickup Code:</span> {order.pickupCode}</p> : null}
            {order.deliveryAddress ? <p><span className="font-semibold text-ink">Delivery Address:</span> {order.deliveryAddress}</p> : null}
            {order.notes ? <p className="md:col-span-2"><span className="font-semibold text-ink">Notes:</span> {order.notes}</p> : null}
          </section>

          <section className="panel">
            <h2 className="heading-md">Items</h2>
            <ul className="mt-4 grid gap-2">
              {order.items.map((item) => (
                <li key={item.id} className="rounded-xl border border-line bg-surface p-3">
                  <div className="flex items-center justify-between">
                    <p className="font-medium">{item.name} x{item.quantity}</p>
                    <p className="text-sm text-ink-soft">GHS {(Number.parseFloat(item.price) * item.quantity).toFixed(2)}</p>
                  </div>
                  <p className="text-xs text-ink-soft">Unit: GHS {item.price}</p>
                  {item.modifiers && item.modifiers.length > 0 ? (
                    <p className="mt-1 text-xs text-ink-soft">
                      {item.modifiers.map((m) => `${m.groupName}: ${m.optionName}`).join(" | ")}
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
            <p className="mt-3 text-sm text-ink-soft">Computed items subtotal: GHS {subtotal.toFixed(2)}</p>
          </section>

          <section className="panel">
            <h2 className="mb-3 text-xl font-semibold">Bill</h2>
            <BillBreakdown
              subtotal={order.subtotal}
              discountTotal={order.discountTotal}
              deliveryFee={order.deliveryFee}
              serviceCharge={order.serviceCharge}
              taxLines={order.taxLines}
              taxTotal={order.taxTotal}
              taxInclusive={order.taxInclusive}
              tipAmount={order.tipAmount}
              total={order.total}
            />
            {order.promoCode ? <p className="mt-2 text-xs text-ink-soft">Promo code: {order.promoCode}</p> : null}
          </section>

          <SplitBillPanel order={order} onPaid={() => void loadOrder()} />

          {feedback ? (
            <section className="panel">
              <h2 className="text-lg font-semibold">Customer rating</h2>
              <p className="mt-1 text-sm">
                <span className="font-semibold" aria-label={`${feedback.rating} out of 5`}>{"★".repeat(feedback.rating)}{"☆".repeat(5 - feedback.rating)}</span>
                {feedback.customerName ? <span className="text-ink-soft"> · {feedback.customerName}</span> : null}
              </p>
              {feedback.comment ? <p className="mt-2 text-sm">“{feedback.comment}”</p> : null}
              {feedback.response ? <p className="mt-2 text-sm text-ink-soft">Reply: {feedback.response}</p> : null}
              <Link href="/feedback" className="mt-2 inline-flex text-sm text-brand underline">Open feedback inbox</Link>
            </section>
          ) : null}

          {order.status !== "CANCELLED" && order.status !== "COMPLETED" ? (
            <section className="panel">
              <h2 className="font-display text-lg font-bold text-ink">Cancel Order</h2>
              <p className="mt-1 text-sm text-ink-soft">Provide reason before cancellation.</p>
              <textarea
                className="mt-3 min-h-24 w-full input text-sm"
                value={cancelReason}
                onChange={(event) => setCancelReason(event.target.value)}
                placeholder="Reason for cancellation"
              />
              {cancelFieldError ? <p className="mt-2 text-xs text-danger-on">{cancelFieldError}</p> : null}
              <button
                type="button"
                className="mt-3 btn btn-danger disabled:opacity-60"
                onClick={() => void handleCancel()}
                disabled={busyCancel}
              >
                {busyCancel ? "Cancelling..." : "Cancel Order"}
              </button>
            </section>
          ) : null}
        </>
      ) : null}
    </main>
  );
}


