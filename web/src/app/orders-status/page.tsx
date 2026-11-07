"use client";

import { useEffect, useMemo, useState } from "react";
import { Client } from "@stomp/stompjs";
import { getPublicOrderStatus, type OrderPublicStatusView } from "@/lib/apiClient";
import { BrandMark } from "@/components/NavSidebar";
import { useBrand } from "@/components/BrandProvider";


const REFRESH_INTERVAL_MS = 30_000;

function elapsedLabel(createdAt: string): string {
  const diffMs = Date.now() - new Date(createdAt).getTime();
  const mins = Math.floor(diffMs / 60_000);
  if (mins < 1) return "just now";
  if (mins === 1) return "1 min ago";
  return `${mins} mins ago`;
}

export default function OrdersStatusPage() {
  const brand = useBrand();
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

  const preparing = useMemo(
    () => orders.filter((o) => o.status === "PENDING" || o.status === "CONFIRMED" || o.status === "PREPARING").sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
    [orders],
  );
  const ready = useMemo(
    () => orders.filter((o) => o.status === "READY").sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
    [orders],
  );
  const onTheWay = useMemo(() => orders.filter((o) => o.status === "OUT_FOR_DELIVERY"), [orders]);
  const code = (id: string) => id.slice(0, 6).toUpperCase();
  const where = (o: OrderPublicStatusView) => (o.tableNumber ? `Table ${o.tableNumber}` : o.type === "PICKUP" ? "Pickup" : o.type === "DELIVERY" ? "Delivery" : "Counter");

  return (
    <div className="flex min-h-dvh flex-col bg-night text-night-ink">
      <header className="flex items-center justify-between gap-4 border-b border-night-line px-6 py-5 lg:px-10">
        <div className="flex items-center gap-4">
          <BrandMark size={48} />
          <div>
            <p className="font-serif text-2xl font-semibold leading-tight">{brand.name}</p>
            <p className="text-sm text-night-soft">Order status</p>
          </div>
        </div>
        <div className="text-right">
          <p className="font-display text-3xl font-extrabold tabular" suppressHydrationWarning>
            {new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </p>
          <p className="flex items-center justify-end gap-2 text-xs text-night-soft" role="status">
            <span className={`h-2 w-2 rounded-full ${realtimeConnected ? "bg-success" : "animate-pulse bg-warning"}`} aria-hidden="true" />
            {realtimeConnected ? "Live" : "Reconnecting…"}
            {lastUpdated ? ` · ${lastUpdated.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}` : ""}
          </p>
        </div>
      </header>

      {error ? <p className="mx-6 mt-4 rounded-xl bg-danger px-4 py-3 text-center text-white lg:mx-10" role="alert">{error}</p> : null}

      <main className="grid flex-1 gap-4 p-4 lg:grid-cols-[1fr_1.25fr] lg:gap-6 lg:p-8">
        {/* Preparing */}
        <section aria-labelledby="col-prep" className="flex flex-col rounded-3xl border border-night-line bg-night-raised">
          <div className="flex items-center justify-between border-b border-night-line px-6 py-4">
            <h2 id="col-prep" className="flex items-center gap-3 font-display text-xl font-extrabold uppercase tracking-[0.14em]">
              <span className="h-3 w-3 rounded-full bg-brand" aria-hidden="true" /> Being prepared
            </h2>
            <span className="font-display text-2xl font-extrabold tabular text-night-soft">{preparing.length}</span>
          </div>
          <div className="grid flex-1 content-start gap-3 p-5 sm:grid-cols-2" aria-live="polite">
            {loading ? (
              Array.from({ length: 6 }).map((_, i) => <div key={i} className="h-20 animate-pulse rounded-2xl bg-night-card" />)
            ) : preparing.length === 0 ? (
              <p className="col-span-full py-10 text-center text-night-soft">Nothing in the kitchen right now.</p>
            ) : (
              preparing.map((order) => (
                <div key={order.orderId} className="flex items-center justify-between rounded-2xl bg-night-card px-5 py-4">
                  <span className="font-mono text-2xl font-semibold tracking-wider">{code(order.orderId)}</span>
                  <span className="text-right text-sm text-night-soft">
                    <span className="block">{where(order)}</span>
                    <span className="block text-xs">{elapsedLabel(order.createdAt)}</span>
                  </span>
                </div>
              ))
            )}
          </div>
        </section>

        {/* Ready */}
        <section aria-labelledby="col-ready" className="flex flex-col rounded-3xl border border-success/40 bg-night-raised">
          <div className="flex items-center justify-between border-b border-night-line px-6 py-4">
            <h2 id="col-ready" className="flex items-center gap-3 font-display text-xl font-extrabold uppercase tracking-[0.14em]">
              <span className="h-3 w-3 animate-pulse rounded-full bg-success" aria-hidden="true" /> Ready — please collect
            </h2>
            <span className="font-display text-2xl font-extrabold tabular text-night-soft">{ready.length}</span>
          </div>
          <div className="grid flex-1 content-start gap-4 p-5 sm:grid-cols-2 xl:grid-cols-3" aria-live="assertive">
            {loading ? (
              Array.from({ length: 3 }).map((_, i) => <div key={i} className="h-32 animate-pulse rounded-2xl bg-night-card" />)
            ) : ready.length === 0 ? (
              <p className="col-span-full py-10 text-center text-night-soft">Ready orders will appear here.</p>
            ) : (
              ready.map((order) => (
                <div key={order.orderId} className="grid animate-fade-up justify-items-center gap-1 rounded-2xl border border-success/30 bg-night-card px-4 py-6 text-center">
                  <span className="font-mono text-4xl font-bold tracking-wider text-gold lg:text-5xl">{code(order.orderId)}</span>
                  <span className="text-sm font-semibold text-night-soft">{where(order)}</span>
                </div>
              ))
            )}
          </div>
          {onTheWay.length > 0 ? (
            <p className="border-t border-night-line px-6 py-3 text-sm text-night-soft">
              Out for delivery: {onTheWay.map((o) => code(o.orderId)).join(" · ")}
            </p>
          ) : null}
        </section>
      </main>
      <p className="px-6 pb-4 text-center text-xs text-night-soft lg:px-10">Your order number is on your receipt. Completed orders leave the board automatically.</p>
    </div>
  );
}
