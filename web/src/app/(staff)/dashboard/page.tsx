"use client";

import Link from "next/link";
import { Icon } from "@/components/Icon";
import { PageHeader, StatCard, StatusBadge, humanize } from "@/components/ui";
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
  "var(--color-chart-1)",
  "var(--color-chart-2)",
  "var(--color-chart-3)",
  "var(--color-chart-4)",
  "var(--color-chart-5)",
];
const CHART_OTHER = "var(--color-chart-other)";
const MAX_SLICES = CHART_PALETTE.length;

function formatMoney(value: string | number): string {
  const amount = typeof value === "number" ? value : Number.parseFloat(value);
  return `GHS ${(Number.isFinite(amount) ? amount : 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function compactMoney(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}k`;
  return value.toFixed(0);
}

function EmptyChart({ label = "No data for this period yet." }: { label?: string }) {
  return <p className="grid min-h-[120px] place-items-center rounded-xl border border-dashed border-line-strong p-4 text-center text-sm text-ink-soft">{label}</p>;
}

/** Fold anything past the palette length into "Other" so hues are never cycled. */
function foldOther(data: { label: string; value: number }[]) {
  const sorted = [...data].sort((a, b) => b.value - a.value);
  if (sorted.length <= MAX_SLICES) return sorted;
  const head = sorted.slice(0, MAX_SLICES - 1);
  const rest = sorted.slice(MAX_SLICES - 1).reduce((sum, entry) => sum + entry.value, 0);
  return [...head, { label: "Other", value: rest }];
}

