"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Modal } from "@/components/Modal";
import { WALKOUT_REASONS, isAtRisk, watchStatus } from "@/features/tables/paymentWatch";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { Icon } from "@/components/Icon";
import { EmptyState, ErrorState, PageHeader, StatusBadge, humanize } from "@/components/ui";
import { formatMoney } from "@/lib/brand";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createTable,
  getActiveTableSessions,
  getOrders,
  getTablePaymentWatch,
  getTables,
  recordWalkout,
  updateTable,
  updateTableStatus,
  type OrderRecord,
  type TablePaymentWatchRecord,
  type TableRecord,
  type WalkoutReason,
} from "@/lib/apiClient";

const WATCH_REFRESH_MS = 60_000;

const CARD_STYLES: Record<TableRecord["status"], { tile: string; dot: string; seat: string }> = {
  AVAILABLE: { tile: "border-available/35 bg-surface hover:border-available", dot: "bg-available", seat: "bg-available/30" },
  OCCUPIED: { tile: "border-occupied/40 bg-occupied-subtle/60 hover:border-occupied", dot: "bg-occupied", seat: "bg-occupied/55" },
  RESERVED: { tile: "border-reserved/40 bg-reserved-subtle/60 hover:border-reserved", dot: "bg-reserved", seat: "bg-reserved/50" },
  CLOSED: { tile: "border-line bg-sunken opacity-75 hover:opacity-100", dot: "bg-closed", seat: "bg-closed/30" },
};

const STATUS_ORDER: TableRecord["status"][] = ["AVAILABLE", "OCCUPIED", "RESERVED", "CLOSED"];

