"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { getAuditEvents, type AuditRecord } from "@/lib/apiClient";

export default function AuditPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [events, setEvents] = useState<AuditRecord[]>([]);
  const [query, setQuery] = useState("");
  const [action, setAction] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [error, setError] = useState("");
  const ACTIONS = ["", "USER_LOGIN", "USER_LOGOUT", "ROLE_ASSIGNED", "PIN_VERIFIED", "PIN_FAILED", "PIN_LOCKED", "PAYMENT_REFUNDED", "ORDER_VOIDED", "DISCOUNT_APPLIED"];
  const filteredEvents = events.filter((event) => {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) return true;
    return (event.actorName?.toLowerCase().includes(normalizedQuery) ?? false)
      || (event.actorId?.toLowerCase().includes(normalizedQuery) ?? false)
      || event.action.toLowerCase().includes(normalizedQuery)
      || (event.entityType?.toLowerCase().includes(normalizedQuery) ?? false)
      || (event.entityId?.toLowerCase().includes(normalizedQuery) ?? false)
      || event.metadata.toLowerCase().includes(normalizedQuery);
  });

  async function loadEvents() {
    if (!session) return;
    const data = await authenticatedFetch((activeSession) => getAuditEvents(activeSession, {
      action: action || undefined,
      from: from ? new Date(from).toISOString() : undefined,
      to: to ? new Date(to).toISOString() : undefined,
    }));
    setEvents(data);
  }

  useEffect(() => {
    if (!session) return;
    loadEvents().catch((err) => setError(err instanceof Error ? err.message : "Could not load audit events"));
  }, [session]);

  async function onFilter(event: FormEvent) {
    event.preventDefault();
    try {
      setError("");
      await loadEvents();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not filter audit events");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading audit...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Audit</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Audit</p>
        <h1 className="text-2xl font-semibold">Audit Events</h1>
        <form className="mt-3 flex flex-wrap gap-2" onSubmit={(event) => void onFilter(event)}>
          <select className="input" value={action} onChange={(e) => setAction(e.target.value)}>
            {ACTIONS.map((value) => <option key={value || "ALL"} value={value}>{value || "All actions"}</option>)}
          </select>
          <input className="input" type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
          <input className="input" type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} />
          <input className="input" placeholder="Search actor, entity, or metadata" value={query} onChange={(e) => setQuery(e.target.value)} />
          <button className="btn btn-primary">Apply</button>
        </form>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
      </section>

      <section className="panel">
        <ul className="grid gap-2">
          {filteredEvents.map((event, index) => (
            <li key={`${event.id ?? event.actorId}-${event.createdAt}-${index}`} className="input">
              <p className="font-semibold">{event.action}</p>
              <p className="text-xs text-ink-soft">Actor: {event.actorName ?? event.actorId ?? "system"}</p>
              <p className="text-xs text-ink-soft">At: {new Date(event.createdAt).toLocaleString()}</p>
              {event.entityType ? <p className="text-xs text-ink-soft">Entity: {event.entityType} {event.entityId ?? ""}</p> : null}
              <p className="mt-1 text-xs text-ink-soft break-all">{event.metadata}</p>
            </li>
          ))}
          {filteredEvents.length === 0 ? <li className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No audit events match the current filters.</li> : null}
        </ul>
      </section>
    </main>
  );
}



