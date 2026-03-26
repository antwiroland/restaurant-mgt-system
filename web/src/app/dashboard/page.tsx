"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import { getOrders, getTables, type OrderRecord, type TableRecord } from "@/lib/apiClient";

type Snapshot = {
  orders: OrderRecord[];
  tables: TableRecord[];
};

export default function DashboardPage() {
  const { session, loading, authenticatedFetch, logout } = useStaffSession();
  const [snapshot, setSnapshot] = useState<Snapshot>({ orders: [], tables: [] });
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    if (!hasStaffRole(session.user.role, ["ADMIN", "MANAGER"])) return;

    authenticatedFetch(async (activeSession) => {
      const [orders, tables] = await Promise.all([
        getOrders(activeSession),
        getTables(activeSession),
      ]);
      setSnapshot({ orders, tables });
    }).catch((err) => {
      setError(err instanceof Error ? err.message : "Could not load dashboard");
    });
  }, [authenticatedFetch, session]);

  if (loading) {
    return <main className="shell"><section className="panel">Loading dashboard...</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Dashboard</p>
          <h1 className="text-3xl font-semibold">Staff access required</h1>
          <p className="mt-2 text-[#35523d]">Sign in before viewing operations metrics.</p>
          <Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  if (!hasStaffRole(session.user.role, ["ADMIN", "MANAGER"])) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Dashboard</p>
          <h1 className="text-3xl font-semibold">Manager view only</h1>
          <p className="mt-2 text-[#35523d]">Signed in as {session.user.role}. Use the POS view for cashier work.</p>
          <div className="mt-4 flex gap-3">
            <Link className="rounded-full bg-[#132018] px-4 py-2 text-white" href="/pos">Open POS</Link>
            <button className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]" onClick={() => void logout()}>
              Sign Out
            </button>
          </div>
        </section>
      </main>
    );
  }

  const activeOrders = snapshot.orders.filter((order) => !["COMPLETED", "CANCELLED", "VOIDED"].includes(order.status));
  const occupiedTables = snapshot.tables.filter((table) => table.status === "OCCUPIED" || table.status === "RESERVED");
  const grossSales = snapshot.orders
    .filter((order) => ["CONFIRMED", "PREPARING", "READY", "COMPLETED"].includes(order.status))
    .reduce((sum, order) => sum + Number.parseFloat(order.total), 0);

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="kicker">Admin Dashboard</p>
            <h1 className="mt-2 text-3xl font-semibold">Operations Snapshot</h1>
            <p className="mt-2 text-[#35523d]">Signed in as {session.user.name}. This view is backed by live orders and table state.</p>
          </div>
          <button className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]" onClick={() => void logout()}>
            Sign Out
          </button>
        </div>
        {error ? <p className="mt-4 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
      </section>

      <section className="grid gap-3 md:grid-cols-3">
        <article className="panel">
          <p className="kicker">Active Orders</p>
          <p className="mt-3 text-4xl font-semibold">{activeOrders.length}</p>
          <p className="mt-2 text-sm text-[#35523d]">Orders still moving through the kitchen or pickup flow.</p>
        </article>
        <article className="panel">
          <p className="kicker">Occupied Tables</p>
          <p className="mt-3 text-4xl font-semibold">{occupiedTables.length}</p>
          <p className="mt-2 text-sm text-[#35523d]">Tables currently reserved or occupied.</p>
        </article>
        <article className="panel">
          <p className="kicker">Gross Sales</p>
          <p className="mt-3 text-4xl font-semibold">GHS {grossSales.toFixed(2)}</p>
          <p className="mt-2 text-sm text-[#35523d]">Computed from live non-cancelled orders.</p>
        </article>
      </section>

      <section className="grid gap-3 md:grid-cols-3">
        <Link className="panel transition hover:-translate-y-0.5" href="/orders">Active Orders</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/tables">Table Map</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/admin">Admin Panel</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/shifts">Shift Management</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/menu">Menu Management</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/orders/pickup">Pickup Lookup</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/group-ordering">Group Ordering</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/branches">Branch Management</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/users">User Management</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/audit">Audit Log</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/reservations">Reservations</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/payments">Payments</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/financial">Financial Controls</Link>
        <Link className="panel transition hover:-translate-y-0.5" href="/receipts">Receipts</Link>
      </section>
    </main>
  );
}
