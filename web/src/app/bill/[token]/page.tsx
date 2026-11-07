"use client";

import { Client } from "@stomp/stompjs";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Icon } from "@/components/Icon";
import { BrandMark } from "@/components/NavSidebar";
import { StatusBadge } from "@/components/ui";
import { brand, formatMoney } from "@/lib/brand";
import { getPublicTableBill, getPublicTableTracking, type PublicOrderTrackingRecord, type TableBillRecord } from "@/lib/apiClient";


function toMoney(value: string): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export default function PublicBillPage() {
  const params = useParams<{ token: string }>();
  const [bill, setBill] = useState<TableBillRecord | null>(null);
  const [orders, setOrders] = useState<PublicOrderTrackingRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [realtimeConnected, setRealtimeConnected] = useState(false);

  async function loadData(tableToken: string) {
    const [nextBill, nextOrders] = await Promise.all([
      getPublicTableBill(tableToken),
      getPublicTableTracking(tableToken),
    ]);
    setBill(nextBill);
    setOrders(nextOrders);
  }

  useEffect(() => {
    const token = params?.token;
    if (!token) {
      setError("Invalid table token");
      setLoading(false);
      return;
    }
    setLoading(true);
    loadData(token)
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load table bill"))
      .finally(() => setLoading(false));
  }, [params]);

  useEffect(() => {
    const token = params?.token;
    if (!token) return;
    const wsUrl = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws";
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      onConnect: () => {
        setRealtimeConnected(true);
        client.subscribe(`/topic/public.tables.${token}.orders`, () => {
          loadData(token).catch(() => {});
        });
      },
      onWebSocketClose: () => setRealtimeConnected(false),
      onStompError: () => setRealtimeConnected(false),
    });

    client.activate();
    return () => {
      setRealtimeConnected(false);
      client.deactivate();
    };
  }, [params]);

  const paymentMeta = useMemo(() => {
    if (!bill) return { label: "Unknown", badge: "badge-neutral" };
    const outstanding = toMoney(bill.outstandingTotal);
    const paid = toMoney(bill.totalPaid);
    if (outstanding <= 0) return { label: "Paid", badge: "badge-success" };
    if (paid > 0) return { label: "Partially Paid", badge: "badge-warning" };
    return { label: "Unpaid", badge: "badge-danger" };
  }, [bill]);

  const outstanding = bill ? Number(bill.outstandingTotal) : 0;

  return (
    <main className="min-h-dvh bg-ivory px-4 pb-16 pt-6">
      <div className="mx-auto grid max-w-md gap-4">
        <header className="flex items-center justify-between">
          <Link href="/" className="flex items-center gap-2.5" aria-label={`${brand.name} home`}>
            <BrandMark size={34} />
            <span className="font-serif text-lg font-semibold">{brand.name}</span>
          </Link>
          <span className="flex items-center gap-1.5 text-xs font-semibold text-ink-soft" role="status">
            <span className={`h-2 w-2 rounded-full ${realtimeConnected ? "bg-success" : "animate-pulse bg-warning"}`} aria-hidden="true" />
            {realtimeConnected ? "Live" : "Reconnecting"}
          </span>
        </header>

        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}

        {loading ? (
          <div className="grid gap-3">
            <Skeleton className="h-56 w-full" />
            <Skeleton className="h-40 w-full" />
          </div>
        ) : null}

        {bill ? (
          <>
            {/* Receipt */}
            <section className="relative overflow-hidden rounded-[20px] border border-line bg-surface shadow-card" aria-labelledby="bill-title">
              <div className="bg-espresso px-6 py-6 text-espresso-ink">
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-espresso-soft">Running bill</p>
                <h1 id="bill-title" className="mt-1 font-display text-3xl font-extrabold">Table {bill.tableNumber}</h1>
                <p className="mt-1 text-sm text-espresso-soft">{bill.activeOrderCount} active order{bill.activeOrderCount === 1 ? "" : "s"}</p>
              </div>
              <dl className="grid gap-3 px-6 py-5 text-[15px]">
                <div className="flex justify-between"><dt className="text-ink-soft">Ordered</dt><dd className="money">{formatMoney(bill.totalOrdered)}</dd></div>
                <div className="flex justify-between"><dt className="text-ink-soft">Paid</dt><dd className="money text-success-on">− {formatMoney(bill.totalPaid)}</dd></div>
                <div className="border-t border-dashed border-line-strong pt-3">
                  <div className="flex items-baseline justify-between">
                    <dt className="font-display text-lg font-bold">Left to pay</dt>
                    <dd className={`money font-display text-2xl ${outstanding > 0 ? "text-brand" : "text-success-on"}`}>{formatMoney(bill.outstandingTotal)}</dd>
                  </div>
                </div>
              </dl>
              <div className="flex items-center gap-2 border-t border-line-subtle bg-ivory/70 px-6 py-3 text-sm">
                <span className={`badge ${paymentMeta.badge}`}>{paymentMeta.label}</span>
                <span className="text-ink-soft">{outstanding > 0 ? "Pay with our team when you're ready." : "Thank you!"}</span>
              </div>
            </section>

            {/* Timeline */}
            <section className="rounded-[20px] border border-line bg-surface p-5" aria-labelledby="timeline-title">
              <h2 id="timeline-title" className="font-display text-base font-bold">Orders at this table</h2>
              {orders.length === 0 ? (
                <p className="mt-3 text-sm text-ink-soft">No orders yet.</p>
              ) : (
                <ol className="mt-4 grid gap-4 border-l-2 border-line pl-5">
                  {orders.map((order) => (
                    <li key={order.orderId} className="relative">
                      <span className="absolute -left-[27px] top-1 h-3 w-3 rounded-full border-2 border-surface bg-brand" aria-hidden="true" />
                      <div className="flex items-center justify-between gap-2">
                        <p className="font-mono text-sm font-semibold">#{order.orderId.slice(0, 8).toUpperCase()}</p>
                        <StatusBadge status={order.status} />
                      </div>
                      <p className="mt-0.5 text-xs text-ink-muted">Updated {new Date(order.updatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</p>
                      {order.notes ? <p className="mt-1 text-xs text-ink-soft">Note: {order.notes}</p> : null}
                      {order.cancelReason ? <p className="mt-1 text-xs text-danger-on">Reason: {order.cancelReason}</p> : null}
                    </li>
                  ))}
                </ol>
              )}
            </section>

            <Link href={params?.token ? `/scan/${params.token}` : "/"} className="btn btn-primary btn-lg btn-block">
              <Icon name="plus" size={18} /> Order more
            </Link>
            <p className="text-center text-xs text-ink-muted">Need anything? Just wave to a member of our team.</p>
          </>
        ) : null}
      </div>
    </main>
  );
}
