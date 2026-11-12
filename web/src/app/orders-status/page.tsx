"use client";

import { useEffect, useMemo, useState } from "react";
import { Client } from "@stomp/stompjs";
import { getPublicOrderStatus, type OrderPublicStatusView } from "@/lib/apiClient";
import { BrandMark } from "@/components/NavSidebar";
import { useBrand } from "@/components/BrandProvider";
import { PICKUP_COLUMNS, groupPickupBoard, type PickupStatus } from "@/features/orders/pickupBoard";

const REFRESH_INTERVAL_MS = 30_000;

const COLUMN_STYLE: Record<PickupStatus, { dot: string; accent: string }> = {
  PENDING: { dot: "bg-gold", accent: "border-t-gold" },
  CONFIRMED: { dot: "bg-info-border", accent: "border-t-info-border" },
  PREPARING: { dot: "bg-brand", accent: "border-t-brand" },
  READY: { dot: "animate-pulse bg-success", accent: "border-t-success" },
};

/** Public pickup board: order numbers grouped by status, no order details. */
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

    const wsUrl = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws";
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      onConnect: () => {
        setRealtimeConnected(true);
        client.subscribe("/topic/orders.new", () => void load());
        client.subscribe("/topic/orders.status", () => void load());
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
            <p className="text-sm text-night-soft">Pickup orders</p>
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

      <main className="grid flex-1 gap-4 p-4 md:grid-cols-2 xl:grid-cols-[1fr_1fr_1fr_1.4fr] lg:gap-5 lg:p-8">
        {PICKUP_COLUMNS.map((column) => {
          const numbers = groups[column.status];
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
                <span className="font-display text-2xl font-extrabold tabular text-night-soft" aria-label={`${numbers.length} orders`}>{numbers.length}</span>
              </div>
              <ul
                className={`grid flex-1 content-start gap-3 p-4 ${ready ? "grid-cols-1 sm:grid-cols-2" : "grid-cols-2"}`}
                aria-live={ready ? "assertive" : "polite"}
              >
                {loading ? (
                  Array.from({ length: 4 }).map((_, i) => <li key={i} className="h-16 animate-pulse rounded-2xl bg-night-card" />)
                ) : numbers.length === 0 ? (
                  <li className="col-span-full py-8 text-center text-night-soft">{column.empty}</li>
                ) : (
                  numbers.map((number) => (
                    <li
                      key={number}
                      className={`grid place-items-center rounded-2xl bg-night-card font-mono font-bold tracking-wider ${
                        ready ? "animate-fade-up border border-success/30 py-6 text-4xl text-gold lg:text-5xl" : "py-4 text-2xl"
                      }`}
                    >
                      <span className="sr-only">Order </span>{number}
                    </li>
                  ))
                )}
              </ul>
            </section>
          );
        })}
      </main>
      <p className="px-6 pb-4 text-center text-xs text-night-soft lg:px-10">
        Your order number is on your receipt. Collected orders leave the board automatically.
      </p>
    </div>
  );
}
