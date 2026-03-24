"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { getOrders, updateOrderStatus, type OrderRecord } from "@/lib/apiClient";

const STATUS_FLOW: OrderRecord["status"][] = ["PENDING", "CONFIRMED", "PREPARING", "READY", "COMPLETED"];

export default function OrdersPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [error, setError] = useState("");
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(getOrders)
      .then(setOrders)
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load orders"));
  }, [authenticatedFetch, session]);

  async function handleStatusChange(orderId: string, status: OrderRecord["status"]) {
    setUpdatingId(orderId);
    try {
      const nextOrder = await authenticatedFetch((activeSession) => updateOrderStatus(activeSession, orderId, status));
      setOrders((current) => current.map((order) => (order.id === orderId ? nextOrder : order)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update order");
    } finally {
      setUpdatingId(null);
    }
  }

  if (loading) {
    return <main className="shell"><section className="panel">Loading orders...</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Orders</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Orders</p>
        <h1 className="text-2xl font-semibold">Active Orders</h1>
        <p className="mt-2 text-[#35523d]">Update kitchen progress directly against the backend order lifecycle.</p>
        {error ? <p className="mt-4 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}

        <ul className="mt-4 grid gap-3">
          {orders.map((order) => (
            <li key={order.id} className="rounded-2xl border border-[#cfe0c8] p-4">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="font-semibold">#{order.id.slice(0, 8)} · {order.type}</p>
                  <p className="mt-1 text-sm text-[#35523d]">
                    {order.items.length} item(s)
                    {order.tableNumber ? ` · Table ${order.tableNumber}` : ""}
                    {order.pickupCode ? ` · Pickup ${order.pickupCode}` : ""}
                  </p>
                  <p className="mt-1 text-sm text-[#35523d]">Total GHS {order.total}</p>
                </div>
                <div className="min-w-[200px]">
                  <label className="grid gap-2 text-sm text-[#35523d]">
                    <span>Status</span>
                    <select
                      className="rounded-xl border border-[#cfe0c8] p-3"
                      value={order.status}
                      disabled={updatingId === order.id || ["CANCELLED", "VOIDED"].includes(order.status)}
                      onChange={(event) => void handleStatusChange(order.id, event.target.value as OrderRecord["status"])}
                    >
                      {[...STATUS_FLOW, "CANCELLED", "VOIDED"].map((status) => (
                        <option key={status} value={status}>{status}</option>
                      ))}
                    </select>
                  </label>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
