"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createTable,
  getOrders,
  getTables,
  updateTable,
  updateTableStatus,
  type OrderRecord,
  type TableRecord,
} from "@/lib/apiClient";

const CARD_STYLES: Record<TableRecord["status"], string> = {
  AVAILABLE: "bg-available-subtle border-available text-available",
  OCCUPIED: "bg-occupied-subtle border-occupied text-occupied",
  RESERVED: "bg-reserved-subtle border-reserved text-reserved",
  CLOSED: "bg-closed-subtle border-closed text-closed",
};

const STATUS_ACTIONS: Array<{ status: TableRecord["status"]; label: string; className: string }> = [
  { status: "AVAILABLE", label: "Set Available", className: "bg-available text-brand-on border-available" },
  { status: "OCCUPIED", label: "Set Occupied", className: "bg-occupied text-brand-on border-occupied" },
  { status: "RESERVED", label: "Set Reserved", className: "bg-reserved text-brand-on border-reserved" },
  { status: "CLOSED", label: "Set Closed", className: "bg-closed text-brand-on border-closed" },
];

export default function TablesPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<TableRecord["status"] | "ALL">("ALL");
  const [zoneFilter, setZoneFilter] = useState("");
  const [error, setError] = useState("");
  const [origin, setOrigin] = useState("");

  const [selectedTableId, setSelectedTableId] = useState<string | null>(null);
  const [addDrawerOpen, setAddDrawerOpen] = useState(false);

  const [editForm, setEditForm] = useState({ number: "", capacity: "4", zone: "" });
  const [createForm, setCreateForm] = useState({ number: "", capacity: "4", zone: "" });
  const [savingEdit, setSavingEdit] = useState(false);
  const [savingCreate, setSavingCreate] = useState(false);
  const [statusBusy, setStatusBusy] = useState<TableRecord["status"] | null>(null);
  const [copied, setCopied] = useState(false);

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

  const selectedTable = useMemo(
    () => tables.find((table) => table.id === selectedTableId) ?? null,
    [tables, selectedTableId],
  );

  useEffect(() => {
    if (!selectedTable) return;
    setEditForm({
      number: selectedTable.number,
      capacity: String(selectedTable.capacity),
      zone: selectedTable.zone ?? "",
    });
  }, [selectedTable]);

  const activeOrders = useMemo(
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

  const groupedTables = useMemo(() => {
    const groups = new Map<string, TableRecord[]>();
    filteredTables.forEach((table) => {
      const zone = table.zone?.trim() || "Main Floor";
      const list = groups.get(zone) ?? [];
      list.push(table);
      groups.set(zone, list);
    });
    return Array.from(groups.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([zone, list]) => ({
        zone,
        tables: list.sort((a, b) => a.number.localeCompare(b.number, undefined, { numeric: true })),
      }));
  }, [filteredTables]);

  const selectedTableOrders = useMemo(() => {
    if (!selectedTable) return [];
    return activeOrders.filter((order) => order.tableId === selectedTable.id);
  }, [activeOrders, selectedTable]);

  function scanUrl(table: TableRecord): string {
    if (!origin) return `/scan/${table.qrToken}`;
    return `${origin}/scan/${table.qrToken}`;
  }

  async function handleCreateTable(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    setSavingCreate(true);
    try {
      await authenticatedFetch((activeSession) => createTable(activeSession, {
        number: createForm.number,
        capacity: Number.parseInt(createForm.capacity || "1", 10),
        zone: createForm.zone || undefined,
      }));
      setCreateForm({ number: "", capacity: "4", zone: "" });
      setAddDrawerOpen(false);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create table");
    } finally {
      setSavingCreate(false);
    }
  }

  async function handleSaveEdit() {
    if (!session || !selectedTable) return;
    setSavingEdit(true);
    try {
      await authenticatedFetch((activeSession) => updateTable(activeSession, selectedTable.id, {
        number: editForm.number,
        capacity: Number.parseInt(editForm.capacity || "1", 10),
        zone: editForm.zone || undefined,
        branchId: selectedTable.branchId,
      }));
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update table");
    } finally {
      setSavingEdit(false);
    }
  }

  async function handleStatusChange(nextStatus: TableRecord["status"]) {
    if (!session || !selectedTable) return;
    setStatusBusy(nextStatus);
    try {
      await authenticatedFetch((activeSession) => updateTableStatus(activeSession, selectedTable.id, nextStatus));
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update table status");
    } finally {
      setStatusBusy(null);
    }
  }

  async function copyToken(value: string) {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  }

  if (loading) {
    return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" />{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Tables</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="kicker">Tables</p>
            <h1 className="text-2xl font-semibold">Floor Map</h1>
            <p className="mt-1 text-sm text-ink-soft">Tap a table tile to open details, status controls, and inline edit form.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link className="btn btn-secondary btn-md" href="/tables/qr">
              QR Studio
            </Link>
            <button className="btn btn-primary btn-md" onClick={() => setAddDrawerOpen(true)}>
              Add Table
            </button>
          </div>
        </div>

        <div className="mt-4 grid gap-2 md:grid-cols-3">
          <input className="input text-sm" placeholder="Search table, zone, or branch" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="select text-sm" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as TableRecord["status"] | "ALL")}>
            <option value="ALL">All statuses</option>
            <option value="AVAILABLE">AVAILABLE</option>
            <option value="OCCUPIED">OCCUPIED</option>
            <option value="RESERVED">RESERVED</option>
            <option value="CLOSED">CLOSED</option>
          </select>
          <select className="select text-sm" value={zoneFilter} onChange={(e) => setZoneFilter(e.target.value)}>
            <option value="">All zones</option>
            {zoneOptions.map((zone) => <option key={zone} value={zone}>{zone}</option>)}
          </select>
        </div>

        {error ? <p className="mt-4 alert alert-danger">{error}</p> : null}
      </section>

      {groupedTables.map((group) => (
        <section key={group.zone} className="panel">
          <p className="kicker">{group.zone}</p>
          <div className="mt-3 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
            {group.tables.map((table) => {
              const hasOrders = activeOrders.some((order) => order.tableId === table.id);
              return (
                <button
                  key={table.id}
                  className={`min-h-[var(--touch-card)] rounded-xl border p-3 text-left transition hover:shadow-card ${CARD_STYLES[table.status]}`}
                  onClick={() => setSelectedTableId(table.id)}
                >
                  <p className="text-lg font-semibold">T-{table.number}</p>
                  <p className="mt-1 text-sm">{table.capacity} seats</p>
                  <p className="mt-1 text-xs font-semibold uppercase tracking-wide">{table.status}</p>
                  <p className="mt-1 text-xs opacity-80">{hasOrders ? "Has active orders" : "No active orders"}</p>
                </button>
              );
            })}
          </div>
        </section>
      ))}

      {groupedTables.length === 0 ? (
        <section className="panel">
          <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No tables match the current filters.</p>
        </section>
      ) : null}

      <Drawer open={!!selectedTable} title={selectedTable ? `Table ${selectedTable.number}` : "Table Details"} onClose={() => setSelectedTableId(null)}>
        {selectedTable ? (
          <div className="grid gap-4">
            <section className="grid gap-1 rounded-xl border border-line bg-muted p-3 text-sm">
              <p><span className="font-semibold">Status:</span> {selectedTable.status}</p>
              <p><span className="font-semibold">Zone:</span> {selectedTable.zone || "Main Floor"}</p>
              <p><span className="font-semibold">Capacity:</span> {selectedTable.capacity} seats</p>
            </section>

            <section className="grid gap-2">
              <p className="field-label">Status Actions</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {STATUS_ACTIONS.map((action) => (
                  <button
                    key={action.status}
                    className={`btn btn-lg border ${action.className}`}
                    disabled={statusBusy === action.status || selectedTable.status === action.status}
                    onClick={() => void handleStatusChange(action.status)}
                  >
                    {statusBusy === action.status ? <Spinner className="text-current" /> : null}
                    {action.label}
                  </button>
                ))}
              </div>
            </section>

            <section className="grid gap-2">
              <p className="field-label">Active Orders</p>
              {selectedTableOrders.length === 0 ? (
                <p className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No active orders for this table.</p>
              ) : (
                <ul className="grid gap-2">
                  {selectedTableOrders.map((order) => (
                    <li key={order.id} className="rounded-xl border border-line p-3 text-sm">
                      <p className="font-semibold">#{order.id.slice(0, 8)} - {order.status}</p>
                      <p className="text-ink-soft">{order.items.length} item(s) - GHS {order.total}</p>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="grid gap-2 rounded-xl border border-line p-3">
              <p className="field-label">Edit Table</p>
              <div className="grid gap-2">
                <input className="input" placeholder="Table number" value={editForm.number} onChange={(e) => setEditForm((s) => ({ ...s, number: e.target.value }))} />
                <input className="input" placeholder="Capacity" type="number" min={1} value={editForm.capacity} onChange={(e) => setEditForm((s) => ({ ...s, capacity: e.target.value }))} />
                <input className="input" placeholder="Zone" value={editForm.zone} onChange={(e) => setEditForm((s) => ({ ...s, zone: e.target.value }))} />
                <button className="btn btn-primary btn-md" onClick={() => void handleSaveEdit()} disabled={savingEdit}>
                  {savingEdit ? <Spinner className="text-current" /> : null}
                  Save Changes
                </button>
              </div>
            </section>

            <section className="grid gap-2 rounded-xl border border-line p-3">
              <p className="field-label">QR Metadata</p>
              <code className="input overflow-x-auto">{selectedTable.qrToken}</code>
              <div className="flex flex-wrap gap-2">
                <button className="btn btn-secondary btn-sm" onClick={() => void copyToken(selectedTable.qrToken)}>
                  {copied ? "Copied" : "Copy Token"}
                </button>
                <a className="btn btn-secondary btn-sm" href={scanUrl(selectedTable)} target="_blank" rel="noreferrer">
                  Open Scan URL
                </a>
              </div>
            </section>

            <section className="grid gap-2 sm:grid-cols-2">
              <Link className="btn btn-secondary btn-md" href={`/tables/${selectedTable.id}/bill`}>Table Bill</Link>
              <Link className="btn btn-secondary btn-md" href={`/tables/${selectedTable.id}/qr`}>Printable QR</Link>
            </section>
          </div>
        ) : null}
      </Drawer>

      <Drawer open={addDrawerOpen} title="Add Table" onClose={() => setAddDrawerOpen(false)}>
        <form className="grid gap-3" onSubmit={(event) => void handleCreateTable(event)}>
          <label className="field">
            <span className="field-label">Table Number</span>
            <input className="input" value={createForm.number} onChange={(e) => setCreateForm((s) => ({ ...s, number: e.target.value }))} required />
          </label>
          <label className="field">
            <span className="field-label">Capacity</span>
            <input className="input" type="number" min={1} value={createForm.capacity} onChange={(e) => setCreateForm((s) => ({ ...s, capacity: e.target.value }))} required />
          </label>
          <label className="field">
            <span className="field-label">Zone</span>
            <input className="input" value={createForm.zone} onChange={(e) => setCreateForm((s) => ({ ...s, zone: e.target.value }))} />
          </label>
          <button className="btn btn-primary btn-md" disabled={savingCreate}>
            {savingCreate ? <Spinner className="text-current" /> : null}
            Create Table
          </button>
        </form>
      </Drawer>
    </main>
  );
}
