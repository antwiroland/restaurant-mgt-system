"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
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
  const [refreshing, setRefreshing] = useState(false);

  async function loadSnapshot() {
    if (!session) return;
    if (!hasStaffRole(session.user.role, ["ADMIN", "MANAGER"])) return;

    const [orders, tables] = await authenticatedFetch(async (activeSession) => {
      return Promise.all([getOrders(activeSession), getTables(activeSession)]);
    });
    setSnapshot({ orders, tables });
  }

  useEffect(() => {
    loadSnapshot().catch((err) => {
      setError(err instanceof Error ? err.message : "Could not load dashboard");
    });
  }, [session]);

  if (loading) {
    return (
      <main className="grid gap-4">
        <section className="panel grid gap-4">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-12 w-full" />
        </section>
        <section className="grid gap-3 md:grid-cols-3">
          <Skeleton className="h-36 w-full" />
          <Skeleton className="h-36 w-full" />
          <Skeleton className="h-36 w-full" />
        </section>
        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 12 }).map((_, index) => (
            <Skeleton key={index} className="h-[120px] w-full" />
          ))}
        </section>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="grid gap-4">
        <section className="panel">
          <p className="kicker">Dashboard</p>
          <h1 className="text-3xl font-semibold text-ink">Staff access required</h1>
          <p className="mt-2 text-ink-soft">Sign in before viewing operations metrics.</p>
          <Link className="btn btn-primary mt-4 inline-flex" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  if (!hasStaffRole(session.user.role, ["ADMIN", "MANAGER"])) {
    return (
      <main className="grid gap-4">
        <section className="panel">
          <p className="kicker">Dashboard</p>
          <h1 className="text-3xl font-semibold text-ink">Manager view only</h1>
          <p className="mt-2 text-ink-soft">Signed in as {session.user.role}. Use the POS view for cashier work.</p>
          <div className="mt-4 flex gap-3">
            <Link className="btn btn-primary" href="/pos">Open POS</Link>
            <button className="btn btn-secondary" onClick={() => void logout()}>
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

  const today = new Date().toLocaleDateString(undefined, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  });

  const operationsTiles = [
    { href: "/pos", label: "POS", note: "Create and charge orders", badge: null },
    { href: "/orders", label: "Orders", note: "Active queue", badge: String(activeOrders.length) },
    { href: "/kds", label: "KDS", note: "Kitchen board", badge: null },
    { href: "/tables", label: "Tables", note: "Floor status", badge: `${occupiedTables.length}/${snapshot.tables.length || 0}` },
    { href: "/reservations", label: "Reservations", note: "Upcoming guests", badge: null },
    { href: "/group-ordering", label: "Group Ordering", note: "Shared sessions", badge: null },
    { href: "/orders/pickup", label: "Pickup", note: "Code lookup", badge: null },
  ];

  const managementTiles = [
    { href: "/menu", label: "Menu", note: "Items and categories" },
    { href: "/payments", label: "Payments", note: "Settlements" },
    { href: "/financial", label: "Financial", note: "Controls and overview" },
    { href: "/receipts", label: "Receipts", note: "Issued receipts" },
    { href: "/shifts", label: "Shifts", note: "Staff schedules" },
  ];

  const adminTiles = [
    { href: "/users", label: "Users", note: "Accounts and roles" },
    { href: "/branches", label: "Branches", note: "Branch settings" },
    { href: "/audit", label: "Audit Log", note: "Trace critical actions" },
    { href: "/admin", label: "Admin Panel", note: "Administrative controls" },
  ];

  return (
    <main className="grid gap-4">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="kicker">Admin Dashboard</p>
            <h1 className="mt-2 text-3xl font-semibold text-ink">Operations Snapshot</h1>
            <p className="mt-1 text-sm text-ink-soft">{today}</p>
            <p className="mt-2 text-ink-soft">Signed in as {session.user.name}. This view is backed by live orders and table state.</p>
          </div>
          <div className="flex gap-2">
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => {
                setRefreshing(true);
                loadSnapshot()
                  .catch((err) => setError(err instanceof Error ? err.message : "Could not refresh dashboard"))
                  .finally(() => setRefreshing(false));
              }}
              disabled={refreshing}
            >
              {refreshing ? <Spinner className="text-current" /> : null}
              Refresh
            </button>
            <button className="btn btn-secondary btn-sm" onClick={() => void logout()}>
              Sign Out
            </button>
          </div>
        </div>
        {error ? <p className="alert alert-danger mt-4">{error}</p> : null}
      </section>

      <section className="grid gap-3 md:grid-cols-3">
        <article className="panel">
          <p className="kicker">Active Orders</p>
          <p className="mt-3 text-4xl font-semibold text-ink">{activeOrders.length}</p>
          <p className="mt-2 text-sm text-ink-soft">Orders still moving through the kitchen or pickup flow.</p>
        </article>
        <article className="panel">
          <p className="kicker">Occupied Tables</p>
          <p className="mt-3 text-4xl font-semibold text-ink">{occupiedTables.length}</p>
          <p className="mt-2 text-sm text-ink-soft">Tables currently reserved or occupied.</p>
        </article>
        <article className="panel">
          <p className="kicker">Gross Sales</p>
          <p className="mt-3 text-4xl font-semibold text-ink">GHS {grossSales.toFixed(2)}</p>
          <p className="mt-2 text-sm text-ink-soft">Computed from live non-cancelled orders.</p>
        </article>
      </section>

      <section className="panel grid gap-5">
        <div className="grid gap-3">
          <p className="kicker">Operations</p>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {operationsTiles.map((tile) => (
              <Link key={tile.href} className="nav-tile" href={tile.href}>
                <div className="flex w-full items-start justify-between gap-2">
                  <span className="rounded-pill bg-brand-subtle px-2 py-1 text-xs font-semibold text-brand">
                    {tile.label.slice(0, 2).toUpperCase()}
                  </span>
                  {tile.badge ? <span className="badge badge-info">{tile.badge}</span> : null}
                </div>
                <p className="text-base font-semibold text-ink">{tile.label}</p>
                <p className="text-sm text-ink-soft">{tile.note}</p>
              </Link>
            ))}
          </div>
        </div>

        <div className="grid gap-3">
          <p className="kicker">Management</p>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {managementTiles.map((tile) => (
              <Link key={tile.href} className="nav-tile" href={tile.href}>
                <span className="rounded-pill bg-brand-subtle px-2 py-1 text-xs font-semibold text-brand">
                  {tile.label.slice(0, 2).toUpperCase()}
                </span>
                <p className="text-base font-semibold text-ink">{tile.label}</p>
                <p className="text-sm text-ink-soft">{tile.note}</p>
              </Link>
            ))}
          </div>
        </div>

        <div className="grid gap-3">
          <p className="kicker">Admin</p>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {adminTiles.map((tile) => (
              <Link key={tile.href} className="nav-tile" href={tile.href}>
                <span className="rounded-pill bg-brand-subtle px-2 py-1 text-xs font-semibold text-brand">
                  {tile.label.slice(0, 2).toUpperCase()}
                </span>
                <p className="text-base font-semibold text-ink">{tile.label}</p>
                <p className="text-sm text-ink-soft">{tile.note}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
