"use client";

import { createRealtimeClient } from "@/lib/realtime";
import { useEffect, useMemo, useState } from "react";
import { getPublicOrderStatus, type OrderPublicStatusView } from "@/lib/apiClient";
import { BrandMark } from "@/components/NavSidebar";
import { useBrand } from "@/components/BrandProvider";
import { PICKUP_COLUMNS, TYPE_LABEL, groupPickupBoard, type PickupStatus } from "@/features/orders/pickupBoard";

const REFRESH_INTERVAL_MS = 30_000;

const COLUMN_STYLE: Record<PickupStatus, { dot: string; accent: string }> = {
  CONFIRMED: { dot: "bg-info-border", accent: "border-t-info-border" },
  PREPARING: { dot: "bg-brand", accent: "border-t-brand" },
  READY: { dot: "animate-pulse bg-success", accent: "border-t-success" },
};

/** Public order board: pickup and delivery order numbers grouped by status, no order details. */
export default function OrdersStatusPage() {
  const brand = useBrand();
  const [orders, setOrders] = useState<OrderPublicStatusView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [realtimeConnected, setRealtimeConnected] = useState(false);
  const [, setTick] = useState(0);

  async function load() {
    try {
      setOrders(await getPublicOrderStatus());
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
    const clockInterval = setInterval(() => setTick((n) => n + 1), 30_000);

    const client = createRealtimeClient({
      onConnect: () => {
        setRealtimeConnected(true);
        client.subscribe("/topic/public.pickup-board", () => void load());
      },
      onWebSocketClose: () => setRealtimeConnected(false),
      onStompError: () => setRealtimeConnected(false),
    });
    client.activate();

    return () => {
      clearInterval(fetchInterval);
      clearInterval(clockInterval);
      setRealtimeConnected(false);
      client.deactivate();
    };
  }, []);

  const groups = useMemo(() => groupPickupBoard(orders), [orders]);

  return (
    <div className="flex min-h-dvh flex-col bg-night text-night-ink">
      <header className="flex items-center justify-between gap-4 border-b border-night-line px-6 py-5 lg:px-10">
        <div className="flex items-center gap-4">
          <BrandMark size={48} />
          <div>
            <p className="font-serif text-2xl font-semibold leading-tight">{brand.name}</p>
            <p className="text-sm text-night-soft">Pickup &amp; delivery orders</p>
          </div>
        </div>
        <div className="text-right">
          <p className="font-display text-3xl font-extrabold tabular" suppressHydrationWarning>
            {new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </p>
          <p className="flex items-center justify-end gap-2 text-xs text-night-soft" role="status">
            <span className={`h-2 w-2 rounded-full ${realtimeConnected ? "bg-success" : "animate-pulse bg-warning"}`} aria-hidden="true" />
            {realtimeConnected ? "Live" : "Reconnecting…"}
          </p>
        </div>
      </header>

      {error ? <p className="mx-6 mt-4 rounded-xl bg-danger px-4 py-3 text-center text-white lg:mx-10" role="alert">{error}</p> : null}

      <main className="grid flex-1 gap-4 p-4 md:grid-cols-[1fr_1fr_1.4fr] lg:gap-5 lg:p-8">
        {PICKUP_COLUMNS.map((column) => {
          const entries = groups[column.status];
          const ready = column.status === "READY";
          return (
            <section
              key={column.status}
              aria-labelledby={`col-${column.status}`}
              className={`flex flex-col rounded-3xl border border-t-4 bg-night-raised ${COLUMN_STYLE[column.status].accent} ${ready ? "border-success/40" : "border-night-line"}`}
            >
              <div className="flex items-center justify-between border-b border-night-line px-5 py-4">
                <h2 id={`col-${column.status}`} className="flex items-center gap-3 font-display text-lg font-extrabold uppercase tracking-[0.12em]">
                  <span className={`h-3 w-3 rounded-full ${COLUMN_STYLE[column.status].dot}`} aria-hidden="true" /> {column.label}
                </h2>
                <span className="font-display text-2xl font-extrabold tabular text-night-soft" aria-label={`${entries.length} orders`}>{entries.length}</span>
              </div>
              <ul
                className={`grid flex-1 content-start gap-3 p-4 ${ready ? "grid-cols-1 sm:grid-cols-2" : "grid-cols-2"}`}
                aria-live={ready ? "assertive" : "polite"}
              >
                {loading ? (
                  Array.from({ length: 4 }).map((_, i) => <li key={i} className="h-16 animate-pulse rounded-2xl bg-night-card" />)
                ) : entries.length === 0 ? (
                  <li className="col-span-full py-8 text-center text-night-soft">{column.empty}</li>
                ) : (
                  entries.map(({ number, type }) => (
                    <li
                      key={number}
                      className={`grid place-items-center gap-1 rounded-2xl bg-night-card ${
                        ready ? "animate-fade-up border border-success/30 py-5" : "py-3"
                      }`}
                    >
                      <span className={`font-mono font-bold tracking-wider ${ready ? "text-4xl text-gold lg:text-5xl" : "text-2xl"}`}>
                        <span className="sr-only">Order </span>{number}
                      </span>
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-[0.12em] ${
                          type === "DELIVERY" ? "bg-info-subtle text-info-on" : "bg-night-line text-night-ink"
                        }`}
                      >
                        {ready ? (type === "DELIVERY" ? "Delivery · waiting for rider" : "Pickup · collect now") : TYPE_LABEL[type]}
                      </span>
                    </li>
                  ))
                )}
              </ul>
            </section>
          );
        })}
      </main>
      <p className="px-6 pb-4 text-center text-xs text-night-soft lg:px-10">
        Your order number is on your receipt. Pickup orders leave the board once collected, delivery orders once the rider has them.
      </p>
    </div>
  );
}
