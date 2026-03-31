"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import {
  getAnalyticsAverageOrderValue,
  getAnalyticsPeakHours,
  getAnalyticsRevenue,
  getAnalyticsTopItems,
  getOrders,
  getTables,
  type AnalyticsAverageOrderValueRecord,
  type AnalyticsPeakHourRecord,
  type AnalyticsRevenueRecord,
  type AnalyticsTopItemRecord,
  type OrderRecord,
  type TableRecord,
} from "@/lib/apiClient";

type Snapshot = {
  orders: OrderRecord[];
  tables: TableRecord[];
  revenue: AnalyticsRevenueRecord;
  topItems: AnalyticsTopItemRecord[];
  peakHours: AnalyticsPeakHourRecord[];
  averageOrderValue: AnalyticsAverageOrderValueRecord;
};

function isoDate(daysAgo: number) {
  const value = new Date();
  value.setDate(value.getDate() - daysAgo);
  return value.toISOString().slice(0, 10);
}

const DEFAULT_FROM = isoDate(29);
const DEFAULT_TO = isoDate(0);

export default function DashboardPage() {
  const { session, loading, authenticatedFetch, logout } = useStaffSession();
  const [snapshot, setSnapshot] = useState<Snapshot>({
    orders: [],
    tables: [],
    revenue: { points: [] },
    topItems: [],
    peakHours: [],
    averageOrderValue: { averageOrderValue: "0", orderCount: 0 },
  });
  const [fromDate, setFromDate] = useState(DEFAULT_FROM);
  const [toDate, setToDate] = useState(DEFAULT_TO);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  async function loadSnapshot() {
    if (!session) return;
    if (!hasStaffRole(session.user.role, ["ADMIN", "MANAGER"])) return;

    const [orders, tables, revenue, topItems, peakHours, averageOrderValue] = await authenticatedFetch(async (activeSession) => {
      return Promise.all([
        getOrders(activeSession),
        getTables(activeSession),
        getAnalyticsRevenue(activeSession, { from: fromDate, to: toDate, period: "DAY" }),
        getAnalyticsTopItems(activeSession, { from: fromDate, to: toDate, limit: 5 }),
        getAnalyticsPeakHours(activeSession, { from: fromDate, to: toDate }),
        getAnalyticsAverageOrderValue(activeSession, { from: fromDate, to: toDate }),
      ]);
    });
    setSnapshot({ orders, tables, revenue, topItems, peakHours, averageOrderValue });
  }

  useEffect(() => {
    loadSnapshot().catch((err) => {
      setError(err instanceof Error ? err.message : "Could not load dashboard");
    });
  }, [session, fromDate, toDate]);

  const activeOrders = useMemo(
    () => snapshot.orders.filter((order) => !["COMPLETED", "CANCELLED", "VOIDED"].includes(order.status)),
    [snapshot.orders],
  );
  const occupiedTables = useMemo(
    () => snapshot.tables.filter((table) => table.status === "OCCUPIED" || table.status === "RESERVED"),
    [snapshot.tables],
  );
  const totalRevenue = useMemo(
    () => snapshot.revenue.points.reduce((sum, point) => sum + Number.parseFloat(point.revenue), 0),
    [snapshot.revenue.points],
  );
  const topRevenueDay = useMemo(() => {
    return [...snapshot.revenue.points]
      .sort((left, right) => Number.parseFloat(right.revenue) - Number.parseFloat(left.revenue))[0];
  }, [snapshot.revenue.points]);
  const busiestHour = useMemo(() => {
    return [...snapshot.peakHours].sort((left, right) => right.orders - left.orders)[0];
  }, [snapshot.peakHours]);

  if (loading) {
    return (
      <main className="grid gap-4">
        <section className="panel grid gap-4">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-12 w-full" />
        </section>
        <section className="grid gap-3 md:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-36 w-full" />
          ))}
        </section>
        <section className="grid gap-4 xl:grid-cols-[1.4fr,1fr]">
          <Skeleton className="h-[320px] w-full" />
          <Skeleton className="h-[320px] w-full" />
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

  const operationsTiles = [
    { href: "/pos", label: "POS", note: "Create and charge orders", badge: null },
    { href: "/orders", label: "Orders", note: "Active queue", badge: String(activeOrders.length) },
    { href: "/kds", label: "KDS", note: "Kitchen board", badge: null },
    { href: "/tables", label: "Tables", note: "Floor status", badge: `${occupiedTables.length}/${snapshot.tables.length || 0}` },
    { href: "/financial", label: "Financial", note: "Controls and overview", badge: null },
  ];

  return (
    <main className="grid gap-4">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="kicker">Admin Dashboard</p>
            <h1 className="mt-2 text-3xl font-semibold text-ink">Analytics and Operations</h1>
            <p className="mt-2 text-ink-soft">Signed in as {session.user.name}. Revenue widgets now use backend analytics endpoints.</p>
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

        <div className="mt-4 grid gap-3 md:grid-cols-[1fr,1fr,auto]">
          <label className="field">
            <span className="field-label">From</span>
            <input className="input" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} />
          </label>
          <label className="field">
            <span className="field-label">To</span>
            <input className="input" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
          </label>
          <div className="rounded-xl border border-line bg-muted px-4 py-3 text-sm text-ink-soft">
            Current range: {fromDate} to {toDate}
          </div>
        </div>

        {error ? <p className="alert alert-danger mt-4">{error}</p> : null}
      </section>

      <section className="grid gap-3 md:grid-cols-4">
        <article className="panel">
          <p className="kicker">Revenue</p>
          <p className="mt-3 text-4xl font-semibold text-ink">GHS {totalRevenue.toFixed(2)}</p>
          <p className="mt-2 text-sm text-ink-soft">Summed from `/phase10/analytics/revenue`.</p>
        </article>
        <article className="panel">
          <p className="kicker">Average Order</p>
          <p className="mt-3 text-4xl font-semibold text-ink">GHS {Number.parseFloat(snapshot.averageOrderValue.averageOrderValue).toFixed(2)}</p>
          <p className="mt-2 text-sm text-ink-soft">{snapshot.averageOrderValue.orderCount} successful paid orders in range.</p>
        </article>
        <article className="panel">
          <p className="kicker">Best Day</p>
          <p className="mt-3 text-2xl font-semibold text-ink">{topRevenueDay?.bucket ?? "No data"}</p>
          <p className="mt-2 text-sm text-ink-soft">{topRevenueDay ? `GHS ${Number.parseFloat(topRevenueDay.revenue).toFixed(2)}` : "No successful payments yet."}</p>
        </article>
        <article className="panel">
          <p className="kicker">Peak Hour</p>
          <p className="mt-3 text-2xl font-semibold text-ink">{busiestHour ? `${String(busiestHour.hour).padStart(2, "0")}:00` : "No data"}</p>
          <p className="mt-2 text-sm text-ink-soft">{busiestHour ? `${busiestHour.orders} items sold in the busiest hour.` : "No hourly traffic yet."}</p>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.35fr,1fr]">
        <article className="panel">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="kicker">Revenue Timeline</p>
              <h2 className="text-xl font-semibold">Daily Revenue</h2>
            </div>
            <span className="badge badge-info">{snapshot.revenue.points.length} buckets</span>
          </div>
          <div className="mt-4 grid gap-3">
            {snapshot.revenue.points.length === 0 ? (
              <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No paid-order analytics are available for this date range.</p>
            ) : (
              snapshot.revenue.points.map((point) => {
                const amount = Number.parseFloat(point.revenue);
                const width = totalRevenue > 0 ? Math.max(8, (amount / totalRevenue) * 100) : 8;
                return (
                  <div key={point.bucket} className="grid gap-2">
                    <div className="flex items-center justify-between gap-3 text-sm">
                      <span className="font-medium text-ink">{point.bucket}</span>
                      <span className="text-ink-soft">GHS {amount.toFixed(2)}</span>
                    </div>
                    <div className="h-3 overflow-hidden rounded-full bg-muted">
                      <div className="h-full rounded-full bg-[linear-gradient(90deg,var(--brand),var(--accent))]" style={{ width: `${width}%` }} />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </article>

        <article className="panel grid gap-4">
          <div>
            <p className="kicker">Top Sellers</p>
            <h2 className="text-xl font-semibold">Best Performing Items</h2>
          </div>
          <div className="grid gap-3">
            {snapshot.topItems.length === 0 ? (
              <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No top-item analytics are available yet.</p>
            ) : (
              snapshot.topItems.map((item, index) => (
                <div key={`${item.itemName}-${index}`} className="card p-3">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-ink">{item.itemName}</p>
                      <p className="mt-1 text-sm text-ink-soft">{item.quantitySold} sold</p>
                    </div>
                    <span className="badge badge-info">GHS {Number.parseFloat(item.revenue).toFixed(2)}</span>
                  </div>
                </div>
              ))
            )}
          </div>

          <div>
            <p className="kicker">Peak Hours</p>
            <div className="mt-3 grid gap-2">
              {snapshot.peakHours.length === 0 ? (
                <p className="text-sm text-ink-soft">No peak-hour data available.</p>
              ) : (
                snapshot.peakHours.map((entry) => (
                  <div key={entry.hour} className="flex items-center justify-between rounded-xl border border-line px-3 py-2 text-sm">
                    <span className="text-ink">{String(entry.hour).padStart(2, "0")}:00</span>
                    <span className="text-ink-soft">{entry.orders} items</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </article>
      </section>

      <section className="panel grid gap-5">
        <div className="grid gap-3">
          <p className="kicker">Operations</p>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
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
      </section>
    </main>
  );
}
