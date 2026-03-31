"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import {
  getAnalyticsOverview,
  getBranches,
  getOrders,
  getTables,
  type AnalyticsOverviewRecord,
  type BranchRecord,
  type OrderRecord,
  type TableRecord,
} from "@/lib/apiClient";

type Snapshot = {
  orders: OrderRecord[];
  tables: TableRecord[];
  branches: BranchRecord[];
  analytics: AnalyticsOverviewRecord;
};

function isoDate(daysAgo: number) {
  const value = new Date();
  value.setDate(value.getDate() - daysAgo);
  return value.toISOString().slice(0, 10);
}

const DEFAULT_FROM = isoDate(29);
const DEFAULT_TO = isoDate(0);
const EMPTY_ANALYTICS: AnalyticsOverviewRecord = {
  totalRevenue: "0",
  paidOrderCount: 0,
  averageOrderValue: "0",
  repeatCustomers: 0,
  revenue: { points: [] },
  topItems: [],
  peakHours: [],
  branches: [],
  paymentMethods: [],
  orderTypes: [],
  orderStatuses: [],
};

const CHART_PALETTE = [
  "var(--color-brand)",
  "var(--color-info)",
  "var(--color-warning)",
  "var(--color-accent)",
  "#7c3aed",
  "#0891b2",
];

function formatMoney(value: string | number): string {
  const amount = typeof value === "number" ? value : Number.parseFloat(value);
  return `GHS ${Number.isFinite(amount) ? amount.toFixed(2) : "0.00"}`;
}

function ratioWidth(value: number, total: number) {
  if (total <= 0) return 8;
  return Math.max(8, (value / total) * 100);
}

