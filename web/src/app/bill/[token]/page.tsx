"use client";

import { Client } from "@stomp/stompjs";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { getPublicTableBill, getPublicTableTracking, type PublicOrderTrackingRecord, type TableBillRecord } from "@/lib/apiClient";

const STATUS_STYLES: Record<PublicOrderTrackingRecord["status"], string> = {
  PENDING: "bg-[#fff7ed] text-[#9a3412]",
  CONFIRMED: "bg-[#eff6ff] text-[#1d4ed8]",
  PREPARING: "bg-[#fef3c7] text-[#92400e]",
  READY: "bg-[#dcfce7] text-[#166534]",
  COMPLETED: "bg-[#e0f2fe] text-[#075985]",
  CANCELLED: "bg-[#fee2e2] text-[#991b1b]",
  VOIDED: "bg-[#e5e7eb] text-[#374151]",
};

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

  return (
    <main className="shell">
      <section className="panel mx-auto max-w-2xl">
        <p className="kicker">Table Bill</p>
        <h1 className="text-2xl font-semibold">Running Bill</h1>
        <p className={`mt-2 text-xs ${realtimeConnected ? "text-[#166534]" : "text-[#9a3412]"}`}>
          {realtimeConnected ? "Realtime tracking connected" : "Realtime tracking reconnecting"}
        </p>
        {loading ? <p className="mt-3 text-sm text-[#35523d]">Loading...</p> : null}
        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        {bill ? (
          <div className="mt-4 rounded-xl border border-[#cfe0c8] p-4 text-sm">
            <p><span className="font-semibold">Table:</span> {bill.tableNumber}</p>
            <p><span className="font-semibold">Status:</span> {bill.tableStatus}</p>
            <p><span className="font-semibold">Active Orders:</span> {bill.activeOrderCount}</p>
            <p><span className="font-semibold">Total Ordered:</span> GHS {bill.totalOrdered}</p>
            <p><span className="font-semibold">Total Paid:</span> GHS {bill.totalPaid}</p>
            <p><span className="font-semibold">Outstanding:</span> GHS {bill.outstandingTotal}</p>
          </div>
        ) : null}

        <div className="mt-4 rounded-xl border border-[#cfe0c8] p-4">
          <h2 className="text-lg font-semibold">Order Tracking</h2>
          <div className="mt-3 grid gap-2">
            {orders.map((order) => (
              <article key={order.orderId} className="rounded-xl border border-[#d9e7d2] p-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="font-medium">#{order.orderId.slice(0, 8).toUpperCase()}</p>
                  <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_STYLES[order.status]}`}>
                    {order.status}
                  </span>
                </div>
                <p className="mt-1 text-xs text-[#35523d]">Updated {new Date(order.updatedAt).toLocaleTimeString()}</p>
                {order.notes ? <p className="mt-1 text-xs text-[#35523d]">Note: {order.notes}</p> : null}
                {order.cancelReason ? <p className="mt-1 text-xs text-[#991b1b]">Reason: {order.cancelReason}</p> : null}
              </article>
            ))}
            {orders.length === 0 ? <p className="text-sm text-[#35523d]">No table orders yet.</p> : null}
          </div>
        </div>

        <div className="mt-4">
          <Link href={params?.token ? `/scan/${params.token}` : "/"} className="rounded-full border border-[#132018] px-3 py-1 text-xs text-[#132018]">Back to Menu</Link>
        </div>
      </section>
    </main>
  );
}
