"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import { createTable, getTables, type TableRecord } from "@/lib/apiClient";

export default function TableQrStudioPage() {
  const router = useRouter();
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ number: "", capacity: "4", zone: "" });

  const canManage = hasStaffRole(session?.user.role, ["ADMIN"]);

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(getTables)
      .then((nextTables) => setTables(nextTables))
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load tables"));
  }, [authenticatedFetch, session]);

  const filteredTables = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return [...tables]
      .filter((table) => !normalizedQuery
        || table.number.toLowerCase().includes(normalizedQuery)
        || (table.zone?.toLowerCase().includes(normalizedQuery) ?? false)
        || (table.branchName?.toLowerCase().includes(normalizedQuery) ?? false))
      .sort((left, right) => left.number.localeCompare(right.number, undefined, { numeric: true }));
  }, [query, tables]);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!canManage) return;
    setSaving(true);
    setError("");
    try {
      const created = await authenticatedFetch((activeSession) => createTable(activeSession, {
        number: form.number,
        capacity: Number.parseInt(form.capacity || "1", 10),
        zone: form.zone || undefined,
      }));
      router.push(`/tables/${created.id}/qr`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create table");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-56" />{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 w-full" />)}</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Table QR</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 xl:grid-cols-[420px,1fr]">
      <section className="panel">
        <p className="kicker">QR Studio</p>
        <h1 className="text-2xl font-semibold">Single-Table QR Setup</h1>
        <p className="mt-2 text-sm text-ink-soft">Create a table, then open its dedicated printable QR page for edits, printing, or deletion.</p>

        {canManage ? (
          <form className="mt-5 grid gap-3" onSubmit={(event) => void handleCreate(event)}>
            <label className="field">
              <span className="field-label">Table Number</span>
              <input className="input" value={form.number} onChange={(event) => setForm((current) => ({ ...current, number: event.target.value }))} required />
            </label>
            <label className="field">
              <span className="field-label">Capacity</span>
              <input className="input" type="number" min={1} value={form.capacity} onChange={(event) => setForm((current) => ({ ...current, capacity: event.target.value }))} required />
            </label>
            <label className="field">
              <span className="field-label">Zone</span>
              <input className="input" value={form.zone} onChange={(event) => setForm((current) => ({ ...current, zone: event.target.value }))} />
            </label>
            <button className="btn btn-primary" disabled={saving}>
              {saving ? <Spinner className="text-current" /> : null}
              Create Table QR
            </button>
          </form>
        ) : (
          <p className="mt-5 rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">
            Only admins can create or delete tables. You can still open existing table QR pages below.
          </p>
        )}

        {error ? <p className="mt-4 alert alert-danger">{error}</p> : null}
      </section>

      <section className="panel">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="kicker">Existing Tables</p>
            <h2 className="text-xl font-semibold">Open Printable QR Page</h2>
          </div>
          <input
            className="input max-w-sm"
            placeholder="Search table, zone, or branch"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {filteredTables.map((table) => (
            <Link key={table.id} className="card card-interactive grid gap-2" href={`/tables/${table.id}/qr`}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-lg font-semibold text-ink">Table {table.number}</p>
                  <p className="text-sm text-ink-soft">{table.zone || "Main Floor"}</p>
                </div>
                <span className="badge badge-info">{table.status}</span>
              </div>
              <p className="text-sm text-ink-soft">{table.capacity} seats{table.branchName ? ` · ${table.branchName}` : ""}</p>
              <span className="text-sm font-medium text-brand">Open QR page</span>
            </Link>
          ))}
        </div>

        {filteredTables.length === 0 ? (
          <p className="mt-4 rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No tables match the current search.</p>
        ) : null}
      </section>
    </main>
  );
}
