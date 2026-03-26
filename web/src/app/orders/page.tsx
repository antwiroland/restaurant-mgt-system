"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { cancelOrder, getOrders, updateOrderStatus, type OrderRecord } from "@/lib/apiClient";

const STATUS_FLOW: OrderRecord["status"][] = ["PENDING", "CONFIRMED", "PREPARING", "READY", "COMPLETED"];

export default function OrdersPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<OrderRecord["status"] | "ALL">("ALL");
  const [typeFilter, setTypeFilter] = useState<OrderRecord["type"] | "ALL">("ALL");
  const [error, setError] = useState("");
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const filteredOrders = orders.filter((order) => {
    const normalizedQuery = query.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || order.id.toLowerCase().includes(normalizedQuery)
      || (order.tableNumber?.toLowerCase().includes(normalizedQuery) ?? false)
      || (order.pickupCode?.toLowerCase().includes(normalizedQuery) ?? false);
    const matchesStatus = statusFilter === "ALL" || order.status === statusFilter;
    const matchesType = typeFilter === "ALL" || order.type === typeFilter;
    return matchesQuery && matchesStatus && matchesType;
  });

  async function loadOrders() {
    if (!session) return;
    const next = await authenticatedFetch(getOrders);
    setOrders(next.filter((order) => !["COMPLETED", "CANCELLED", "VOIDED"].includes(order.status)));
  }

  useEffect(() => {
    loadOrders().catch((err) => setError(err instanceof Error ? err.message : "Could not load orders"));
  }, [session]);

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

  async function handleCancel(orderId: string) {
    setUpdatingId(orderId);
    try {
      const reason = prompt("Cancel reason (optional)", "") || undefined;
      await authenticatedFetch((activeSession) => cancelOrder(activeSession, orderId, { reason }));
      await loadOrders();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not cancel order");
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
        <p className="mt-2 text-[#35523d]">Update kitchen progress and drill into each order.</p>
        <div className="mt-3 flex gap-2">
          <Link className="rounded-full border border-[#132018] px-3 py-1 text-xs text-[#132018]" href="/orders/pickup">Pickup Lookup</Link>
          <Link className="rounded-full border border-[#132018] px-3 py-1 text-xs text-[#132018]" href="/menu">Menu Management</Link>
        </div>
        <div className="mt-3 grid gap-2 md:grid-cols-3">
          <input className="rounded-xl border border-[#cfe0c8] p-2 text-sm" placeholder="Search order, table, or pickup code" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as OrderRecord["status"] | "ALL")}>
            <option value="ALL">All statuses</option>
            {[...STATUS_FLOW, "CANCELLED", "VOIDED"].map((status) => <option key={status} value={status}>{status}</option>)}
          </select>
          <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as OrderRecord["type"] | "ALL")}>
            <option value="ALL">All types</option>
            <option value="DINE_IN">DINE_IN</option>
            <option value="PICKUP">PICKUP</option>
            <option value="DELIVERY">DELIVERY</option>
          </select>
        </div>
        {error ? <p className="mt-4 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}

        <ul className="mt-4 grid gap-3">
          {filteredOrders.map((order) => (
            <li key={order.id} className="rounded-2xl border border-[#cfe0c8] p-4">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="font-semibold">#{order.id.slice(0, 8)} - {order.type}</p>
                  <p className="mt-1 text-sm text-[#35523d]">
                    {order.items.length} item(s)
                    {order.tableNumber ? ` - Table ${order.tableNumber}` : ""}
                    {order.pickupCode ? ` - Pickup ${order.pickupCode}` : ""}
                  </p>
                  <p className="mt-1 text-sm text-[#35523d]">Total GHS {order.total}</p>
                </div>
                <div className="min-w-[220px]">
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

              <div className="mt-3 flex gap-2">
                <Link className="rounded-full border border-[#132018] px-3 py-1 text-xs text-[#132018]" href={`/orders/${order.id}`}>View Details</Link>
                <button
                  className="rounded-full border border-[#991b1b] px-3 py-1 text-xs text-[#991b1b] disabled:opacity-60"
                  disabled={updatingId === order.id || ["CANCELLED", "VOIDED", "COMPLETED"].includes(order.status)}
                  onClick={() => void handleCancel(order.id)}
                >
                  Cancel
                </button>
              </div>
            </li>
          ))}
          {filteredOrders.length === 0 ? <li className="rounded-xl border border-dashed border-[#cfe0c8] p-3 text-sm text-[#35523d]">No orders match the current filters.</li> : null}
        </ul>
      </section>
    </main>
  );
}
