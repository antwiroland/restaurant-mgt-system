"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { cancelOrder, getOrderById, type OrderRecord } from "@/lib/apiClient";

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [order, setOrder] = useState<OrderRecord | null>(null);
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

  if (loading) return <main className="shell"><section className="panel">Loading order...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Order</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="kicker">Order Detail</p>
            <h1 className="text-2xl font-semibold">{order ? `#${order.id.slice(0, 8).toUpperCase()}` : "Order not found"}</h1>
            {order ? (
              <p className="mt-1 text-sm text-ink-soft">
                {order.type} - {order.status} - Created {new Date(order.createdAt).toLocaleString()}
              </p>
            ) : null}
          </div>
          <div className="flex gap-2">
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
            <p><span className="font-semibold text-ink">Subtotal:</span> GHS {order.subtotal}</p>
            <p><span className="font-semibold text-ink">Total:</span> GHS {order.total}</p>
            {order.pickupCode ? <p><span className="font-semibold text-ink">Pickup Code:</span> {order.pickupCode}</p> : null}
            {order.deliveryAddress ? <p><span className="font-semibold text-ink">Delivery Address:</span> {order.deliveryAddress}</p> : null}
            {order.notes ? <p className="md:col-span-2"><span className="font-semibold text-ink">Notes:</span> {order.notes}</p> : null}
          </section>

          <section className="panel">
            <h2 className="text-xl font-semibold">Items</h2>
            <ul className="mt-4 grid gap-2">
              {order.items.map((item) => (
                <li key={item.id} className="input">
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

          {order.status !== "CANCELLED" && order.status !== "COMPLETED" ? (
            <section className="panel">
              <h2 className="text-lg font-semibold">Cancel Order</h2>
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


