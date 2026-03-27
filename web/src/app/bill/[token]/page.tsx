"use client";

import { Client } from "@stomp/stompjs";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { getPublicTableBill, getPublicTableTracking, type PublicOrderTrackingRecord, type TableBillRecord } from "@/lib/apiClient";

const STATUS_BADGE: Record<PublicOrderTrackingRecord["status"], string> = {
  PENDING: "badge-pending",
  CONFIRMED: "badge-confirmed",
  PREPARING: "badge-preparing",
  READY: "badge-ready",
  COMPLETED: "badge-completed",
  CANCELLED: "badge-cancelled",
  VOIDED: "badge-voided",
};

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
  const [message, setMessage] = useState("");
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

  return (
    <main className="shell-sm max-w-3xl">
      <section className="panel">
        <p className="kicker">Table Bill</p>
        <h1 className="text-2xl font-semibold">Running Receipt</h1>
        <p className={`mt-1 text-xs ${realtimeConnected ? "text-success-on" : "text-warning-on"}`}>
          {realtimeConnected ? "Realtime bill updates connected" : "Realtime updates reconnecting"}
        </p>

        {loading ? (
          <div className="mt-4 grid gap-3">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-28 w-full" />
            <Skeleton className="h-36 w-full" />
          </div>
        ) : null}

        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}

        {bill ? (
          <>
            <article className="mt-4 rounded-xl border border-line bg-surface p-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-lg font-semibold text-ink">Table {bill.tableNumber}</p>
                  <p className="text-sm text-ink-soft">{bill.activeOrderCount} active order(s)</p>
                </div>
                <span className={`badge ${paymentMeta.badge}`}>{paymentMeta.label}</span>
              </div>

              <div className="mt-4 grid gap-2 text-sm">
                <div className="flex items-center justify-between"><span>Total Ordered</span><strong>GHS {bill.totalOrdered}</strong></div>
                <div className="flex items-center justify-between"><span>Total Paid</span><strong>GHS {bill.totalPaid}</strong></div>
                <div className="flex items-center justify-between"><span>Outstanding</span><strong>GHS {bill.outstandingTotal}</strong></div>
              </div>
            </article>

            <article className="mt-4 rounded-xl border border-line bg-surface p-4">
              <h2 className="text-lg font-semibold text-ink">Order Timeline</h2>
              <div className="mt-3 grid gap-2">
                {orders.map((order) => (
                  <article key={order.orderId} className="rounded-lg border border-line p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="font-medium text-ink">#{order.orderId.slice(0, 8).toUpperCase()}</p>
                      <span className={`badge ${STATUS_BADGE[order.status]}`}>{order.status}</span>
                    </div>
                    <p className="mt-1 text-xs text-ink-soft">Updated {new Date(order.updatedAt).toLocaleTimeString()}</p>
                    {order.notes ? <p className="mt-1 text-xs text-ink-soft">Note: {order.notes}</p> : null}
                    {order.cancelReason ? <p className="mt-1 text-xs text-danger-on">Reason: {order.cancelReason}</p> : null}
                  </article>
                ))}
                {orders.length === 0 ? <p className="text-sm text-ink-soft">No table orders yet.</p> : null}
              </div>
            </article>

            <div className="mt-4 grid gap-2 sm:grid-cols-2">
              <button
                type="button"
                className="btn btn-secondary btn-md"
                onClick={() => {
                  setMessage("Service request sent. A staff member will assist you shortly.");
                }}
              >
                Request Service
              </button>
              <Link href={params?.token ? `/scan/${params.token}` : "/"} className="btn btn-primary btn-md">
                Back to Menu
              </Link>
            </div>

            {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
          </>
        ) : null}
      </section>
    </main>
  );
}
