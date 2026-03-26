"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { activeOrdersForTable } from "@/features/tables/tables";
import {
  createTable,
  getOrders,
  getTableQr,
  getTables,
  updateTable,
  updateTableStatus,
  type OrderRecord,
  type TableRecord,
} from "@/lib/apiClient";

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
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<TableRecord["status"] | "ALL">("ALL");
  const [zoneFilter, setZoneFilter] = useState("");
  const [error, setError] = useState("");
  const [origin, setOrigin] = useState("");
  const [tableForm, setTableForm] = useState({ number: "", capacity: "4", zone: "" });

  useEffect(() => {
    if (typeof window !== "undefined") setOrigin(window.location.origin);
  }, []);

  async function loadData() {
    if (!session) return;
    const [nextTables, nextOrders] = await authenticatedFetch(async (activeSession) => {
      const [t, o] = await Promise.all([getTables(activeSession), getOrders(activeSession)]);
      return [t, o] as const;
    });
    setTables(nextTables);
    setOrders(nextOrders);
  }

  useEffect(() => {
    loadData().catch((err) => setError(err instanceof Error ? err.message : "Could not load tables"));
  }, [session]);

  const activeOrderList = useMemo(
    () => orders.filter((order) => !["COMPLETED", "CANCELLED", "VOIDED"].includes(order.status)),
    [orders],
  );
  const zoneOptions = useMemo(
    () => Array.from(new Set(tables.map((table) => table.zone).filter((zone): zone is string => !!zone))).sort((a, b) => a.localeCompare(b)),
    [tables],
  );
  const filteredTables = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return tables.filter((table) => {
      const matchesQuery = !normalizedQuery
        || table.number.toLowerCase().includes(normalizedQuery)
        || (table.zone?.toLowerCase().includes(normalizedQuery) ?? false)
        || (table.branchName?.toLowerCase().includes(normalizedQuery) ?? false);
      const matchesStatus = statusFilter === "ALL" || table.status === statusFilter;
      const matchesZone = !zoneFilter || (table.zone ?? "") === zoneFilter;
      return matchesQuery && matchesStatus && matchesZone;
    });
  }, [tables, query, statusFilter, zoneFilter]);

  function webScanLink(table: TableRecord): string {
    if (!origin) return `/scan/${table.qrToken}`;
    return `${origin}/scan/${table.qrToken}`;
  }

  async function onCreateTable(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    try {
      await authenticatedFetch((activeSession) => createTable(activeSession, {
        number: tableForm.number,
        capacity: Number.parseInt(tableForm.capacity || "1", 10),
        zone: tableForm.zone || undefined,
      }));
      setTableForm({ number: "", capacity: "4", zone: "" });
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create table");
    }
  }

  async function quickEditTable(table: TableRecord) {
    if (!session) return;
    const number = prompt("Table number", table.number);
    if (!number) return;
    const capacity = Number.parseInt(prompt("Capacity", String(table.capacity)) || String(table.capacity), 10);
    const zone = prompt("Zone", table.zone ?? "") ?? undefined;
    try {
      await authenticatedFetch((activeSession) => updateTable(activeSession, table.id, { number, capacity, zone, branchId: table.branchId }));
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update table");
    }
  }

  async function changeStatus(table: TableRecord, status: TableRecord["status"]) {
    if (!session) return;
    try {
      await authenticatedFetch((activeSession) => updateTableStatus(activeSession, table.id, status));
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update table status");
    }
  }

  async function showQrMeta(table: TableRecord) {
    if (!session) return;
    try {
      const qr = await authenticatedFetch((activeSession) => getTableQr(activeSession, table.id));
      alert(`Table ${qr.tableNumber}\nToken: ${qr.qrToken}\nURL: ${qr.qrUrl}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load QR metadata");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading tables...</section></main>;

  if (!session) {
    return (
      <main className="shell"><section className="panel"><p className="kicker">Tables</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link></section></main>
    );
  }

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Tables</p>
        <h1 className="text-2xl font-semibold">Table Management + Live Map</h1>
        <p className="mt-2 text-[#35523d]">Create/edit tables, change status, view QR metadata, and open running bill.</p>
        <div className="mt-3 grid gap-2 md:grid-cols-3">
          <input className="rounded-xl border border-[#cfe0c8] p-2 text-sm" placeholder="Search table, zone, or branch" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as TableRecord["status"] | "ALL")}>
            <option value="ALL">All statuses</option>
            <option value="AVAILABLE">AVAILABLE</option>
            <option value="OCCUPIED">OCCUPIED</option>
            <option value="RESERVED">RESERVED</option>
            <option value="CLOSED">CLOSED</option>
          </select>
          <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={zoneFilter} onChange={(e) => setZoneFilter(e.target.value)}>
            <option value="">All zones</option>
            {zoneOptions.map((zone) => <option key={zone} value={zone}>{zone}</option>)}
          </select>
        </div>
        {error ? <p className="mt-4 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        <form className="mt-3 flex flex-wrap items-center gap-2" onSubmit={(event) => void onCreateTable(event)}>
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Table number" value={tableForm.number} onChange={(e) => setTableForm((s) => ({ ...s, number: e.target.value }))} required />
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Capacity" type="number" min={1} value={tableForm.capacity} onChange={(e) => setTableForm((s) => ({ ...s, capacity: e.target.value }))} required />
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Zone" value={tableForm.zone} onChange={(e) => setTableForm((s) => ({ ...s, zone: e.target.value }))} />
          <button className="rounded-full bg-[#132018] px-4 py-2 text-white">Create Table</button>
        </form>
      </section>

      <section className="panel">
        <div className="mt-1 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {filteredTables.map((table) => {
            const tableOrders = activeOrdersForTable(table.id, activeOrderList);
            return (
              <article key={table.id} className={`rounded-2xl p-4 ${TABLE_STYLES[table.status]}`}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-lg font-semibold">Table {table.number}</p>
                    <p className="text-sm opacity-80">{table.zone || "Main floor"} - {table.capacity} seats</p>
                  </div>
                  <span className="rounded-full border border-current px-3 py-1 text-xs font-semibold">{table.status}</span>
                </div>
                <p className="mt-3 text-sm">{tableOrders.length > 0 ? `Active orders: ${tableOrders.join(", ")}` : "No active orders"}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <button className="rounded-full border border-current px-3 py-1 text-xs" onClick={() => void quickEditTable(table)}>Edit</button>
                  <select className="rounded-full border border-current px-3 py-1 text-xs bg-transparent" value={table.status} onChange={(e) => void changeStatus(table, e.target.value as TableRecord["status"])}>
                    <option value="AVAILABLE">AVAILABLE</option>
                    <option value="OCCUPIED">OCCUPIED</option>
                    <option value="RESERVED">RESERVED</option>
                    <option value="CLOSED">CLOSED</option>
                  </select>
                  <button className="rounded-full border border-current px-3 py-1 text-xs" onClick={() => void showQrMeta(table)}>QR Meta</button>
                  <Link className="rounded-full border border-current px-3 py-1 text-xs" href={`/tables/${table.id}/bill`}>Table Bill</Link>
                  <Link className="rounded-full border border-current px-3 py-1 text-xs" href={`/scan/${table.qrToken}`}>Open Web Menu</Link>
                  <a className="rounded-full border border-current px-3 py-1 text-xs" href={webScanLink(table)} target="_blank" rel="noreferrer">Scan URL</a>
                </div>
              </article>
            );
          })}
        </div>
        {filteredTables.length === 0 ? <p className="mt-3 rounded-xl border border-dashed border-[#cfe0c8] p-3 text-sm text-[#35523d]">No tables match the current filters.</p> : null}
      </section>
    </main>
  );
}
