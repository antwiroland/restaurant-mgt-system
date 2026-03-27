"use client";

import { useEffect, useMemo, useState } from "react";
import { Client } from "@stomp/stompjs";
import { getPublicOrderStatus, type OrderPublicStatusView } from "@/lib/apiClient";

type OrderStatus = OrderPublicStatusView["status"];

const STATUS_META: Record<OrderStatus, { label: string; card: string; dot: string }> = {
  PENDING: {
    label: "Pending",
    card: "bg-pending-subtle border-pending text-pending",
    dot: "bg-pending",
  },
  CONFIRMED: {
    label: "Confirmed",
    card: "bg-confirmed-subtle border-confirmed text-confirmed",
    dot: "bg-confirmed",
  },
  PREPARING: {
    label: "Preparing",
    card: "bg-preparing-subtle border-preparing text-preparing",
    dot: "bg-preparing",
  },
  READY: {
    label: "Ready",
    card: "bg-ready-subtle border-ready text-ready",
    dot: "bg-ready",
  },
  OUT_FOR_DELIVERY: {
    label: "Out for Delivery",
    card: "bg-info-subtle border-info text-info",
    dot: "bg-info",
  },
  DELIVERED: {
    label: "Delivered",
    card: "bg-success-subtle border-success text-success",
    dot: "bg-success",
  },
};

const DISPLAY_STATUSES: OrderStatus[] = ["PENDING", "CONFIRMED", "PREPARING", "READY"];

const REFRESH_INTERVAL_MS = 30_000;

function elapsedLabel(createdAt: string): string {
  const diffMs = Date.now() - new Date(createdAt).getTime();
  const mins = Math.floor(diffMs / 60_000);
  if (mins < 1) return "just now";
  if (mins === 1) return "1 min ago";
  return `${mins} mins ago`;
}

export default function OrdersStatusPage() {
  const [orders, setOrders] = useState<OrderPublicStatusView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [realtimeConnected, setRealtimeConnected] = useState(false);
  const [, setTick] = useState(0);

  async function load() {
    try {
      const data = await getPublicOrderStatus();
      setOrders(data);
      setLastUpdated(new Date());
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load order status");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    const fetchInterval = setInterval(() => void load(), REFRESH_INTERVAL_MS);
    const tickInterval = setInterval(() => setTick((n) => n + 1), 60_000);

    const wsUrl = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws";
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      onConnect: () => {
        setRealtimeConnected(true);
        client.subscribe("/topic/orders.new", () => {
          void load();
        });
        client.subscribe("/topic/orders.status", () => {
          void load();
        });
      },
      onWebSocketClose: () => setRealtimeConnected(false),
      onStompError: () => setRealtimeConnected(false),
    });
    client.activate();

    return () => {
      clearInterval(fetchInterval);
      clearInterval(tickInterval);
      setRealtimeConnected(false);
      client.deactivate();
    };
  }, []);

  const counts = useMemo(() => {
    const result: Partial<Record<OrderStatus, number>> = {};
    for (const o of orders) {
      result[o.status] = (result[o.status] ?? 0) + 1;
    }
    return result;
  }, [orders]);

  const groups = useMemo(() =>
    DISPLAY_STATUSES.map((status) => ({
      status,
      meta: STATUS_META[status],
      orders: orders.filter((o) => o.status === status),
    })).filter((g) => g.orders.length > 0),
  [orders]);

  return (
    <div className="min-h-screen bg-canvas px-4 py-8">
      <div className="mx-auto max-w-5xl">
        {/* Header */}
        <div className="mb-8 text-center">
          <p className="kicker">Restaurant</p>
          <h1 className="mt-1 text-3xl font-semibold text-ink">Order Status Board</h1>
          <p className="mt-2 text-sm text-ink-soft">Completed and cancelled orders are removed</p>
          <div className="mt-2 flex items-center justify-center gap-1.5">
            <span className={`h-2 w-2 rounded-full ${realtimeConnected ? "bg-success" : "bg-warning"}`} />
            <p className="text-xs text-ink-soft">
              {realtimeConnected ? "Live updates connected" : "Reconnecting\u2026"}
            </p>
          </div>
          {lastUpdated ? (
            <p className="mt-1 text-xs text-ink-soft">
              Last updated: {lastUpdated.toLocaleTimeString()}
            </p>
          ) : null}
        </div>

        {/* Legend / summary */}
        <div className="mb-6 flex flex-wrap justify-center gap-4">
          {DISPLAY_STATUSES.map((status) => {
            const meta = STATUS_META[status];
            const count = counts[status] ?? 0;
            return (
              <div key={status} className="flex items-center gap-2">
                <span className={`h-3 w-3 rounded-full ${meta.dot}`} />
                <span className="text-sm text-ink-soft">
                  {meta.label} <span className="font-semibold text-ink">({count})</span>
                </span>
              </div>
            );
          })}
        </div>

        {error ? (
          <p className="mb-6 alert alert-danger text-center">{error}</p>
        ) : null}

        {loading ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
            {Array.from({ length: 12 }).map((_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl bg-muted" />
            ))}
          </div>
        ) : groups.length === 0 ? (
          <p className="mt-12 text-center text-sm text-ink-soft">No active orders right now.</p>
        ) : (
          groups.map(({ status, meta, orders: groupOrders }) => (
            <section key={status} className="mb-8">
              <h2 className="mb-3 text-xs font-semibold uppercase tracking-widest text-ink-soft">
                {meta.label}
              </h2>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
                {groupOrders.map((order) => (
                  <div
                    key={order.orderId}
                    className={`rounded-xl border p-4 text-center ${meta.card}`}
                  >
                    <p className="text-xl font-bold tracking-wide">
                      #{order.orderId.slice(0, 6).toUpperCase()}
                    </p>
                    {order.tableNumber ? (
                      <p className="mt-1 text-xs opacity-75">Table {order.tableNumber}</p>
                    ) : (
                      <p className="mt-1 text-xs capitalize opacity-75">
                        {order.type.toLowerCase().replace("_", " ")}
                      </p>
                    )}
                    <p className="mt-2 text-xs font-semibold uppercase tracking-wide">
                      {meta.label}
                    </p>
                    <p className="mt-1 text-xs opacity-60">{elapsedLabel(order.createdAt)}</p>
                  </div>
                ))}
              </div>
            </section>
          ))
        )}
      </div>
    </div>
  );
}
