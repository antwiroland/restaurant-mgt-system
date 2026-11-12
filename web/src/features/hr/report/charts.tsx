"use client";

import { useState } from "react";

/**
 * Small single-series charts for the HR dashboard, built like the main dashboard's: one hue (chart-1), thin rounded
 * marks, values in text ink, hover tooltips, and a table view for screen readers.
 */
export function HBars({ data, label, empty = "Nobody here yet." }: { data: { label: string; count: number }[]; label: string; empty?: string }) {
  const max = Math.max(0, ...data.map((d) => d.count));
  if (data.length === 0 || max === 0) return <p className="text-sm text-ink-muted">{empty}</p>;
  return (
    <ul className="grid gap-2.5" aria-label={label}>
      {data.map((d) => (
        <li key={d.label} className="group grid gap-1" title={`${d.label}: ${d.count}`}>
          <div className="flex items-baseline justify-between gap-3 text-sm">
            <span className="truncate text-ink">{d.label}</span>
            <span className="tabular font-semibold text-ink-soft">{d.count}</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-[color:var(--color-chart-1)] transition-opacity group-hover:opacity-80"
              style={{ width: `${Math.max(2, (d.count / max) * 100)}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );
}

export type TrendRow = { month: string; headcount: number; joined: number; left: number };

const monthLabel = (month: string) => {
  const [y, m] = month.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, 1)).toLocaleDateString("en-GB", { month: "short", timeZone: "UTC" });
};

/** Headcount at each month end (this month: today), with joiners and leavers in the tooltip and the table. */
export function HeadcountColumns({ data }: { data: TrendRow[] }) {
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(0, ...data.map((d) => d.headcount));
  if (data.length === 0 || max === 0) return <p className="text-sm text-ink-muted">No employees in this period.</p>;
  return (
    <div className="grid gap-2">
      <div className="relative flex h-36 items-end gap-2" aria-hidden="true">
        {data.map((d, i) => (
          <div key={d.month} className="relative flex h-full flex-1 items-end" onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
            <div className={`w-full rounded-t-[4px] bg-[color:var(--color-chart-1)] transition-opacity ${hover === null || hover === i ? "opacity-100" : "opacity-45"}`}
              style={{ height: `${Math.max(3, (d.headcount / max) * 100)}%` }} />
            {hover === i ? (
              <div className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-2 -translate-x-1/2 whitespace-nowrap rounded-lg border border-line bg-surface px-2.5 py-1.5 text-xs shadow-lg">
                <p className="font-semibold text-ink">{monthLabel(d.month)}: {d.headcount} employed</p>
                <p className="text-ink-soft">+{d.joined} joined · −{d.left} left</p>
              </div>
            ) : null}
          </div>
        ))}
      </div>
      <div className="flex gap-2 text-[11px] text-ink-muted" aria-hidden="true">
        {data.map((d) => <span key={d.month} className="flex-1 text-center">{monthLabel(d.month)}</span>)}
      </div>
      <table className="sr-only">
        <caption>Headcount by month</caption>
        <thead><tr><th>Month</th><th>Employed</th><th>Joined</th><th>Left</th></tr></thead>
        <tbody>{data.map((d) => <tr key={d.month}><td>{d.month}</td><td>{d.headcount}</td><td>{d.joined}</td><td>{d.left}</td></tr>)}</tbody>
      </table>
    </div>
  );
}
