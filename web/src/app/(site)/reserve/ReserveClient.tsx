"use client";

import { useEffect, useMemo, useState } from "react";
import {
  cancelGuestReservation,
  createGuestReservation,
  getPublicTableStatus,
  lookupGuestReservations,
  type ReservationRecord,
  type TablePublicStatusView,
} from "@/lib/apiClient";
import { ConfirmModal } from "@/components/ConfirmModal";
import { Icon } from "@/components/Icon";
import { Spinner } from "@/components/Spinner";
import { useBrand } from "@/components/BrandProvider";

type Tab = "book" | "manage";

const TIMES = Array.from({ length: 26 }, (_, i) => {
  const minutes = 10 * 60 + i * 30; // 10:00 → 22:30
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
});

function isoDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function formatWhen(iso: string) {
  return new Date(iso).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
}

export function ReserveClient() {
  const [tab, setTab] = useState<Tab>("book");

  // Follow #manage links (footer, confirmation) — also while already on the page.
  useEffect(() => {
    const sync = () => {
      if (window.location.hash === "#manage") setTab("manage");
    };
    const frame = window.requestAnimationFrame(sync);
    window.addEventListener("hashchange", sync);
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener("hashchange", sync);
    };
  }, []);

  return (
    <div className="grid gap-8">
      <div className="segmented w-fit" role="tablist" aria-label="Reservations">
        <button type="button" role="tab" aria-selected={tab === "book"} onClick={() => setTab("book")}>
          <Icon name="calendar" size={17} /> Book a table
        </button>
        <button type="button" role="tab" aria-selected={tab === "manage"} onClick={() => setTab("manage")} id="manage">
          <Icon name="search" size={17} /> Manage booking
        </button>
      </div>
      {tab === "book" ? <BookForm /> : <ManageBookings />}
    </div>
  );
}

/* ------------------------------------------------------------------ */