function BarChart({
  data,
  colorClassName,
  valueFormatter,
  legendLabel,
}: {
  data: { label: string; value: number }[];
  colorClassName: string;
  valueFormatter: (value: number) => string;
  legendLabel: string;
}) {
  const max = Math.max(...data.map((entry) => entry.value), 0);

  if (data.length === 0) {
    return <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No chart data available.</p>;
  }

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-muted/50 px-3 py-2 text-xs text-ink-soft">
        <span className={`h-3 w-3 rounded-full ${colorClassName}`} />
        <span>{legendLabel}</span>
        <span className="ml-auto">Peak {valueFormatter(max)}</span>
      </div>
      {data.map((entry) => (
        <div key={entry.label} className="grid gap-2">
          <div className="flex items-center justify-between gap-3 text-sm">
            <span className="font-medium text-ink">{entry.label}</span>
            <span className="text-ink-soft">{valueFormatter(entry.value)}</span>
          </div>
          <div className="h-3 overflow-hidden rounded-full bg-muted">
            <div className={`h-full rounded-full ${colorClassName}`} style={{ width: `${ratioWidth(entry.value, max)}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function DonutChart({
  data,
  valueFormatter,
  centerLabel = "total",
}: {
  data: { label: string; value: number }[];
  valueFormatter: (value: number) => string;
  centerLabel?: string;
}) {
  const total = data.reduce((sum, entry) => sum + entry.value, 0);
  let accumulated = 0;

  if (data.length === 0 || total === 0) {
    return <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No chart data available.</p>;
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[220px,1fr] lg:items-center">
      <svg viewBox="0 0 42 42" className="mx-auto h-56 w-56 -rotate-90">
        <circle cx="21" cy="21" r="15.9155" fill="transparent" stroke="var(--color-line)" strokeWidth="6" />
        {data.map((entry, index) => {
          const value = (entry.value / total) * 100;
          const segment = (
            <circle
              key={entry.label}
              cx="21"
              cy="21"
              r="15.9155"
              fill="transparent"
              stroke={CHART_PALETTE[index % CHART_PALETTE.length]}
              strokeWidth="6"
              strokeDasharray={`${value} ${100 - value}`}
              strokeDashoffset={-accumulated}
            />
          );
          accumulated += value;
          return segment;
        })}
        <circle cx="21" cy="21" r="9.5" fill="var(--color-surface)" />
        <text x="21" y="20" textAnchor="middle" className="fill-current text-[3.5px] font-semibold text-ink">
          {total}
        </text>
        <text x="21" y="24" textAnchor="middle" className="fill-current text-[2.5px] text-ink-soft">
          {centerLabel}
        </text>
      </svg>

      <div className="grid gap-3">
        {data.map((entry, index) => (
          <div key={entry.label} className="flex items-center justify-between gap-3 rounded-xl border border-line px-4 py-3 text-sm">
            <div className="flex items-center gap-3">
              <span
                className="h-3 w-3 rounded-full"
                style={{ backgroundColor: CHART_PALETTE[index % CHART_PALETTE.length] }}
              />
              <span className="font-medium text-ink">{entry.label}</span>
            </div>
            <span className="text-ink-soft">
              {valueFormatter(entry.value)} · {Math.round((entry.value / total) * 100)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function LineChart({
  data,
  valueFormatter,
  strokeColor = "var(--color-brand)",
}: {
  data: { label: string; value: number }[];
  valueFormatter: (value: number) => string;
  strokeColor?: string;
}) {
  if (data.length === 0) {
    return <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No chart data available.</p>;
  }

  const width = Math.max(640, data.length * 88);
  const height = 240;
  const padding = 24;
  const max = Math.max(...data.map((entry) => entry.value), 0);
  const labelStep = Math.max(1, Math.ceil(data.length / 6));
  const points = data.map((entry, index) => {
    const x = data.length === 1
      ? width / 2
      : padding + (index * (width - padding * 2)) / (data.length - 1);
    const y = max === 0
      ? height - padding
      : height - padding - ((entry.value / max) * (height - padding * 2));
    return { ...entry, x, y };
  });
  const path = points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
  const areaPath = `${path} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`;
  const gridValues = [0.25, 0.5, 0.75, 1].map((factor) => ({
    y: height - padding - factor * (height - padding * 2),
    value: max * factor,
  }));

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-muted/50 px-3 py-2 text-xs text-ink-soft">
        <span className="h-3 w-3 rounded-full" style={{ backgroundColor: strokeColor }} />
        <span>Revenue trend</span>
        <span className="ml-auto">Peak {valueFormatter(max)}</span>
      </div>
      <div className="overflow-x-auto rounded-2xl border border-line bg-[linear-gradient(180deg,rgba(19,32,24,0.05),rgba(255,255,255,0))]">
        <svg viewBox={`0 0 ${width} ${height}`} className="h-72 min-w-[640px] w-full overflow-visible p-2">
          {gridValues.map((grid) => (
            <g key={grid.y}>
              <line x1={padding} y1={grid.y} x2={width - padding} y2={grid.y} stroke="var(--color-line)" strokeDasharray="4 6" />
              <text x={padding - 4} y={grid.y - 4} textAnchor="end" className="fill-current text-[10px] text-ink-muted">
                {valueFormatter(grid.value).replace("GHS ", "")}
              </text>
            </g>
          ))}
          <path d={areaPath} fill="rgba(19,32,24,0.1)" />
          <path d={path} fill="none" stroke={strokeColor} strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
          {points.map((point, index) => (
            <g key={point.label}>
              <circle cx={point.x} cy={point.y} r="5" fill={strokeColor} />
              {index % labelStep === 0 || index === points.length - 1 ? (
                <text x={point.x} y={height - 6} textAnchor="middle" className="fill-current text-[11px] text-ink-soft">
                  {point.label}
                </text>
              ) : null}
            </g>
          ))}
        </svg>
      </div>

      <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
        {data.map((entry) => (
          <div key={entry.label} className="rounded-xl border border-line px-4 py-3 text-sm">
            <p className="font-medium text-ink">{entry.label}</p>
            <p className="mt-1 text-ink-soft">{valueFormatter(entry.value)}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { session, loading, authenticatedFetch, logout } = useStaffSession();
  const [snapshot, setSnapshot] = useState<Snapshot>({
    orders: [],
    tables: [],
    branches: [],
    analytics: EMPTY_ANALYTICS,
  });
  const [fromDate, setFromDate] = useState(DEFAULT_FROM);
  const [toDate, setToDate] = useState(DEFAULT_TO);
  const [selectedBranchId, setSelectedBranchId] = useState("");
  const [period, setPeriod] = useState<"DAY" | "WEEK" | "MONTH">("DAY");
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  async function loadSnapshot() {
    if (!session) return;
    if (!hasStaffRole(session.user.role, ["ADMIN", "MANAGER"])) return;

    const branchId = selectedBranchId || undefined;
    const [orders, tables, branches, analytics] = await authenticatedFetch(async (activeSession) => {
      return Promise.all([
        getOrders(activeSession),
        getTables(activeSession),
        getBranches(activeSession),
        getAnalyticsOverview(activeSession, { from: fromDate, to: toDate, branchId, period }),
      ]);
    });
    setSnapshot({ orders, tables, branches, analytics });
  }

  useEffect(() => {
    loadSnapshot().catch((err) => {
      setError(err instanceof Error ? err.message : "Could not load dashboard");
    });
  }, [session, fromDate, toDate, selectedBranchId, period]);

  const filteredOrders = useMemo(() => {
    if (!selectedBranchId) return snapshot.orders;
    return snapshot.orders.filter((order) => order.branchId === selectedBranchId);
  }, [selectedBranchId, snapshot.orders]);

  const filteredTables = useMemo(() => {
    if (!selectedBranchId) return snapshot.tables;
    return snapshot.tables.filter((table) => table.branchId === selectedBranchId);
  }, [selectedBranchId, snapshot.tables]);

  const activeOrders = useMemo(
    () => filteredOrders.filter((order) => !["COMPLETED", "CANCELLED", "VOIDED"].includes(order.status)),
    [filteredOrders],
  );
  const occupiedTables = useMemo(
    () => filteredTables.filter((table) => table.status === "OCCUPIED" || table.status === "RESERVED"),
    [filteredTables],
  );
  const topRevenueBucket = useMemo(() => {
    return [...snapshot.analytics.revenue.points]
      .sort((left, right) => Number.parseFloat(right.revenue) - Number.parseFloat(left.revenue))[0];
  }, [snapshot.analytics.revenue.points]);
  const busiestHour = useMemo(() => {
    return [...snapshot.analytics.peakHours].sort((left, right) => right.orders - left.orders)[0];
  }, [snapshot.analytics.peakHours]);
  const selectedBranchName = useMemo(() => {
    if (!selectedBranchId) return "All branches";
    return snapshot.branches.find((branch) => branch.id === selectedBranchId)?.name ?? "Selected branch";
  }, [selectedBranchId, snapshot.branches]);
  const revenueChartData = useMemo(() => {
    return snapshot.analytics.revenue.points.map((point) => ({
      label: point.bucket,
      value: Number.parseFloat(point.revenue),
    }));
  }, [snapshot.analytics.revenue.points]);
  const branchRevenueData = useMemo(() => {
    return snapshot.analytics.branches.map((branch) => ({
      label: branch.branchName,
      value: Number.parseFloat(branch.revenue),
    }));
  }, [snapshot.analytics.branches]);
  const paymentMixData = useMemo(() => {
    return snapshot.analytics.paymentMethods.map((entry) => ({
      label: entry.method.replace("_", " "),
      value: Number.parseFloat(entry.revenue),
    }));
  }, [snapshot.analytics.paymentMethods]);
  const orderTypeData = useMemo(() => {
    return snapshot.analytics.orderTypes.map((entry) => ({
      label: entry.type.replace("_", " "),
      value: Number.parseFloat(entry.revenue),
    }));
  }, [snapshot.analytics.orderTypes]);
  const statusDistributionData = useMemo(() => {
    return snapshot.analytics.orderStatuses.map((entry) => ({
      label: entry.status.replaceAll("_", " "),
      value: entry.orderCount,
    }));
  }, [snapshot.analytics.orderStatuses]);
  const peakHourData = useMemo(() => {
    return snapshot.analytics.peakHours.map((entry) => ({
      label: `${String(entry.hour).padStart(2, "0")}:00`,
      value: entry.orders,
    }));
  }, [snapshot.analytics.peakHours]);

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
        <section className="grid gap-4 xl:grid-cols-[1.35fr,1fr]">
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
    { href: "/tables", label: "Tables", note: "Floor status", badge: `${occupiedTables.length}/${filteredTables.length || 0}` },
    { href: "/financial", label: "Financial", note: "Controls and overview", badge: null },
  ];

  return (
    <main className="grid gap-4">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="kicker">Admin Dashboard</p>
            <h1 className="mt-2 text-3xl font-semibold text-ink">Restaurant Analytics</h1>
            <p className="mt-2 text-ink-soft">
              Signed in as {session.user.name}. This view now captures revenue, branch performance, order mix,
              payment mix, status distribution, top sellers, and operational load for {selectedBranchName}.
            </p>
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

        <div className="mt-4 grid gap-3 xl:grid-cols-[1fr,1fr,1fr,1fr,auto]">
          <label className="field">
            <span className="field-label">From</span>
            <input className="input" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} />
          </label>
          <label className="field">
            <span className="field-label">To</span>
            <input className="input" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
          </label>
          <label className="field">
            <span className="field-label">Branch</span>
            <select className="input" value={selectedBranchId} onChange={(event) => setSelectedBranchId(event.target.value)}>
              <option value="">All branches</option>
              {snapshot.branches.map((branch) => (
                <option key={branch.id} value={branch.id}>{branch.name}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">Revenue Period</span>
            <select className="input" value={period} onChange={(event) => setPeriod(event.target.value as "DAY" | "WEEK" | "MONTH")}>
              <option value="DAY">Daily</option>
              <option value="WEEK">Weekly</option>
              <option value="MONTH">Monthly</option>
            </select>
          </label>
          <div className="rounded-xl border border-line bg-muted px-4 py-3 text-sm text-ink-soft">
            Range: {fromDate} to {toDate}
          </div>
        </div>

        {error ? <p className="alert alert-danger mt-4">{error}</p> : null}
      </section>

      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
        <article className="panel">
          <p className="kicker">Revenue</p>
          <p className="mt-3 text-4xl font-semibold text-ink">{formatMoney(snapshot.analytics.totalRevenue)}</p>
          <p className="mt-2 text-sm text-ink-soft">Successful payments in the selected range.</p>
        </article>
        <article className="panel">
          <p className="kicker">Paid Orders</p>
          <p className="mt-3 text-4xl font-semibold text-ink">{snapshot.analytics.paidOrderCount}</p>
          <p className="mt-2 text-sm text-ink-soft">Completed revenue-generating orders.</p>
        </article>
        <article className="panel">
          <p className="kicker">Average Order</p>
          <p className="mt-3 text-4xl font-semibold text-ink">{formatMoney(snapshot.analytics.averageOrderValue)}</p>
          <p className="mt-2 text-sm text-ink-soft">Average revenue per paid order.</p>
        </article>
        <article className="panel">
          <p className="kicker">Repeat Customers</p>
          <p className="mt-3 text-4xl font-semibold text-ink">{snapshot.analytics.repeatCustomers}</p>
          <p className="mt-2 text-sm text-ink-soft">Customers with at least two paid orders in range.</p>
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
              <h2 className="text-xl font-semibold">{period === "DAY" ? "Daily" : period === "WEEK" ? "Weekly" : "Monthly"} Revenue</h2>
            </div>
            <span className="badge badge-info">{snapshot.analytics.revenue.points.length} buckets</span>
          </div>
          <div className="mt-4">
            <LineChart data={revenueChartData} valueFormatter={(value) => formatMoney(value)} />
          </div>
        </article>

        <article className="panel grid gap-4">
          <div>
            <p className="kicker">Top Sellers</p>
            <h2 className="text-xl font-semibold">Best Performing Items</h2>
          </div>
          <div className="grid gap-3">
            {snapshot.analytics.topItems.length === 0 ? (
              <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No top-item analytics are available yet.</p>
            ) : (
              snapshot.analytics.topItems.map((item, index) => (
                <div key={`${item.itemName}-${index}`} className="card p-3">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-ink">{item.itemName}</p>
                      <p className="mt-1 text-sm text-ink-soft">{item.quantitySold} sold</p>
                    </div>
                    <span className="badge badge-info">{formatMoney(item.revenue)}</span>
                  </div>
                </div>
              ))
            )}
          </div>

          <div>
            <p className="kicker">Best Bucket</p>
            <p className="mt-2 text-base font-semibold text-ink">{topRevenueBucket?.bucket ?? "No data"}</p>
            <p className="mt-1 text-sm text-ink-soft">{topRevenueBucket ? formatMoney(topRevenueBucket.revenue) : "No successful payments yet."}</p>
          </div>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        <article className="panel">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="kicker">Branch Performance</p>
              <h2 className="text-xl font-semibold">Revenue by Branch</h2>
            </div>
            <span className="badge badge-info">{snapshot.analytics.branches.length} branches</span>
          </div>
          <div className="mt-4">
            <BarChart
              data={branchRevenueData}
              colorClassName="bg-[linear-gradient(90deg,#0f766e,#14b8a6)]"
              valueFormatter={(value) => formatMoney(value)}
              legendLabel="Branch revenue contribution"
            />
          </div>
          <div className="mt-4 grid gap-3">
            {snapshot.analytics.branches.length === 0 ? (
              <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No branch-attributed revenue for this range.</p>
            ) : (
              snapshot.analytics.branches.map((branch) => (
                <div key={branch.branchId} className="rounded-xl border border-line p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-ink">{branch.branchName}</p>
                      <p className="mt-1 text-sm text-ink-soft">{branch.branchCode}</p>
                    </div>
                    <span className="badge badge-info">{formatMoney(branch.revenue)}</span>
                  </div>
                  <div className="mt-3 grid gap-2 text-sm text-ink-soft md:grid-cols-2">
                    <p>{branch.orderCount} paid orders</p>
                    <p>Average ticket {formatMoney(branch.averageOrderValue)}</p>
                  </div>
                </div>
              ))
            )}
          </div>
        </article>

        <article className="panel grid gap-4">
          <div>
            <p className="kicker">Payment Mix</p>
            <h2 className="text-xl font-semibold">Revenue by Payment Method</h2>
          </div>
          <DonutChart data={paymentMixData} valueFormatter={(value) => formatMoney(value)} centerLabel="revenue" />
          <div className="grid gap-3">
            {snapshot.analytics.paymentMethods.map((entry) => (
              <div key={entry.method} className="flex items-center justify-between rounded-xl border border-line px-4 py-3">
                <div>
                  <p className="font-semibold text-ink">{entry.method.replace("_", " ")}</p>
                  <p className="mt-1 text-sm text-ink-soft">{entry.paymentCount} payments</p>
                </div>
                <span className="badge badge-info">{formatMoney(entry.revenue)}</span>
              </div>
            ))}
          </div>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1fr,1fr,1fr]">
        <article className="panel">
          <p className="kicker">Order Mix</p>
          <h2 className="text-xl font-semibold">Revenue by Order Type</h2>
          <div className="mt-4">
            <BarChart
              data={orderTypeData}
              colorClassName="bg-[linear-gradient(90deg,#1d4ed8,#60a5fa)]"
              valueFormatter={(value) => formatMoney(value)}
              legendLabel="Revenue by order type"
            />
          </div>
          <div className="mt-4 grid gap-3">
            {snapshot.analytics.orderTypes.map((entry) => (
              <div key={entry.type} className="rounded-xl border border-line p-4">
                <div className="flex items-center justify-between gap-3">
                  <p className="font-semibold text-ink">{entry.type.replace("_", " ")}</p>
                  <span className="badge badge-info">{formatMoney(entry.revenue)}</span>
                </div>
                <p className="mt-2 text-sm text-ink-soft">{entry.orderCount} paid orders</p>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <p className="kicker">Status Mix</p>
          <h2 className="text-xl font-semibold">Order Status Distribution</h2>
          <div className="mt-4">
            <DonutChart data={statusDistributionData} valueFormatter={(value) => `${value} orders`} centerLabel="orders" />
          </div>
          <div className="mt-4 grid gap-3">
            {snapshot.analytics.orderStatuses.map((entry) => (
              <div key={entry.status} className="flex items-center justify-between rounded-xl border border-line px-4 py-3">
                <span className="font-medium text-ink">{entry.status.replaceAll("_", " ")}</span>
                <span className="badge badge-info">{entry.orderCount}</span>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <p className="kicker">Peak Hours</p>
          <h2 className="text-xl font-semibold">Hourly Sales Density</h2>
          <div className="mt-4">
            <BarChart
              data={peakHourData}
              colorClassName="bg-[linear-gradient(90deg,#ea580c,#f59e0b)]"
              valueFormatter={(value) => `${value} items`}
              legendLabel="Items sold per hour"
            />
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
