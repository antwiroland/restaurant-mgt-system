"use client";

import { useEffect, useMemo, useState } from "react";
import { getPublicTableStatus, type TablePublicStatusView } from "@/lib/apiClient";

type TableStatus = TablePublicStatusView["status"];

const STATUS_META: Record<TableStatus, { label: string; card: string; dot: string }> = {
  AVAILABLE: {
    label: "Available",
    card: "bg-available-subtle border-available text-available",
    dot: "bg-available",
  },
  OCCUPIED: {
    label: "Occupied",
    card: "bg-occupied-subtle border-occupied text-occupied",
    dot: "bg-occupied",
  },
  RESERVED: {
    label: "Reserved",
    card: "bg-reserved-subtle border-reserved text-reserved",
    dot: "bg-reserved",
  },
  CLOSED: {
    label: "Closed",
    card: "bg-closed-subtle border-closed text-closed",
    dot: "bg-closed",
  },
};

const REFRESH_INTERVAL_MS = 30_000;

export default function TableStatusPage() {
  const [tables, setTables] = useState<TablePublicStatusView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  async function load() {
    try {
      const data = await getPublicTableStatus();
      setTables(data);
      setLastUpdated(new Date());
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load table status");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    const interval = setInterval(() => void load(), REFRESH_INTERVAL_MS);
    return () => clearInterval(interval);
  }, []);

  const groups = useMemo(() => {
    const map = new Map<string, TablePublicStatusView[]>();
    for (const table of tables) {
      const zone = table.zone?.trim() || "Main Floor";
      const list = map.get(zone) ?? [];
      list.push(table);
      map.set(zone, list);
    }
    return Array.from(map.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([zone, list]) => ({
        zone,
        tables: list.sort((a, b) => a.number.localeCompare(b.number, undefined, { numeric: true })),
      }));
  }, [tables]);

  const counts = useMemo(() => {
    const result: Partial<Record<TableStatus, number>> = {};
    for (const t of tables) {
      result[t.status] = (result[t.status] ?? 0) + 1;
    }
    return result;
  }, [tables]);

  return (
    <div className="min-h-screen bg-canvas px-4 py-8">
      <div className="mx-auto max-w-5xl">
        {/* Header */}
        <div className="mb-8 text-center">
          <p className="kicker">Restaurant</p>
          <h1 className="mt-1 text-3xl font-semibold text-ink">Table Availability</h1>
          <p className="mt-2 text-sm text-ink-soft">Updated every 30 seconds · Walk-in welcome</p>
          {lastUpdated ? (
            <p className="mt-1 text-xs text-ink-soft">
              Last updated: {lastUpdated.toLocaleTimeString()}
            </p>
          ) : null}
        </div>

        {/* Legend / summary */}
        <div className="mb-6 flex flex-wrap justify-center gap-4">
          {(["AVAILABLE", "OCCUPIED", "RESERVED", "CLOSED"] as TableStatus[]).map((status) => {
            const meta = STATUS_META[status];
            const count = counts[status] ?? 0;
            return (
              <div key={status} className="flex items-center gap-2">
                <span className={`h-3 w-3 rounded-full ${meta.dot}`} />
                <span className="text-sm text-ink-soft">
                  {meta.label} <span className="font-semibold text-ink">({count})</span>
                </span>
              </div>
            );
          })}
        </div>

        {error ? (
          <p className="mb-6 alert alert-danger text-center">{error}</p>
        ) : null}

        {loading ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
            {Array.from({ length: 12 }).map((_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-xl bg-muted" />
            ))}
          </div>
        ) : (
          groups.map((group) => (
            <section key={group.zone} className="mb-8">
              <h2 className="mb-3 text-xs font-semibold uppercase tracking-widest text-ink-soft">
                {group.zone}
              </h2>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
                {group.tables.map((table) => {
                  const meta = STATUS_META[table.status];
                  return (
                    <div
                      key={table.number}
                      className={`rounded-xl border p-4 text-center ${meta.card}`}
                    >
                      <p className="text-2xl font-bold">{table.number}</p>
                      <p className="mt-1 text-xs opacity-75">{table.capacity} seats</p>
                      <p className="mt-2 text-xs font-semibold uppercase tracking-wide">
                        {meta.label}
                      </p>
                    </div>
                  );
                })}
              </div>
            </section>
          ))
        )}

        {!loading && groups.length === 0 && !error ? (
          <p className="text-center text-sm text-ink-soft">No tables available right now.</p>
        ) : null}
      </div>
    </div>
  );
}
