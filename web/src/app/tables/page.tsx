"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { activeOrdersForTable } from "@/features/tables/tables";
import { getOrders, getTables, type OrderRecord, type TableRecord } from "@/lib/apiClient";

const TABLE_STYLES: Record<TableRecord["status"], string> = {
  AVAILABLE: "bg-[#dcfce7] text-[#166534]",
  OCCUPIED: "bg-[#fee2e2] text-[#991b1b]",
  RESERVED: "bg-[#fef3c7] text-[#92400e]",
  CLOSED: "bg-[#e5e7eb] text-[#374151]",
};

export default function TablesPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(async (activeSession) => {
      const [nextTables, nextOrders] = await Promise.all([
        getTables(activeSession),
        getOrders(activeSession),
      ]);
      setTables(nextTables);
      setOrders(nextOrders);
    }).catch((err) => setError(err instanceof Error ? err.message : "Could not load tables"));
  }, [authenticatedFetch, session]);

  const activeOrderList = useMemo(
    () => orders.filter((order) => !["COMPLETED", "CANCELLED", "VOIDED"].includes(order.status)),
    [orders],
  );

  if (loading) {
    return <main className="shell"><section className="panel">Loading tables...</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Tables</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Tables</p>
        <h1 className="text-2xl font-semibold">Live Table Map</h1>
        <p className="mt-2 text-[#35523d]">Live status with active order references for dine-in service.</p>
        {error ? <p className="mt-4 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}

        <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
          {tables.map((table) => {
            const tableOrders = activeOrdersForTable(table.id, activeOrderList);
            return (
              <article key={table.id} className={`rounded-2xl p-4 ${TABLE_STYLES[table.status]}`}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-lg font-semibold">Table {table.number}</p>
                    <p className="text-sm opacity-80">{table.zone || "Main floor"} · {table.capacity} seats</p>
                  </div>
                  <span className="rounded-full border border-current px-3 py-1 text-xs font-semibold">{table.status}</span>
                </div>
                <p className="mt-4 text-sm">
                  {tableOrders.length > 0 ? `Active orders: ${tableOrders.join(", ")}` : "No active orders"}
                </p>
              </article>
            );
          })}
        </div>
      </section>
    </main>
  );
}
