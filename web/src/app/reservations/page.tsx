"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  cancelReservation,
  createReservation,
  getReservations,
  getTables,
  updateReservationStatus,
  type ReservationRecord,
  type ReservationStatus,
  type TableRecord,
} from "@/lib/apiClient";

export default function ReservationsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [reservations, setReservations] = useState<ReservationRecord[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<ReservationStatus | "ALL">("ALL");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const [filterDate, setFilterDate] = useState("");
  const [filterTableId, setFilterTableId] = useState("");

  const [form, setForm] = useState({
    tableId: "",
    customerName: "",
    customerPhone: "",
    partySize: "2",
    reservedAt: "",
    durationMins: "60",
    notes: "",
  });
  const filteredReservations = reservations.filter((reservation) => {
    const normalizedQuery = query.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || reservation.tableNumber.toLowerCase().includes(normalizedQuery)
      || (reservation.customerName?.toLowerCase().includes(normalizedQuery) ?? false)
      || (reservation.customerPhone?.toLowerCase().includes(normalizedQuery) ?? false);
    const matchesStatus = statusFilter === "ALL" || reservation.status === statusFilter;
    return matchesQuery && matchesStatus;
  });

  async function loadData() {
    if (!session) return;
    const [t, r] = await authenticatedFetch(async (activeSession) => {
      const [tablesRes, reservationsRes] = await Promise.all([
        getTables(activeSession),
        getReservations(activeSession, {
          date: filterDate || undefined,
          tableId: filterTableId || undefined,
        }),
      ]);
      return [tablesRes, reservationsRes] as const;
    });
    setTables(t);
    setReservations(r);
    if (!form.tableId && t.length > 0) {
      setForm((current) => ({ ...current, tableId: t[0].id }));
    }
  }

  useEffect(() => {
    loadData().catch((err) => setError(err instanceof Error ? err.message : "Could not load reservations"));
  }, [session, filterDate, filterTableId]);

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    try {
      setError("");
      await authenticatedFetch((activeSession) =>
        createReservation(activeSession, {
          tableId: form.tableId,
          customerName: form.customerName || undefined,
          customerPhone: form.customerPhone || undefined,
          partySize: Number.parseInt(form.partySize || "1", 10),
          reservedAt: new Date(form.reservedAt).toISOString(),
          durationMins: Number.parseInt(form.durationMins || "60", 10),
          notes: form.notes || undefined,
        }),
      );
      setMessage("Reservation created");
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create reservation");
    }
  }

  async function onStatus(id: string, status: ReservationStatus) {
    if (!session) return;
    try {
      setError("");
      await authenticatedFetch((activeSession) => updateReservationStatus(activeSession, id, status));
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update reservation status");
    }
  }

  async function onCancel(id: string) {
    if (!session) return;
    try {
      setError("");
      await authenticatedFetch((activeSession) => cancelReservation(activeSession, id));
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not cancel reservation");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading reservations...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Reservations</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Reservations</p>
        <h1 className="text-2xl font-semibold">Reservation Management</h1>
        <div className="mt-3 flex flex-wrap gap-2">
          <input className="rounded-xl border border-[#cfe0c8] p-2" type="date" value={filterDate} onChange={(e) => setFilterDate(e.target.value)} />
          <select className="rounded-xl border border-[#cfe0c8] p-2" value={filterTableId} onChange={(e) => setFilterTableId(e.target.value)}>
            <option value="">All tables</option>
            {tables.map((table) => <option key={table.id} value={table.id}>Table {table.number}</option>)}
          </select>
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Search guest, phone, or table" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="rounded-xl border border-[#cfe0c8] p-2" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as ReservationStatus | "ALL")}>
            <option value="ALL">All statuses</option>
            <option value="PENDING">PENDING</option>
            <option value="CONFIRMED">CONFIRMED</option>
            <option value="CANCELLED">CANCELLED</option>
          </select>
        </div>
      </section>

      <section className="panel">
        <h2 className="text-lg font-semibold">Create Reservation</h2>
        <form className="mt-3 grid gap-2 md:grid-cols-3" onSubmit={(event) => void onCreate(event)}>
          <select className="rounded-xl border border-[#cfe0c8] p-2" value={form.tableId} onChange={(e) => setForm((s) => ({ ...s, tableId: e.target.value }))} required>
            <option value="">Select table</option>
            {tables.map((table) => <option key={table.id} value={table.id}>Table {table.number}</option>)}
          </select>
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Customer name" value={form.customerName} onChange={(e) => setForm((s) => ({ ...s, customerName: e.target.value }))} />
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Customer phone" value={form.customerPhone} onChange={(e) => setForm((s) => ({ ...s, customerPhone: e.target.value }))} />
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Party size" type="number" min={1} value={form.partySize} onChange={(e) => setForm((s) => ({ ...s, partySize: e.target.value }))} required />
          <input className="rounded-xl border border-[#cfe0c8] p-2" type="datetime-local" value={form.reservedAt} onChange={(e) => setForm((s) => ({ ...s, reservedAt: e.target.value }))} required />
          <input className="rounded-xl border border-[#cfe0c8] p-2" placeholder="Duration mins" type="number" min={15} value={form.durationMins} onChange={(e) => setForm((s) => ({ ...s, durationMins: e.target.value }))} />
          <input className="md:col-span-2 rounded-xl border border-[#cfe0c8] p-2" placeholder="Notes" value={form.notes} onChange={(e) => setForm((s) => ({ ...s, notes: e.target.value }))} />
          <button className="rounded-full bg-[#132018] px-4 py-2 text-white">Create</button>
        </form>
      </section>

      <section className="panel">
        <h2 className="text-lg font-semibold">Existing Reservations</h2>
        <ul className="mt-3 grid gap-2">
          {filteredReservations.map((reservation) => (
            <li key={reservation.id} className="rounded-xl border border-[#cfe0c8] p-3">
              <p className="font-semibold">Table {reservation.tableNumber} · {reservation.customerName || "Guest"} ({reservation.partySize})</p>
              <p className="text-xs text-[#35523d]">{new Date(reservation.reservedAt).toLocaleString()} · {reservation.durationMins} mins · {reservation.customerPhone || "No phone"}</p>
              <div className="mt-2 flex flex-wrap gap-2">
                <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={reservation.status} onChange={(e) => void onStatus(reservation.id, e.target.value as ReservationStatus)}>
                  <option value="PENDING">PENDING</option>
                  <option value="CONFIRMED">CONFIRMED</option>
                  <option value="CANCELLED">CANCELLED</option>
                </select>
                <button className="rounded-full border border-[#991b1b] px-3 py-1 text-xs text-[#991b1b]" onClick={() => void onCancel(reservation.id)}>Cancel</button>
              </div>
            </li>
          ))}
          {filteredReservations.length === 0 ? <li className="rounded-xl border border-dashed border-[#cfe0c8] p-3 text-sm text-[#35523d]">No reservations match the current filters.</li> : null}
        </ul>
        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        {message ? <p className="mt-3 rounded-xl bg-[#dcfce7] px-4 py-3 text-sm text-[#166534]">{message}</p> : null}
      </section>
    </main>
  );
}