/** Single-hue horizontal bars (magnitude). Values are always printed as text. */
function BarChart({
  data,
  valueFormatter,
  label,
}: {
  data: { label: string; value: number }[];
  valueFormatter: (value: number) => string;
  label: string;
}) {
  const max = Math.max(...data.map((entry) => entry.value), 0);
  if (data.length === 0 || max === 0) return <EmptyChart />;
  return (
    <ul className="grid gap-3" aria-label={label}>
      {data.map((entry) => (
        <li key={entry.label} className="group grid gap-1.5" title={`${entry.label}: ${valueFormatter(entry.value)}`}>
          <div className="flex items-baseline justify-between gap-3 text-sm">
            <span className="font-semibold text-ink">{entry.label}</span>
            <span className="money text-ink-soft">{valueFormatter(entry.value)}</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-[color:var(--color-chart-1)] transition-[width,opacity] duration-500 group-hover:opacity-80"
              style={{ width: `${Math.max(2, (entry.value / max) * 100)}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}

/** Vertical column chart for ordered buckets (e.g. hours). Hover shows value. */
function ColumnChart({
  data,
  valueFormatter,
  label,
}: {
  data: { label: string; value: number }[];
  valueFormatter: (value: number) => string;
  label: string;
}) {
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(...data.map((entry) => entry.value), 0);
  if (data.length === 0 || max === 0) return <EmptyChart />;
  return (
    <div className="grid gap-2" role="img" aria-label={label}>
      <div className="relative flex h-40 items-end gap-[2px]">
        {data.map((entry, index) => (
          <div
            key={entry.label}
            className="relative flex h-full flex-1 items-end"
            onMouseEnter={() => setHover(index)}
            onMouseLeave={() => setHover(null)}
          >
            <div
              className={`w-full rounded-t-[4px] transition-opacity ${hover === null || hover === index ? "opacity-100" : "opacity-45"}`}
              style={{ height: `${Math.max(2, (entry.value / max) * 100)}%`, background: "var(--color-chart-1)" }}
            />
            {hover === index ? (
              <div className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-2 -translate-x-1/2 whitespace-nowrap rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs shadow-lg">
                <p className="font-semibold text-ink">{entry.label}</p>
                <p className="text-ink-soft">{valueFormatter(entry.value)}</p>
              </div>
            ) : null}
          </div>
        ))}
      </div>
      <div className="flex justify-between text-[11px] text-ink-muted">
        <span>{data[0]?.label}</span>
        <span>{data[data.length - 1]?.label}</span>
      </div>
    </div>
  );
}

function DonutChart({
  data: raw,
  valueFormatter,
  centerLabel = "total",
  centerValue,
}: {
  data: { label: string; value: number }[];
  valueFormatter: (value: number) => string;
  centerLabel?: string;
  centerValue?: string;
}) {
  const data = foldOther(raw.filter((entry) => entry.value > 0));
  const total = data.reduce((sum, entry) => sum + entry.value, 0);
  const [hover, setHover] = useState<number | null>(null);
  if (data.length === 0 || total === 0) return <EmptyChart />;
  const segments = data.map((entry, index) => ({
    entry,
    index,
    value: (entry.value / total) * 100,
    offset: data.slice(0, index).reduce((sum, prev) => sum + (prev.value / total) * 100, 0),
  }));
  const gap = data.length > 1 ? 0.8 : 0;
  const colorAt = (index: number, entry: { label: string }) => (entry.label === "Other" ? CHART_OTHER : CHART_PALETTE[index]);

  return (
    <div className="grid items-center gap-5 sm:grid-cols-[180px_1fr]">
      <div className="relative mx-auto h-44 w-44">
        <svg viewBox="0 0 42 42" className="h-full w-full -rotate-90" aria-hidden="true">
          <circle cx="21" cy="21" r="15.9155" fill="transparent" stroke="var(--color-muted)" strokeWidth="5" />
          {segments.map(({ entry, index, value, offset }) => (
              <circle
                key={entry.label}
                cx="21"
                cy="21"
                r="15.9155"
                fill="transparent"
                stroke={colorAt(index, entry)}
                strokeWidth={hover === index ? 6.5 : 5}
                strokeDasharray={`${Math.max(0, value - gap)} ${100 - Math.max(0, value - gap)}`}
                strokeDashoffset={-offset}
                className="cursor-pointer transition-[stroke-width] duration-150"
                onMouseEnter={() => setHover(index)}
                onMouseLeave={() => setHover(null)}
              />
          ))}
        </svg>
        <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
          <div>
            <p className="font-display text-lg font-extrabold leading-tight text-ink tabular">
              {hover !== null ? `${Math.round((data[hover].value / total) * 100)}%` : centerValue ?? total}
            </p>
            <p className="text-[11px] text-ink-muted">{hover !== null ? data[hover].label : centerLabel}</p>
          </div>
        </div>
      </div>
      <ul className="grid gap-1.5">
        {data.map((entry, index) => (
          <li
            key={entry.label}
            className={`flex items-center justify-between gap-3 rounded-lg px-2.5 py-2 text-sm transition-colors ${hover === index ? "bg-sunken" : ""}`}
            onMouseEnter={() => setHover(index)}
            onMouseLeave={() => setHover(null)}
          >
            <span className="flex items-center gap-2.5">
              <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: colorAt(index, entry) }} aria-hidden="true" />
              <span className="font-semibold text-ink">{entry.label}</span>
            </span>
            <span className="money text-ink-soft">
              {valueFormatter(entry.value)} <span className="text-ink-muted">· {Math.round((entry.value / total) * 100)}%</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function LineChart({
  data,
  valueFormatter,
}: {
  data: { label: string; value: number }[];
  valueFormatter: (value: number) => string;
}) {
  const [hover, setHover] = useState<number | null>(null);
  if (data.length === 0) return <EmptyChart />;

  const width = 720;
  const height = 260;
  const padX = 44;
  const padTop = 16;
  const padBottom = 28;
  const max = Math.max(...data.map((entry) => entry.value), 0) || 1;
  const niceMax = Math.ceil(max / 4) * 4 || 4;
  const labelStep = Math.max(1, Math.ceil(data.length / 7));
  const xAt = (index: number) => (data.length === 1 ? width / 2 : padX + (index * (width - padX * 2)) / (data.length - 1));
  const yAt = (value: number) => height - padBottom - (value / niceMax) * (height - padTop - padBottom);
  const points = data.map((entry, index) => ({ ...entry, x: xAt(index), y: yAt(entry.value) }));
  const path = points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
  const areaPath = `${path} L ${points[points.length - 1].x} ${height - padBottom} L ${points[0].x} ${height - padBottom} Z`;
  const grid = [0, 0.25, 0.5, 0.75, 1].map((f) => ({ y: yAt(niceMax * f), value: niceMax * f }));

  function onMove(event: React.MouseEvent<SVGSVGElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * width;
    let nearest = 0;
    for (let i = 1; i < points.length; i += 1) if (Math.abs(points[i].x - x) < Math.abs(points[nearest].x - x)) nearest = i;
    setHover(nearest);
  }

  const active = hover !== null ? points[hover] : null;

  return (
    <div className="relative">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="h-64 w-full overflow-visible"
        role="img"
        aria-label={`Revenue trend, peak ${valueFormatter(max)}`}
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <defs>
          <linearGradient id="rev-area" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-chart-1)" stopOpacity="0.18" />
            <stop offset="100%" stopColor="var(--color-chart-1)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {grid.map((line) => (
          <g key={line.y}>
            <line x1={padX} y1={line.y} x2={width - padX} y2={line.y} stroke="var(--color-line-subtle)" />
            <text x={padX - 8} y={line.y + 3} textAnchor="end" className="fill-[color:var(--color-ink-muted)] text-[10px]">
              {compactMoney(line.value)}
            </text>
          </g>
        ))}
        <path d={areaPath} fill="url(#rev-area)" />
        <path d={path} fill="none" stroke="var(--color-chart-1)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        {points.map((point, index) =>
          index % labelStep === 0 || index === points.length - 1 ? (
            <text key={`l-${point.label}`} x={point.x} y={height - 8} textAnchor="middle" className="fill-[color:var(--color-ink-muted)] text-[10px]">
              {point.label}
            </text>
          ) : null,
        )}
        {active ? (
          <g>
            <line x1={active.x} y1={padTop} x2={active.x} y2={height - padBottom} stroke="var(--color-line-strong)" strokeDasharray="3 3" />
            <circle cx={active.x} cy={active.y} r="5" fill="var(--color-chart-1)" stroke="var(--color-surface)" strokeWidth="2" />
          </g>
        ) : null}
      </svg>
      {active ? (
        <div
          className="pointer-events-none absolute top-2 z-10 -translate-x-1/2 whitespace-nowrap rounded-lg border border-line bg-surface px-3 py-2 text-xs shadow-lg"
          style={{ left: `${(active.x / width) * 100}%` }}
        >
          <p className="font-semibold text-ink">{active.label}</p>
          <p className="money text-ink-soft">{valueFormatter(active.value)}</p>
        </div>
      ) : null}
      <details className="mt-2 text-sm">
        <summary className="cursor-pointer select-none text-xs font-semibold text-ink-soft hover:text-ink">View as table</summary>
        <div className="mt-2 max-h-56 overflow-auto rounded-lg border border-line">
          <table className="data-table">
            <thead><tr><th>Period</th><th className="num">Revenue</th></tr></thead>
            <tbody>
              {data.map((entry) => (
                <tr key={entry.label}><td>{entry.label}</td><td className="num">{valueFormatter(entry.value)}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}

export default function DashboardPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
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

  if (loading || !session || !hasStaffRole(session.user.role, ["ADMIN", "MANAGER"])) {
    // AppShell handles sign-in and role gating; render the page skeleton meanwhile.
    return (
      <main className="shell">
        <Skeleton className="h-14 w-72" />
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => <Skeleton key={index} className="h-32 w-full" />)}
        </section>
        <section className="grid gap-4 xl:grid-cols-[1.6fr_1fr]">
          <Skeleton className="h-[340px] w-full" />
          <Skeleton className="h-[340px] w-full" />
        </section>
      </main>
    );
  }

  const pendingOrders = activeOrders.filter((order) => order.status === "PENDING").length;
  const inKitchen = activeOrders.filter((order) => order.status === "CONFIRMED" || order.status === "PREPARING").length;
  const readyOrders = activeOrders.filter((order) => order.status === "READY").length;
  const recentOrders = [...filteredOrders].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 6);
  const topMax = Math.max(...snapshot.analytics.topItems.map((item) => Number(item.revenue)), 0);
  const greeting = (() => {
    const hour = new Date().getHours();
    return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  })();

  const liveTiles = [
    { href: "/orders", label: "Awaiting payment / accept", value: pendingOrders, icon: "receipt" as const, tone: "text-pending bg-pending-subtle" },
    { href: "/kds", label: "In the kitchen", value: inKitchen, icon: "chef" as const, tone: "text-preparing bg-preparing-subtle" },
    { href: "/kds", label: "Ready to serve", value: readyOrders, icon: "checkCircle" as const, tone: "text-ready bg-ready-subtle" },
    { href: "/tables", label: "Tables in use", value: `${occupiedTables.length}/${filteredTables.length}`, icon: "table" as const, tone: "text-info-on bg-info-subtle" },
  ];

  return (
    <main className="shell">
      <PageHeader
        eyebrow={new Date().toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" })}
        title={`${greeting}, ${session.user.name.split(" ")[0]}`}
        description={`Performance for ${selectedBranchName.toLowerCase() === "all branches" ? "all branches" : selectedBranchName}, ${fromDate} → ${toDate}.`}
        actions={
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
            {refreshing ? <Spinner className="text-current" /> : <Icon name="refresh" size={16} />}
            Refresh
          </button>
        }
      />

      {/* Filters — one row above all charts */}
      <section className="flex flex-col gap-3 rounded-panel border border-line bg-surface p-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap items-center gap-2">
          <div className="segmented" role="group" aria-label="Quick range">
            {[
              { label: "7 days", days: 6 },
              { label: "30 days", days: 29 },
              { label: "90 days", days: 89 },
            ].map((range) => (
              <button
                key={range.label}
                type="button"
                aria-pressed={fromDate === isoDate(range.days) && toDate === isoDate(0)}
                onClick={() => { setFromDate(isoDate(range.days)); setToDate(isoDate(0)); }}
              >
                {range.label}
              </button>
            ))}
          </div>
          <label className="flex items-center gap-2 text-sm">
            <span className="sr-only">From</span>
            <input className="input min-h-[40px] w-auto py-1.5" type="date" value={fromDate} max={toDate} onChange={(event) => setFromDate(event.target.value)} aria-label="From date" />
          </label>
          <span className="text-ink-muted">→</span>
          <label className="flex items-center gap-2 text-sm">
            <span className="sr-only">To</span>
            <input className="input min-h-[40px] w-auto py-1.5" type="date" value={toDate} min={fromDate} onChange={(event) => setToDate(event.target.value)} aria-label="To date" />
          </label>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {snapshot.branches.length > 1 ? (
            <select className="select min-h-[40px] w-auto py-1.5" value={selectedBranchId} onChange={(event) => setSelectedBranchId(event.target.value)} aria-label="Branch">
              <option value="">All branches</option>
              {snapshot.branches.map((branch) => (
                <option key={branch.id} value={branch.id}>{branch.name}</option>
              ))}
            </select>
          ) : null}
          <div className="segmented" role="group" aria-label="Revenue grouping">
            {(["DAY", "WEEK", "MONTH"] as const).map((value) => (
              <button key={value} type="button" aria-pressed={period === value} onClick={() => setPeriod(value)}>
                {value === "DAY" ? "Daily" : value === "WEEK" ? "Weekly" : "Monthly"}
              </button>
            ))}
          </div>
        </div>
      </section>

      {error ? <p className="alert alert-danger" role="alert"><Icon name="alert" size={18} className="mt-0.5 shrink-0" />{error}</p> : null}

      {/* Right now */}
      <section aria-labelledby="live-title" className="grid gap-3">
        <h2 id="live-title" className="flex items-center gap-2 text-sm font-bold text-ink">
          <span className="relative flex h-2.5 w-2.5"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-50" /><span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-success" /></span>
          Right now
        </h2>
        <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
          {liveTiles.map((tile) => (
            <Link key={tile.label} href={tile.href} className="group flex items-center gap-3 rounded-card border border-line bg-surface p-4 transition-[border-color,box-shadow] hover:border-line-strong hover:shadow-card">
              <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-xl ${tile.tone}`}><Icon name={tile.icon} size={22} /></span>
              <span className="min-w-0">
                <span className="block font-display text-2xl font-extrabold leading-none tabular text-ink">{tile.value}</span>
                <span className="mt-1 block truncate text-xs font-semibold text-ink-soft">{tile.label}</span>
              </span>
              <Icon name="chevronRight" size={18} className="ml-auto shrink-0 text-ink-muted transition-transform group-hover:translate-x-0.5" />
            </Link>
          ))}
        </div>
      </section>

      {/* KPIs */}
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Key figures">
        <StatCard label="Revenue" value={formatMoney(snapshot.analytics.totalRevenue)} meta="Successful payments in range" icon="trend" tone="brand" />
        <StatCard label="Paid orders" value={snapshot.analytics.paidOrderCount.toLocaleString()} meta="Revenue-generating orders" icon="receipt" tone="info" />
        <StatCard label="Average order" value={formatMoney(snapshot.analytics.averageOrderValue)} meta="Revenue per paid order" icon="tag" tone="gold" />
        <StatCard
          label="Repeat customers"
          value={snapshot.analytics.repeatCustomers.toLocaleString()}
          meta={busiestHour ? `Busiest hour ${String(busiestHour.hour).padStart(2, "0")}:00` : "Two or more paid orders"}
          icon="users"
          tone="sage"
        />
      </section>

      {/* Revenue + top sellers */}
      <section className="grid gap-4 xl:grid-cols-[1.6fr_1fr]">
        <article className="panel">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="heading-md">Revenue</h2>
              <p className="text-sm text-ink-soft">{period === "DAY" ? "Daily" : period === "WEEK" ? "Weekly" : "Monthly"} · {selectedBranchName}</p>
            </div>
            {topRevenueBucket ? (
              <div className="text-right">
                <p className="text-xs font-semibold text-ink-muted">Best period</p>
                <p className="text-sm font-bold text-ink">{topRevenueBucket.bucket} · <span className="money">{formatMoney(topRevenueBucket.revenue)}</span></p>
              </div>
            ) : null}
          </div>
          <div className="mt-5">
            <LineChart data={revenueChartData} valueFormatter={(value) => formatMoney(value)} />
          </div>
        </article>

        <article className="panel">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="heading-md">Best sellers</h2>
              <p className="text-sm text-ink-soft">By revenue in range</p>
            </div>
            <Link href="/menu" className="btn btn-ghost btn-sm">Menu <Icon name="arrowRight" size={14} /></Link>
          </div>
          {snapshot.analytics.topItems.length === 0 ? (
            <div className="mt-5"><EmptyChart label="Best sellers appear after the first paid orders." /></div>
          ) : (
            <ol className="mt-5 grid gap-4">
              {snapshot.analytics.topItems.map((item, index) => (
                <li key={`${item.itemName}-${index}`} className="grid grid-cols-[28px_1fr] items-start gap-3">
                  <span className={`grid h-7 w-7 place-items-center rounded-lg font-display text-sm font-extrabold ${index === 0 ? "bg-accent-subtle text-accent-hover" : "bg-muted text-ink-soft"}`}>{index + 1}</span>
                  <div className="grid gap-1.5">
                    <div className="flex items-baseline justify-between gap-3">
                      <p className="truncate font-semibold text-ink">{item.itemName}</p>
                      <p className="money shrink-0 text-sm text-ink">{formatMoney(item.revenue)}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                        <div className="h-full rounded-full bg-[color:var(--color-chart-1)]" style={{ width: `${topMax ? (Number(item.revenue) / topMax) * 100 : 0}%` }} />
                      </div>
                      <span className="shrink-0 text-xs text-ink-muted">{item.quantitySold} sold</span>
                    </div>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </article>
      </section>

      {/* Mix */}
      <section className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        <article className="panel">
          <h2 className="heading-md">Payment methods</h2>
          <p className="text-sm text-ink-soft">Share of revenue</p>
          <div className="mt-5">
            <DonutChart
              data={paymentMixData}
              valueFormatter={(value) => formatMoney(value)}
              centerLabel="revenue"
              centerValue={compactMoney(paymentMixData.reduce((sum, entry) => sum + entry.value, 0))}
            />
          </div>
        </article>
        <article className="panel">
          <h2 className="heading-md">Order channels</h2>
          <p className="text-sm text-ink-soft">Revenue by service type</p>
          <div className="mt-5">
            <BarChart data={orderTypeData} valueFormatter={(value) => formatMoney(value)} label="Revenue by order type" />
          </div>
        </article>
        <article className="panel lg:col-span-2 xl:col-span-1">
          <h2 className="heading-md">Busiest hours</h2>
          <p className="text-sm text-ink-soft">Items sold per hour of day</p>
          <div className="mt-5">
            <ColumnChart data={peakHourData} valueFormatter={(value) => `${value} items`} label="Items sold per hour" />
          </div>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1fr_1.4fr]">
        <article className="panel">
          <h2 className="heading-md">Order outcomes</h2>
          <p className="text-sm text-ink-soft">Orders by status in range</p>
          <div className="mt-5">
            <DonutChart data={statusDistributionData} valueFormatter={(value) => `${value}`} centerLabel="orders" />
          </div>
        </article>

        <article className="panel">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="heading-md">Recent orders</h2>
              <p className="text-sm text-ink-soft">Latest activity across channels</p>
            </div>
            <Link href="/orders" className="btn btn-ghost btn-sm">All orders <Icon name="arrowRight" size={14} /></Link>
          </div>
          {recentOrders.length === 0 ? (
            <div className="mt-5"><EmptyChart label="No orders yet." /></div>
          ) : (
            <ul className="mt-4 divide-y divide-line-subtle">
              {recentOrders.map((order) => (
                <li key={order.id}>
                  <Link href={`/orders/${order.id}`} className="-mx-2 flex items-center gap-3 rounded-lg px-2 py-3 hover:bg-ivory">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-muted text-ink-soft">
                      <Icon name={order.type === "DINE_IN" ? "table" : order.type === "PICKUP" ? "bag" : "scooter"} size={18} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-ink">
                        <span className="font-mono">#{order.id.slice(0, 8).toUpperCase()}</span>
                        <span className="font-normal text-ink-soft"> · {order.type === "DINE_IN" ? `Table ${order.tableNumber ?? "—"}` : humanize(order.type)} · {order.items.reduce((n, i) => n + i.quantity, 0)} items</span>
                      </p>
                      <p className="text-xs text-ink-muted">{new Date(order.createdAt).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}</p>
                    </div>
                    <StatusBadge status={order.status} className="hidden sm:inline-flex" />
                    <span className="money w-28 shrink-0 text-right text-sm text-ink">{formatMoney(order.total)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>

      {snapshot.analytics.branches.length > 1 ? (
        <section className="panel">
          <h2 className="heading-md">Branch performance</h2>
          <div className="mt-4 overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Branch</th><th className="num">Paid orders</th><th className="num">Average ticket</th><th className="num">Revenue</th></tr></thead>
              <tbody>
                {snapshot.analytics.branches.map((branch) => (
                  <tr key={branch.branchId}>
                    <td><span className="font-semibold">{branch.branchName}</span> <span className="text-ink-muted">{branch.branchCode}</span></td>
                    <td className="num">{branch.orderCount}</td>
                    <td className="num">{formatMoney(branch.averageOrderValue)}</td>
                    <td className="num font-semibold">{formatMoney(branch.revenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}
    </main>
  );
}
