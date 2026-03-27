"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { CancelOrderModal } from "@/components/CancelOrderModal";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { useStaffSession } from "@/components/SessionProvider";
import { cancelOrder, getOrders, updateOrderStatus, type OrderRecord } from "@/lib/apiClient";

const STATUS_FLOW: OrderRecord["status"][] = ["PENDING", "CONFIRMED", "PREPARING", "READY", "COMPLETED"];
const TYPE_FILTERS: Array<{ value: OrderRecord["type"] | "ALL"; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "DINE_IN", label: "Dine-In" },
  { value: "PICKUP", label: "Pickup" },
  { value: "DELIVERY", label: "Delivery" },
];

function nextStatus(status: OrderRecord["status"]): OrderRecord["status"] | null {
  const index = STATUS_FLOW.indexOf(status);
  if (index < 0 || index >= STATUS_FLOW.length - 1) return null;
  return STATUS_FLOW[index + 1];
}

function statusActionLabel(status: OrderRecord["status"]): string {
  if (status === "PENDING") return "Accept";
  if (status === "CONFIRMED") return "Start Prep";
  if (status === "PREPARING") return "Mark Ready";
  if (status === "READY") return "Complete";
  return "Update";
}

function typeMeta(type: OrderRecord["type"]): { icon: string; label: string } {
  if (type === "DINE_IN") return { icon: "🍽️", label: "Dine-In" };
  if (type === "PICKUP") return { icon: "📦", label: "Pickup" };
  return { icon: "🛵", label: "Delivery" };
}

export default function OrdersPage() {
  const router = useRouter();
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<OrderRecord["type"] | "ALL">("ALL");
  const [error, setError] = useState("");
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const [cancelOrderId, setCancelOrderId] = useState<string | null>(null);

  const filteredOrders = orders.filter((order) => {
    const normalizedQuery = query.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || order.id.toLowerCase().includes(normalizedQuery)
      || (order.tableNumber?.toLowerCase().includes(normalizedQuery) ?? false)
      || (order.pickupCode?.toLowerCase().includes(normalizedQuery) ?? false);
    const matchesType = typeFilter === "ALL" || order.type === typeFilter;
    return matchesQuery && matchesType;
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

  async function handleCancel(orderId: string, reason?: string) {
    setUpdatingId(orderId);
    try {
      await authenticatedFetch((activeSession) => cancelOrder(activeSession, orderId, { reason }));
      await loadOrders();
      setCancelOrderId(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not cancel order");
    } finally {
      setUpdatingId(null);
    }
  }

  if (loading) {
    return (
      <main className="shell">
        <section className="panel grid gap-3">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-12 w-full" />
          {Array.from({ length: 5 }).map((_, index) => (
            <Skeleton key={index} className="h-32 w-full" />
          ))}
        </section>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Orders</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="kicker">Orders</p>
            <h1 className="text-2xl font-semibold">Active Orders</h1>
            <p className="mt-1 text-ink-soft">Tap an order card to open details. Use the primary action to move status.</p>
          </div>
          <div className="flex gap-2">
            <button className="btn btn-secondary btn-sm" onClick={() => void loadOrders()}>
              Refresh
            </button>
            <Link className="btn btn-secondary btn-sm" href="/orders/pickup">Pickup</Link>
          </div>
        </div>

        <div className="mt-4 grid gap-2">
          <div className="-mx-4 overflow-x-auto border-y border-line px-4 py-2">
            <div className="flex min-w-max gap-2">
              {TYPE_FILTERS.map((chip) => (
                <button
                  key={chip.value}
                  type="button"
                  className={`btn btn-sm ${typeFilter === chip.value ? "btn-primary" : "btn-secondary"}`}
                  onClick={() => setTypeFilter(chip.value)}
                >
                  {chip.label}
                </button>
              ))}
            </div>
          </div>

          <input
            className="input text-sm"
            placeholder="Search by order ID, table, or pickup code"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        {error ? <p className="mt-4 alert alert-danger">{error}</p> : null}

        <ul className="mt-4 grid gap-3">
          {filteredOrders.map((order) => {
            const next = nextStatus(order.status);
            const type = typeMeta(order.type);
            return (
              <li
                key={order.id}
                className="card card-interactive"
                role="button"
                tabIndex={0}
                onClick={() => router.push(`/orders/${order.id}`)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    router.push(`/orders/${order.id}`);
                  }
                }}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-base font-semibold text-ink">#{order.id.slice(0, 8)} · {type.icon} {type.label}</p>
                    <p className="mt-1 text-sm text-ink-soft">
                      {order.items.length} items · GHS {order.total}
                      {order.tableNumber ? ` · Table ${order.tableNumber}` : ""}
                      {order.pickupCode ? ` · Pickup ${order.pickupCode}` : ""}
                    </p>
                    <p className="mt-1 text-xs text-ink-soft">{new Date(order.createdAt).toLocaleTimeString()}</p>
                  </div>
                  <span className={`badge badge-${order.status.toLowerCase()}`}>{order.status}</span>
                </div>

                <div className="mt-3 flex flex-wrap gap-2" onClick={(event) => event.stopPropagation()}>
                  {next ? (
                    <button
                      className="btn btn-primary btn-md"
                      disabled={updatingId === order.id}
                      onClick={() => void handleStatusChange(order.id, next)}
                    >
                      {updatingId === order.id ? <Spinner className="text-current" /> : null}
                      {statusActionLabel(order.status)}
                    </button>
                  ) : null}
                  <button
                    className="btn btn-danger btn-md"
                    disabled={updatingId === order.id || ["CANCELLED", "VOIDED", "COMPLETED"].includes(order.status)}
                    onClick={() => setCancelOrderId(order.id)}
                  >
                    Cancel
                  </button>
                  <Link className="btn btn-secondary btn-md" href={`/orders/${order.id}`}>
                    View Details
                  </Link>
                </div>
              </li>
            );
          })}
          {filteredOrders.length === 0 ? (
            <li className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No orders match the current filters.</li>
          ) : null}
        </ul>
      </section>

      <CancelOrderModal
        open={!!cancelOrderId}
        orderLabel={cancelOrderId ? `Order #${cancelOrderId.slice(0, 8)}` : undefined}
        busy={!!cancelOrderId && updatingId === cancelOrderId}
        onClose={() => setCancelOrderId(null)}
        onConfirm={(reason) => {
          if (!cancelOrderId) return;
          void handleCancel(cancelOrderId, reason);
        }}
      />
    </main>
  );
}