const STATUS_ACTIONS: Array<{ status: TableRecord["status"]; label: string; className: string }> = [
  { status: "AVAILABLE", label: "Available", className: "border-available/40 text-available hover:bg-available-subtle" },
  { status: "OCCUPIED", label: "Occupied", className: "border-occupied/40 text-occupied hover:bg-occupied-subtle" },
  { status: "RESERVED", label: "Reserved", className: "border-reserved/40 text-reserved hover:bg-reserved-subtle" },
  { status: "CLOSED", label: "Closed", className: "border-closed/40 text-closed hover:bg-closed-subtle" },
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
  const [guestSessions, setGuestSessions] = useState<Record<string, number>>({});
  const [watchByTable, setWatchByTable] = useState<Record<string, TablePaymentWatchRecord>>({});
  const [walkoutOpen, setWalkoutOpen] = useState(false);
  const [walkoutReason, setWalkoutReason] = useState<WalkoutReason>("WALKED_OUT");
  const [walkoutNote, setWalkoutNote] = useState("");
  const [walkoutBusy, setWalkoutBusy] = useState(false);
  const [walkoutError, setWalkoutError] = useState("");
  const [notice, setNotice] = useState("");

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
    const sessions = await authenticatedFetch((activeSession) => getActiveTableSessions(activeSession)).catch(() => []);
    setGuestSessions(Object.fromEntries(sessions.map((entry) => [entry.tableId, entry.sessionCount])));
    await loadWatch();
  }

  /** What each table still owes, refreshed every minute so "served 20 min ago" stays true. */
  async function loadWatch() {
    const watch = await authenticatedFetch((activeSession) => getTablePaymentWatch(activeSession)).catch(() => null);
    if (watch) setWatchByTable(Object.fromEntries(watch.map((entry) => [entry.tableId, entry])));
  }

  useEffect(() => {
    loadData().catch((err) => setError(err instanceof Error ? err.message : "Could not load tables"));
  }, [session]);

  useEffect(() => {
    if (!session) return;
    const timer = window.setInterval(() => void loadWatch(), WATCH_REFRESH_MS);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  async function submitWalkout() {
    if (!session || !selectedTable) return;
    if (walkoutReason === "OTHER" && !walkoutNote.trim()) {
      setWalkoutError("Say what happened");
      return;
    }
    setWalkoutBusy(true);
    setWalkoutError("");
    try {
      const recorded = await authenticatedFetch((activeSession) =>
        recordWalkout(activeSession, selectedTable.id, { reason: walkoutReason, note: walkoutNote.trim() || undefined }));
      setWalkoutOpen(false);
      setWalkoutNote("");
      setWalkoutReason("WALKED_OUT");
      setSelectedTableId(null);
      setNotice(`Walkout recorded for table ${recorded.tableNumber}: ${formatMoney(recorded.amountOwed)} written off. The table is free again.`);
      await loadData();
    } catch (err) {
      setWalkoutError(err instanceof Error ? err.message : "Could not record the walkout");
    } finally {
      setWalkoutBusy(false);
    }
  }

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
          <h1 className="heading-lg mt-1">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  const isAdmin = session.user.role === "ADMIN";
  const canRecordWalkout = session.user.role === "ADMIN" || session.user.role === "MANAGER";
  const owing = Object.values(watchByTable);
  const atRisk = owing.filter(isAtRisk);
  const owedTotal = owing.reduce((sum, entry) => sum + Number(entry.outstanding), 0);
  const selectedWatch = selectedTable ? watchByTable[selectedTable.id] : undefined;
  const statusCounts = STATUS_ORDER.map((status) => ({ status, count: tables.filter((t) => t.status === status).length }));

  return (
    <main className="shell">
      <PageHeader
        eyebrow="Service"
        title="Floor & tables"
        description="See who's seated where, take an order for a table, and settle its bill."
        actions={
          <>
            <Link className="btn btn-secondary btn-sm" href="/tables/qr"><Icon name="qr" size={16} /> QR studio</Link>
            {isAdmin ? (
              <button className="btn btn-primary btn-sm" onClick={() => setAddDrawerOpen(true)}>
                <Icon name="plus" size={16} /> Add table
              </button>
            ) : null}
          </>
        }
      />

      <section className="grid gap-3">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          <button
            type="button"
            aria-pressed={statusFilter === "ALL"}
            onClick={() => setStatusFilter("ALL")}
            className={`flex items-center justify-between rounded-card border px-4 py-3 text-left transition-colors ${statusFilter === "ALL" ? "border-espresso bg-espresso text-espresso-ink" : "border-line bg-surface hover:border-line-strong"}`}
          >
            <span className="text-sm font-semibold">All tables</span>
            <span className="font-display text-xl font-extrabold tabular">{tables.length}</span>
          </button>
          {statusCounts.map(({ status, count }) => (
            <button
              key={status}
              type="button"
              aria-pressed={statusFilter === status}
              onClick={() => setStatusFilter(statusFilter === status ? "ALL" : status)}
              className={`flex items-center justify-between rounded-card border px-4 py-3 text-left transition-colors ${statusFilter === status ? "border-espresso bg-espresso text-espresso-ink" : "border-line bg-surface hover:border-line-strong"}`}
            >
              <span className="flex items-center gap-2 text-sm font-semibold">
                <span className={`h-2.5 w-2.5 rounded-full ${CARD_STYLES[status].dot}`} aria-hidden="true" />
                {humanize(status)}
              </span>
              <span className="font-display text-xl font-extrabold tabular">{count}</span>
            </button>
          ))}
        </div>

        <div className="flex flex-col gap-2 sm:flex-row">
          <label className="relative flex-1">
            <span className="sr-only">Search tables</span>
            <Icon name="search" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input className="input pl-10" placeholder="Search table, zone or branch" value={query} onChange={(e) => setQuery(e.target.value)} />
          </label>
          {zoneOptions.length > 1 ? (
            <select className="select sm:w-56" value={zoneFilter} onChange={(e) => setZoneFilter(e.target.value)} aria-label="Zone">
              <option value="">All zones</option>
              {zoneOptions.map((zone) => <option key={zone} value={zone}>{zone}</option>)}
            </select>
          ) : null}
        </div>

        {error ? <ErrorState message={error} onRetry={() => void loadData().catch((err) => setError(err instanceof Error ? err.message : "Could not load tables"))} /> : null}
        {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}

        {owing.length > 0 ? (
          <div className={`grid gap-2 rounded-card border p-3 ${atRisk.length > 0 ? "border-danger/40 bg-danger-subtle/40" : "border-line bg-surface"}`} aria-label="Unpaid tables">
            <p className="text-sm font-semibold text-ink">
              {owing.length} table{owing.length === 1 ? "" : "s"} owe <span className="money">{formatMoney(owedTotal)}</span>
              {atRisk.length > 0 ? <span className="text-danger-on"> · {atRisk.length} need{atRisk.length === 1 ? "s" : ""} you now</span> : null}
            </p>
            {atRisk.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {atRisk.map((entry) => (
                  <button key={entry.tableId} type="button" className="btn btn-sm btn-danger" onClick={() => setSelectedTableId(entry.tableId)}>
                    Table {entry.tableNumber}: {watchStatus(entry)}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </section>

      {groupedTables.map((group) => (
        <section key={group.zone} aria-labelledby={`zone-${group.zone}`} className="grid gap-3">
          <div className="flex items-baseline justify-between">
            <h2 id={`zone-${group.zone}`} className="font-display text-base font-bold text-ink">{group.zone}</h2>
            <span className="text-xs text-ink-muted">{group.tables.length} tables</span>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
            {group.tables.map((table) => {
              const tableOrders = activeOrders.filter((order) => order.tableId === table.id);
              const running = tableOrders.reduce((sum, order) => sum + Number(order.total), 0);
              const watch = watchByTable[table.id];
              const risky = watch ? isAtRisk(watch) : false;
              const style = CARD_STYLES[table.status];
              const seats = Math.min(table.capacity, 8);
              return (
                <button
                  key={table.id}
                  className={`group relative grid min-h-[148px] content-between rounded-xl border-2 p-3 text-left transition-[border-color,box-shadow,opacity] hover:shadow-card ${style.tile} ${risky ? "ring-2 ring-danger ring-offset-1" : ""}`}
                  onClick={() => setSelectedTableId(table.id)}
                  aria-label={`Table ${table.number}, ${table.capacity} seats, ${table.status.toLowerCase()}${watch ? `, owes ${formatMoney(watch.outstanding)}, ${watchStatus(watch)}` : tableOrders.length ? `, ${tableOrders.length} active orders` : ""}`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="font-display text-2xl font-extrabold leading-none text-ink">{table.number}</span>
                    <span className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide text-ink-soft">
                      <span className={`h-2 w-2 rounded-full ${style.dot}`} aria-hidden="true" />
                      {humanize(table.status)}
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-1 py-2" aria-hidden="true">
                    {Array.from({ length: seats }).map((_, i) => (
                      <span key={i} className={`h-2.5 w-2.5 rounded-full ${style.seat}`} />
                    ))}
                    {table.capacity > 8 ? <span className="text-[10px] text-ink-muted">+{table.capacity - 8}</span> : null}
                  </div>
                  <div className="grid gap-1 text-xs">
                    {watch ? (
                      <>
                        <span className="flex items-center justify-between font-semibold text-ink">
                          <span>Owes</span>
                          <span className="money">{formatMoney(watch.outstanding)}</span>
                        </span>
                        <span className={`flex items-center gap-1 font-semibold ${risky ? "text-danger-on" : "text-ink-soft"}`}>
                          {risky ? <Icon name="alert" size={12} /> : null}{watchStatus(watch)}
                        </span>
                      </>
                    ) : tableOrders.length > 0 ? (
                      <span className="flex items-center justify-between font-semibold text-ink">
                        <span>{tableOrders.length} order{tableOrders.length > 1 ? "s" : ""}</span>
                        <span className="money">{formatMoney(running)}</span>
                      </span>
                    ) : (
                      <span className="text-ink-muted">{table.capacity} seats · no orders</span>
                    )}
                    {guestSessions[table.id] ? (
                      <span className="flex items-center gap-1 font-semibold text-info-on">
                        <Icon name="qr" size={12} /> {guestSessions[table.id]} guest phone{guestSessions[table.id] > 1 ? "s" : ""}
                      </span>
                    ) : null}
                  </div>
                </button>
              );
            })}
          </div>
        </section>
      ))}

      {groupedTables.length === 0 ? (
        <EmptyState
          icon="table"
          title={tables.length === 0 ? "No tables yet" : "No tables match"}
          description={tables.length === 0 ? (isAdmin ? "Add your first table to start seating guests and printing QR codes." : "An admin needs to add tables before they appear here.") : "Try a different status, zone or search."}
          action={tables.length === 0 && isAdmin ? <button className="btn btn-primary btn-sm" onClick={() => setAddDrawerOpen(true)}>Add table</button> : undefined}
        />
      ) : null}

      <Modal
        open={walkoutOpen && !!selectedTable && !!selectedWatch}
        title={selectedTable ? `Record walkout · Table ${selectedTable.number}` : "Record walkout"}
        onClose={() => setWalkoutOpen(false)}
        footer={
          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-secondary btn-md" onClick={() => setWalkoutOpen(false)} disabled={walkoutBusy}>Cancel</button>
            <button type="button" className="btn btn-danger btn-md" onClick={() => void submitWalkout()} disabled={walkoutBusy}>
              {walkoutBusy ? <Spinner className="text-current" /> : null}
              Write off {selectedWatch ? formatMoney(selectedWatch.outstanding) : ""}
            </button>
          </div>
        }
      >
        <div className="grid gap-4 text-sm">
          <p className="text-ink-soft">
            Use this when the guests have left without paying. The unpaid orders are written off, the table is freed, and the
            loss goes on the walkout report with who took the orders and any phone number the guests left.
          </p>
          <fieldset className="grid gap-2">
            <legend className="field-label">What happened</legend>
            {WALKOUT_REASONS.map((option) => (
              <label key={option.value} className="flex items-center gap-2">
                <input type="radio" name="walkout-reason" value={option.value} checked={walkoutReason === option.value} onChange={() => setWalkoutReason(option.value)} />
                {option.label}
              </label>
            ))}
          </fieldset>
          <label className="field">
            <span className="field-label">Note {walkoutReason === "OTHER" ? "" : <span className="font-normal text-ink-muted">(optional)</span>}</span>
            <textarea className="textarea min-h-[72px]" maxLength={500} value={walkoutNote} onChange={(e) => setWalkoutNote(e.target.value)} placeholder="e.g. Left while the waiter was at another table" />
          </label>
          {walkoutError ? <p className="alert alert-danger" role="alert">{walkoutError}</p> : null}
        </div>
      </Modal>

      <Drawer open={!!selectedTable} title={selectedTable ? `Table ${selectedTable.number}` : "Table Details"} onClose={() => setSelectedTableId(null)}>
        {selectedTable ? (
          <div className="grid grid-cols-1 gap-4">
            <section className="grid grid-cols-3 gap-2 rounded-xl bg-ivory p-3 text-sm">
              <div><p className="text-xs text-ink-muted">Status</p><StatusBadge status={selectedTable.status} className="mt-1" /></div>
              <div><p className="text-xs text-ink-muted">Zone</p><p className="mt-1 font-semibold">{selectedTable.zone || "Main floor"}</p></div>
              <div><p className="text-xs text-ink-muted">Seats</p><p className="mt-1 font-semibold">{selectedTable.capacity}</p></div>
            </section>

            {selectedWatch ? (
              <section className={`grid gap-2 rounded-xl border p-3 text-sm ${isAtRisk(selectedWatch) ? "border-danger/40 bg-danger-subtle/40" : "border-line"}`} aria-label="Bill">
                <div className="flex items-baseline justify-between">
                  <span className="font-semibold">Owes</span>
                  <span className="money font-display text-xl">{formatMoney(selectedWatch.outstanding)}</span>
                </div>
                <p className={isAtRisk(selectedWatch) ? "font-semibold text-danger-on" : "text-ink-soft"}>{watchStatus(selectedWatch)}</p>
                {canRecordWalkout ? (
                  <button type="button" className="btn btn-secondary btn-sm justify-self-start" onClick={() => { setWalkoutError(""); setWalkoutOpen(true); }}>
                    Record walkout
                  </button>
                ) : null}
              </section>
            ) : null}

            <section className="grid gap-2 sm:grid-cols-2">
              <Link className="btn btn-primary btn-lg" href={`/pos?table=${selectedTable.id}`}>
                <Icon name="plus" size={18} /> Take order
              </Link>
              <Link className="btn btn-secondary btn-lg" href={`/tables/${selectedTable.id}/bill`}>
                <Icon name="receipt" size={18} /> View bill
              </Link>
            </section>

            <section className="grid gap-2">
              <p className="field-label">Set status</p>
              <div className="grid grid-cols-2 gap-2">
                {STATUS_ACTIONS.map((action) => (
                  <button
                    key={action.status}
                    className={`btn btn-md border bg-surface ${action.className} ${selectedTable.status === action.status ? "ring-2 ring-espresso ring-offset-1" : ""}`}
                    disabled={statusBusy === action.status || selectedTable.status === action.status}
                    onClick={() => void handleStatusChange(action.status)}
                  >
                    {statusBusy === action.status ? <Spinner className="text-current" /> : null}
                    {action.label}
                  </button>
                ))}
              </div>
            </section>

            <section className="grid grid-cols-1 gap-2">
              <p className="field-label">Active orders</p>
              {selectedTableOrders.length === 0 ? (
                <p className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No active orders for this table.</p>
              ) : (
                <ul className="grid grid-cols-1 gap-2">
                  {selectedTableOrders.map((order) => (
                    <li key={order.id}>
                      <Link href={`/orders/${order.id}`} className="flex items-center justify-between gap-3 rounded-xl border border-line p-3 text-sm hover:border-line-strong">
                        <span className="min-w-0 flex-1">
                          <span className="font-mono font-semibold">#{order.id.slice(0, 8).toUpperCase()}</span>
                          <span className="block truncate text-ink-soft">{order.items.map((item) => `${item.quantity}× ${item.name}`).join(", ")}</span>
                        </span>
                        <span className="grid shrink-0 justify-items-end gap-1">
                          <StatusBadge status={order.status} />
                          <span className="money">{formatMoney(order.total)}</span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {session.user.role === "ADMIN" ? (
            <section className="grid gap-2 rounded-xl border border-line p-3">
              <p className="field-label">Edit table</p>
              <div className="grid gap-2">
                <input className="input" placeholder="Table number" value={editForm.number} onChange={(e) => setEditForm((s) => ({ ...s, number: e.target.value }))} />
                <input className="input" placeholder="Capacity" type="number" min={1} value={editForm.capacity} onChange={(e) => setEditForm((s) => ({ ...s, capacity: e.target.value }))} />
                <input className="input" placeholder="Zone" value={editForm.zone} onChange={(e) => setEditForm((s) => ({ ...s, zone: e.target.value }))} />
                <button className="btn btn-primary btn-md" onClick={() => void handleSaveEdit()} disabled={savingEdit}>
                  {savingEdit ? <Spinner className="text-current" /> : null}
                  Save changes
                </button>
              </div>
            </section>
            ) : null}

            <section className="grid gap-2 rounded-xl border border-line p-3">
              <p className="field-label">Guest QR link</p>
              <code className="input block min-w-0 break-all font-mono text-xs leading-relaxed">{selectedTable.qrToken}</code>
              <div className="flex flex-wrap gap-2">
                <button className="btn btn-secondary btn-sm" onClick={() => void copyToken(selectedTable.qrToken)}>
                  {copied ? "Copied" : "Copy Token"}
                </button>
                <a className="btn btn-secondary btn-sm" href={scanUrl(selectedTable)} target="_blank" rel="noreferrer">
                  Open Scan URL
                </a>
              </div>
            </section>

            <Link className="btn btn-secondary btn-md" href={`/tables/${selectedTable.id}/qr`}><Icon name="print" size={16} /> Printable QR card</Link>
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