function BookForm() {
  const brand = useBrand();
  const today = isoDate(new Date());
  const [tables, setTables] = useState<TablePublicStatusView[] | null>(null);
  const [tablesError, setTablesError] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [party, setParty] = useState(2);
  const [date, setDate] = useState(today);
  const [time, setTime] = useState("19:00");
  const [tableId, setTableId] = useState("");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [confirmed, setConfirmed] = useState<ReservationRecord | null>(null);

  useEffect(() => {
    getPublicTableStatus()
      .then(setTables)
      .catch(() => setTablesError("We couldn't load seating right now. Please refresh, or call us to book."));
  }, []);

  const bookable = useMemo(() => (tables ?? []).filter((t) => t.id && t.status !== "CLOSED"), [tables]);
  const fitting = useMemo(() => bookable.filter((t) => t.capacity >= party).sort((a, b) => a.capacity - b.capacity), [bookable, party]);
  const missingIds = tables !== null && tables.length > 0 && bookable.length === 0 && tables.some((t) => t.status !== "CLOSED");

  useEffect(() => {
    if (tableId && !fitting.some((t) => t.id === tableId)) setTableId("");
  }, [fitting, tableId]);

  const slotsForDate = useMemo(() => {
    if (date !== today) return TIMES;
    const now = new Date();
    const minutesNow = now.getHours() * 60 + now.getMinutes() + 30;
    return TIMES.filter((t) => {
      const [h, m] = t.split(":").map(Number);
      return h * 60 + m > minutesNow;
    });
  }, [date, today]);

  useEffect(() => {
    if (!slotsForDate.includes(time) && slotsForDate.length > 0) setTime(slotsForDate[0]);
  }, [slotsForDate, time]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!name.trim()) next.name = "Please tell us your name";
    if (!/^\+?[0-9\s-]{7,20}$/.test(phone.trim())) next.phone = "Enter a valid phone number";
    if (!tableId) next.table = "Choose a table";
    if (!slotsForDate.includes(time)) next.time = "Choose a time later today, or another date";
    setErrors(next);
    if (Object.keys(next).length) return;

    setSubmitting(true);
    setSubmitError("");
    try {
      const reservation = await createGuestReservation({
        tableId,
        customerName: name.trim(),
        customerPhone: phone.replace(/\s+/g, ""),
        partySize: party,
        reservedAt: new Date(`${date}T${time}:00`).toISOString(),
        durationMins: 90,
        notes: notes.trim() || undefined,
      });
      setConfirmed(reservation);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "We couldn't make that booking. Please try another time or table.");
    } finally {
      setSubmitting(false);
    }
  }

  if (confirmed) {
    return (
      <section className="mx-auto w-full max-w-xl animate-fade-up rounded-[24px] border border-line bg-surface p-8 text-center shadow-lg">
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-full bg-success-subtle text-success"><Icon name="check" size={28} strokeWidth={2.25} /></span>
        <h2 className="heading-serif mt-5 text-3xl">You&apos;re booked in</h2>
        <p className="mt-2 text-ink-soft">We look forward to welcoming you, {confirmed.customerName ?? name}.</p>
        <dl className="mt-7 grid grid-cols-3 gap-3 rounded-xl bg-ivory p-4 text-left text-sm">
          <div><dt className="kicker">When</dt><dd className="mt-1 font-semibold text-ink">{formatWhen(confirmed.reservedAt)}</dd></div>
          <div><dt className="kicker">Guests</dt><dd className="mt-1 font-semibold text-ink">{confirmed.partySize}</dd></div>
          <div><dt className="kicker">Table</dt><dd className="mt-1 font-semibold text-ink">{confirmed.tableNumber}</dd></div>
        </dl>
        <p className="mt-5 text-sm text-ink-muted">Status: {confirmed.status.toLowerCase()}. Use your phone number under “Manage booking” to view or cancel.</p>
        <button type="button" className="btn btn-secondary mt-6" onClick={() => { setConfirmed(null); setNotes(""); setTableId(""); }}>
          Make another booking
        </button>
      </section>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-8 lg:grid-cols-[1.2fr_1fr]">
      <div className="grid content-start gap-6 rounded-[24px] border border-line bg-surface p-6 sm:p-8">
        <fieldset className="grid gap-4">
          <legend className="font-display text-lg font-bold">When are you coming?</legend>
          <div className="grid gap-4 sm:grid-cols-[1fr_1fr]">
            <label className="field">
              <span className="field-label">Date</span>
              <input className="input" type="date" min={today} value={date} onChange={(e) => setDate(e.target.value)} />
            </label>
            <div className="field">
              <span className="field-label" id="party-label">Guests</span>
              <div className="flex items-center gap-2" role="group" aria-labelledby="party-label">
                <button type="button" className="btn btn-secondary btn-icon" onClick={() => setParty((p) => Math.max(1, p - 1))} aria-label="Fewer guests"><Icon name="minus" size={18} /></button>
                <output className="min-w-[3ch] flex-1 text-center font-display text-xl font-bold tabular" aria-live="polite">{party}</output>
                <button type="button" className="btn btn-secondary btn-icon" onClick={() => setParty((p) => Math.min(30, p + 1))} aria-label="More guests"><Icon name="plus" size={18} /></button>
              </div>
            </div>
          </div>
          <div className="field">
            <span className="field-label">Time</span>
            {slotsForDate.length === 0 ? (
              <p className="alert alert-info">No more times today — please choose another date.</p>
            ) : (
              <div className="grid grid-cols-4 gap-2 sm:grid-cols-6" role="radiogroup" aria-label="Time">
                {slotsForDate.map((slot) => (
                  <button
                    key={slot}
                    type="button"
                    role="radio"
                    aria-checked={time === slot}
                    onClick={() => setTime(slot)}
                    className={`min-h-[42px] rounded-lg border text-sm font-semibold tabular transition-colors ${
                      time === slot ? "border-espresso bg-espresso text-espresso-ink" : "border-line bg-surface text-ink-soft hover:border-line-strong hover:text-ink"
                    }`}
                  >
                    {slot}
                  </button>
                ))}
              </div>
            )}
            {errors.time ? <span className="field-error" role="alert">{errors.time}</span> : null}
          </div>
        </fieldset>

        <fieldset className="grid gap-3">
          <legend className="font-display text-lg font-bold">Choose your table</legend>
          {tablesError ? <p className="alert alert-danger">{tablesError}</p> : null}
          {tables === null && !tablesError ? (
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">{Array.from({ length: 6 }, (_, i) => <div key={i} className="skeleton h-[72px] rounded-xl" />)}</div>
          ) : null}
          {missingIds ? (
            <p className="alert alert-warning">Online table selection isn&apos;t enabled yet. {brand.phone ? `Please call ${brand.phone} to book.` : "Please contact the restaurant to book."}</p>
          ) : null}
          {tables !== null && !missingIds && fitting.length === 0 && !tablesError ? (
            <p className="alert alert-info">No single table seats {party}. For larger parties, please contact us directly.</p>
          ) : null}
          {fitting.length > 0 ? (
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Table">
              {fitting.map((table) => (
                <button
                  key={table.id}
                  type="button"
                  role="radio"
                  aria-checked={tableId === table.id}
                  onClick={() => setTableId(table.id!)}
                  className={`grid gap-0.5 rounded-xl border p-3 text-left transition-colors ${
                    tableId === table.id ? "border-brand bg-brand-subtle ring-1 ring-brand" : "border-line bg-surface hover:border-line-strong"
                  }`}
                >
                  <span className="font-display font-bold text-ink">Table {table.number}</span>
                  <span className="text-xs text-ink-soft">Seats {table.capacity}{table.zone ? ` · ${table.zone}` : ""}</span>
                </button>
              ))}
            </div>
          ) : null}
          {errors.table ? <span className="field-error" role="alert">{errors.table}</span> : null}
        </fieldset>
      </div>

      <div className="grid content-start gap-6 rounded-[24px] border border-line bg-surface p-6 sm:p-8 lg:sticky lg:top-24">
        <fieldset className="grid gap-4">
          <legend className="font-display text-lg font-bold">Your details</legend>
          <label className="field">
            <span className="field-label">Full name</span>
            <input className="input" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} aria-invalid={errors.name ? true : undefined} aria-describedby={errors.name ? "r-name-err" : undefined} />
            {errors.name ? <span id="r-name-err" className="field-error" role="alert">{errors.name}</span> : null}
          </label>
          <label className="field">
            <span className="field-label">Phone number</span>
            <input className="input" type="tel" inputMode="tel" autoComplete="tel" placeholder="+233 20 000 0000" value={phone} onChange={(e) => setPhone(e.target.value)} aria-invalid={errors.phone ? true : undefined} aria-describedby={errors.phone ? "r-phone-err" : "r-phone-hint"} />
            {errors.phone ? <span id="r-phone-err" className="field-error" role="alert">{errors.phone}</span> : <span id="r-phone-hint" className="field-hint">You&apos;ll use this to manage your booking.</span>}
          </label>
          <label className="field">
            <span className="field-label">Special requests <span className="font-normal text-ink-muted">(optional)</span></span>
            <textarea className="textarea" maxLength={1000} placeholder="Birthday, high chair, allergies…" value={notes} onChange={(e) => setNotes(e.target.value)} />
          </label>
        </fieldset>

        <div className="rounded-xl bg-ivory p-4 text-sm">
          <p className="kicker">Summary</p>
          <p className="mt-2 font-semibold text-ink">
            {new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" })} · {time}
          </p>
          <p className="text-ink-soft">
            {party} {party === 1 ? "guest" : "guests"}
            {tableId ? ` · Table ${fitting.find((t) => t.id === tableId)?.number ?? ""}` : ""}
          </p>
        </div>

        {submitError ? <p className="alert alert-danger" role="alert"><Icon name="alert" size={18} className="mt-0.5 shrink-0" />{submitError}</p> : null}
        <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={submitting || missingIds}>
          {submitting ? <Spinner /> : null}
          {submitting ? "Booking…" : "Confirm reservation"}
        </button>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------------ */

function ManageBookings() {
  const [phone, setPhone] = useState("");
  const [results, setResults] = useState<ReservationRecord[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [toCancel, setToCancel] = useState<ReservationRecord | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [notice, setNotice] = useState("");

  async function lookup(event?: React.FormEvent) {
    event?.preventDefault();
    if (!phone.trim()) {
      setError("Enter the phone number you booked with");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const rows = await lookupGuestReservations(phone.replace(/\s+/g, ""));
      setResults(rows.sort((a, b) => a.reservedAt.localeCompare(b.reservedAt)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Lookup failed");
    } finally {
      setLoading(false);
    }
  }

  async function confirmCancel() {
    if (!toCancel) return;
    setCancelling(true);
    try {
      await cancelGuestReservation(toCancel.id, phone.replace(/\s+/g, ""));
      setNotice(`Your booking for ${formatWhen(toCancel.reservedAt)} has been cancelled.`);
      setToCancel(null);
      await lookup();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Cancellation failed");
      setToCancel(null);
    } finally {
      setCancelling(false);
    }
  }

  return (
    <section className="mx-auto grid w-full max-w-2xl gap-6">
      <form onSubmit={lookup} className="grid gap-3 rounded-[24px] border border-line bg-surface p-6 sm:p-8" noValidate>
        <h2 className="font-display text-lg font-bold">Find your bookings</h2>
        <p className="text-sm text-ink-soft">Enter the phone number you used when booking.</p>
        <div className="flex flex-col gap-2 sm:flex-row">
          <label className="flex-1">
            <span className="sr-only">Phone number</span>
            <input className="input" type="tel" inputMode="tel" autoComplete="tel" placeholder="+233 20 000 0000" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </label>
          <button type="submit" className="btn btn-primary btn-md" disabled={loading}>{loading ? <Spinner /> : <Icon name="search" size={18} />} Find</button>
        </div>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      </form>

      {results !== null ? (
        results.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-line-strong bg-surface p-8 text-center text-ink-soft">No bookings found for that number.</p>
        ) : (
          <ul className="grid gap-3">
            {results.map((r) => {
              const past = new Date(r.reservedAt).getTime() < Date.now();
              const cancellable = r.status !== "CANCELLED" && !past;
              return (
                <li key={r.id} className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-line bg-surface p-5">
                  <div className="flex items-center gap-4">
                    <span className="grid h-14 w-14 shrink-0 place-items-center rounded-xl bg-cream text-center leading-none">
                      <span>
                        <span className="block text-[11px] font-bold uppercase text-brand">{new Date(r.reservedAt).toLocaleDateString(undefined, { month: "short" })}</span>
                        <span className="font-display text-xl font-extrabold text-ink">{new Date(r.reservedAt).getDate()}</span>
                      </span>
                    </span>
                    <div>
                      <p className="font-display font-bold text-ink">{formatWhen(r.reservedAt)}</p>
                      <p className="text-sm text-ink-soft">{r.partySize} guests · Table {r.tableNumber}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`badge badge-dot ${r.status === "CONFIRMED" ? "badge-success" : r.status === "CANCELLED" ? "badge-neutral" : "badge-pending"}`}>{r.status.toLowerCase()}</span>
                    {cancellable ? <button type="button" className="btn btn-danger btn-sm" onClick={() => setToCancel(r)}>Cancel</button> : null}
                  </div>
                </li>
              );
            })}
          </ul>
        )
      ) : null}

      <ConfirmModal
        open={!!toCancel}
        title="Cancel this booking?"
        message={toCancel ? `Your table for ${toCancel.partySize} on ${formatWhen(toCancel.reservedAt)} will be released.` : ""}
        confirmLabel="Cancel booking"
        cancelLabel="Keep it"
        danger
        busy={cancelling}
        onConfirm={() => void confirmCancel()}
        onClose={() => setToCancel(null)}
      />
    </section>
  );
}
