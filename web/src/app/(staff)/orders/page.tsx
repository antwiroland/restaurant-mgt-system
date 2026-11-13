"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { CancelOrderModal } from "@/components/CancelOrderModal";
import { Icon, type IconName } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { EmptyState, ErrorState, PageHeader, StatusBadge } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { cancelOrder, getOrders, updateOrderStatus, type OrderRecord } from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";

const STATUS_FLOW: OrderRecord["status"][] = ["PENDING", "CONFIRMED", "PREPARING", "READY", "COMPLETED"];
const CLOSED: OrderRecord["status"][] = ["COMPLETED", "CANCELLED", "VOIDED", "DELIVERED"];

const TYPE_FILTERS: Array<{ value: OrderRecord["type"] | "ALL"; label: string; icon?: IconName }> = [
  { value: "ALL", label: "All channels" },
  { value: "DINE_IN", label: "Dine in", icon: "table" },
  { value: "PICKUP", label: "Pickup", icon: "bag" },
  { value: "DELIVERY", label: "Delivery", icon: "scooter" },
];

type StatusTab = "ACTIVE" | "PENDING" | "KITCHEN" | "READY" | "DONE" | "CANCELLED" | "ALL";

const STATUS_TABS: Array<{ value: StatusTab; label: string; match: (s: OrderRecord["status"]) => boolean }> = [
  { value: "ACTIVE", label: "Active", match: (s) => !CLOSED.includes(s) },
  { value: "PENDING", label: "New", match: (s) => s === "PENDING" },
  { value: "KITCHEN", label: "In kitchen", match: (s) => s === "CONFIRMED" || s === "PREPARING" },
  { value: "READY", label: "Ready", match: (s) => s === "READY" || s === "OUT_FOR_DELIVERY" },
  { value: "DONE", label: "Completed", match: (s) => s === "COMPLETED" || s === "DELIVERED" },
  { value: "CANCELLED", label: "Cancelled", match: (s) => s === "CANCELLED" || s === "VOIDED" },
  { value: "ALL", label: "All", match: () => true },
];

function nextStatus(status: OrderRecord["status"]): OrderRecord["status"] | null {
  const index = STATUS_FLOW.indexOf(status);
  if (index < 0 || index >= STATUS_FLOW.length - 1) return null;
  return STATUS_FLOW[index + 1];
}

function statusActionLabel(status: OrderRecord["status"]): string {
  if (status === "PENDING") return "Accept";
  if (status === "CONFIRMED") return "Start prep";
  if (status === "PREPARING") return "Mark ready";
  if (status === "READY") return "Complete";
  return "Update";
}

function typeMeta(type: OrderRecord["type"]): { icon: IconName; label: string } {
  if (type === "DINE_IN") return { icon: "table", label: "Dine in" };
  if (type === "PICKUP") return { icon: "bag", label: "Pickup" };
  return { icon: "scooter", label: "Delivery" };
}

function localDate(iso: string) {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function minutesAgo(iso: string) {
  const mins = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000));
  if (mins < 60) return `${mins}m ago`;
  if (mins < 60 * 24) return `${Math.floor(mins / 60)}h ago`;
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

export default function OrdersPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<OrderRecord["type"] | "ALL">("ALL");
  const [statusTab, setStatusTab] = useState<StatusTab>("ACTIVE");
  const [dateFilter, setDateFilter] = useState("");
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const [cancelOrderId, setCancelOrderId] = useState<string | null>(null);

  const scoped = useMemo(() => {
    const q = query.trim().toLowerCase();
    return orders.filter((order) => {
      const matchesQuery =
        !q ||
        order.id.toLowerCase().includes(q) ||
        (order.tableNumber?.toLowerCase().includes(q) ?? false) ||
        (order.pickupCode?.toLowerCase().includes(q) ?? false) ||
        order.items.some((item) => item.name.toLowerCase().includes(q));
      const matchesType = typeFilter === "ALL" || order.type === typeFilter;
      const matchesDate = !dateFilter || localDate(order.createdAt) === dateFilter;
      return matchesQuery && matchesType && matchesDate;
    });
  }, [orders, query, typeFilter, dateFilter]);

  const counts = useMemo(() => {
    const map = {} as Record<StatusTab, number>;
    for (const tab of STATUS_TABS) map[tab.value] = scoped.filter((order) => tab.match(order.status)).length;
    return map;
  }, [scoped]);

  const filteredOrders = useMemo(() => {
    const tab = STATUS_TABS.find((t) => t.value === statusTab) ?? STATUS_TABS[0];
    return scoped
      .filter((order) => tab.match(order.status))
      .sort((a, b) => (statusTab === "ACTIVE" ? a.createdAt.localeCompare(b.createdAt) : b.createdAt.localeCompare(a.createdAt)));
  }, [scoped, statusTab]);

  async function loadOrders() {
    if (!session) return;
    const next = await authenticatedFetch(getOrders);
    setOrders(next);
    setLoaded(true);
  }

  useEffect(() => {
    loadOrders().catch((err) => {
      setError(err instanceof Error ? err.message : "Could not load orders");
      setLoaded(true);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  async function refresh() {
    setRefreshing(true);
    setError("");
    try {
      await loadOrders();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load orders");
    } finally {
      setRefreshing(false);
    }
  }

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

  if (loading || !session) {
    return (
      <main className="shell">
        <Skeleton className="h-14 w-64" />
        <Skeleton className="h-12 w-full" />
        {Array.from({ length: 5 }).map((_, index) => <Skeleton key={index} className="h-16 w-full" />)}
      </main>
    );
  }

  const actions = (order: OrderRecord, size: "sm" | "md" = "sm") => {
    const next = nextStatus(order.status);
    const busy = updatingId === order.id;
    const cancellable = !["CANCELLED", "VOIDED", "COMPLETED", "DELIVERED"].includes(order.status);
    return (
      <div className={`flex items-center justify-end gap-2 ${size === "sm" ? "flex-nowrap whitespace-nowrap" : "flex-wrap"}`}>
        {order.type === "PICKUP" && order.status === "READY" ? (
          // Handing over needs the customer's pickup code, entered on the pickup counter page.
          <Link className={`btn btn-primary btn-${size}`} href="/orders/pickup">Hand over</Link>
        ) : next ? (
          <button className={`btn btn-primary btn-${size}`} disabled={busy} onClick={() => void handleStatusChange(order.id, next)}>
            {busy ? <Spinner className="text-current" size={16} /> : null}
            {statusActionLabel(order.status)}
          </button>
        ) : null}
        {cancellable ? (
          <button className={`btn btn-ghost btn-${size} text-danger-on hover:bg-danger-subtle`} disabled={busy} onClick={() => setCancelOrderId(order.id)}>
            Cancel
          </button>
        ) : null}
      </div>
    );
  };

  return (
    <main className="shell">
      <PageHeader
        eyebrow="Service"
        title="Orders"
        description="Every order across dine-in, pickup and delivery. Move orders forward or open one for the full timeline."
        actions={
          <>
            <Link className="btn btn-secondary btn-sm" href="/orders/pickup"><Icon name="bag" size={16} /> Pickup lookup</Link>
            <button className="btn btn-secondary btn-sm" onClick={() => void refresh()} disabled={refreshing}>
              {refreshing ? <Spinner size={16} /> : <Icon name="refresh" size={16} />} Refresh
            </button>
            <Link className="btn btn-primary btn-sm" href="/pos"><Icon name="plus" size={16} /> New order</Link>
          </>
        }
      />

      <section className="overflow-hidden rounded-panel border border-line bg-surface">
        {/* Status tabs */}
        <div className="overflow-x-auto border-b border-line [scrollbar-width:none]">
          <div className="flex min-w-max px-2" role="tablist" aria-label="Order status">
            {STATUS_TABS.map((tab) => (
              <button
                key={tab.value}
                type="button"
                role="tab"
                aria-selected={statusTab === tab.value}
                onClick={() => setStatusTab(tab.value)}
                className={`relative flex min-h-[52px] items-center gap-2 px-4 text-sm font-semibold transition-colors ${
                  statusTab === tab.value ? "text-ink" : "text-ink-muted hover:text-ink"
                }`}
              >
                {tab.label}
                <span className={`rounded-pill px-2 py-0.5 text-xs tabular ${statusTab === tab.value ? "bg-espresso text-espresso-ink" : "bg-muted text-ink-soft"}`}>
                  {counts[tab.value] ?? 0}
                </span>
                {statusTab === tab.value ? <span className="absolute inset-x-3 bottom-0 h-[3px] rounded-t-full bg-brand" aria-hidden="true" /> : null}
              </button>
            ))}
          </div>
        </div>

        {/* Filters */}
        <div className="flex flex-col gap-3 border-b border-line-subtle p-3 md:flex-row md:items-center">
          <label className="relative block flex-1">
            <span className="sr-only">Search orders</span>
            <Icon name="search" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input className="input pl-10" placeholder="Order ID, table, pickup code or dish" value={query} onChange={(e) => setQuery(e.target.value)} />
          </label>
          <div className="flex flex-wrap items-center gap-2">
            <div className="segmented" role="group" aria-label="Channel">
              {TYPE_FILTERS.map((chip) => (
                <button key={chip.value} type="button" aria-pressed={typeFilter === chip.value} onClick={() => setTypeFilter(chip.value)}>
                  {chip.icon ? <Icon name={chip.icon} size={16} /> : null}
                  <span className={chip.icon ? "hidden sm:inline" : ""}>{chip.label}</span>
                </button>
              ))}
            </div>
            <input className="input min-h-[44px] w-auto py-2" type="date" value={dateFilter} onChange={(e) => setDateFilter(e.target.value)} aria-label="Filter by date" />
            {dateFilter || query || typeFilter !== "ALL" ? (
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setDateFilter(""); setQuery(""); setTypeFilter("ALL"); }}>
                Clear
              </button>
            ) : null}
          </div>
        </div>

        {error ? <div className="p-3"><ErrorState message={error} onRetry={() => void refresh()} /></div> : null}

        {!loaded ? (
          <div className="grid gap-2 p-3">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-14" />)}</div>
        ) : filteredOrders.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon="receipt"
              title={orders.length === 0 ? "No orders yet" : "Nothing here"}
              description={orders.length === 0 ? "Orders from the POS, table QR codes and the app appear here as they come in." : "No orders match this status and filter."}
              action={orders.length === 0 ? <Link href="/pos" className="btn btn-primary btn-sm">Open POS</Link> : undefined}
            />
          </div>
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden overflow-x-auto lg:block">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Channel</th>
                    <th>Items</th>
                    <th>Status</th>
                    <th className="num">Total</th>
                    <th className="text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredOrders.map((order) => {
                    const type = typeMeta(order.type);
                    return (
                      <tr key={order.id}>
                        <td>
                          <Link href={`/orders/${order.id}`} className="font-mono text-sm font-semibold text-ink hover:text-brand hover:underline">
                            #{order.id.slice(0, 8).toUpperCase()}
                          </Link>
                          <p className="text-xs text-ink-muted">{minutesAgo(order.createdAt)}</p>
                        </td>
                        <td>
                          <span className="flex items-center gap-2 text-sm">
                            <Icon name={type.icon} size={16} className="text-ink-muted" />
                            {order.type === "DINE_IN" ? `Table ${order.tableNumber ?? "—"}` : type.label}
                            {order.pickupCode ? <span className="badge badge-neutral font-mono normal-case">{order.pickupCode}</span> : null}
                          </span>
                        </td>
                        <td className="max-w-[280px]">
                          <p className="truncate text-sm text-ink-soft">{order.items.map((item) => `${item.quantity}× ${item.name}`).join(", ")}</p>
                        </td>
                        <td><StatusBadge status={order.status} /></td>
                        <td className="num whitespace-nowrap font-semibold">{formatMoney(order.total)}</td>
                        <td className="w-px">{actions(order)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Mobile / tablet cards */}
            <ul className="grid gap-px bg-line-subtle lg:hidden">
              {filteredOrders.map((order) => {
                const type = typeMeta(order.type);
                return (
                  <li key={order.id} className="bg-surface p-4">
                    <div className="flex items-start justify-between gap-3">
                      <Link href={`/orders/${order.id}`} className="min-w-0">
                        <p className="flex items-center gap-2 font-semibold text-ink">
                          <Icon name={type.icon} size={16} className="text-ink-muted" />
                          {order.type === "DINE_IN" ? `Table ${order.tableNumber ?? "—"}` : type.label}
                          <span className="font-mono text-xs font-normal text-ink-muted">#{order.id.slice(0, 8).toUpperCase()}</span>
                        </p>
                        <p className="mt-1 line-clamp-2 text-sm text-ink-soft">{order.items.map((item) => `${item.quantity}× ${item.name}`).join(", ")}</p>
                        <p className="mt-1 text-xs text-ink-muted">{minutesAgo(order.createdAt)}{order.pickupCode ? ` · Pickup ${order.pickupCode}` : ""}</p>
                      </Link>
                      <div className="grid justify-items-end gap-1.5">
                        <StatusBadge status={order.status} />
                        <span className="money text-sm">{formatMoney(order.total)}</span>
                      </div>
                    </div>
                    <div className="mt-3">{actions(order, "md")}</div>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </section>

      <CancelOrderModal
        open={!!cancelOrderId}
        orderLabel={cancelOrderId ? `Order #${cancelOrderId.slice(0, 8).toUpperCase()}` : undefined}
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
