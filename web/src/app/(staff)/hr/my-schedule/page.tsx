"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, Field, errorMessage, formatDate } from "@/features/hr/ui";
import { IssuesList } from "@/features/hr/schedule/ShiftDialog";
import { MyTimeClock } from "@/features/hr/attendance/MyTimeClock";
import { MyTimesheets } from "@/features/hr/timesheets/MyTimesheets";
import { MyInterviews } from "@/features/hr/recruitment/MyInterviews";
import { DAY_CODES, shiftLabel, trimTime } from "@/features/hr/schedule/scheduleUtils";
import { addDays, formatMinutes, isoDate } from "@/features/workforce/time";
import {
  ScheduleIssuesError,
  addMyAvailability,
  cancelMySwap,
  deleteMyAvailability,
  getMyAvailability,
  getMyShifts,
  getMySwaps,
  getSwapOptions,
  requestSwap,
  type Availability,
  type AvailabilityKind,
  type ScheduleIssue,
  type Shift,
  type SwapOption,
  type SwapRequest,
  type WeekDay,
} from "@/lib/hrApi";

export default function MySchedulePage() {
  return (
    <HrGuard anyOf={["self"]} title="No employee record linked">
      <MySchedule />
    </HrGuard>
  );
}

function MySchedule() {
  const { authenticatedFetch } = useStaffSession();
  const { access } = useHrAccess();
  const [shifts, setShifts] = useState<Shift[] | null>(null);
  const [swaps, setSwaps] = useState<SwapRequest[]>([]);
  const [availability, setAvailability] = useState<Availability[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [swapFor, setSwapFor] = useState<Shift | null>(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      const from = isoDate(new Date());
      const to = isoDate(addDays(new Date(), 27));
      await authenticatedFetch(async (s) => {
        const [sh, sw, av] = await Promise.all([getMyShifts(s, from, to), getMySwaps(s), getMyAvailability(s)]);
        setShifts(sh);
        setSwaps(sw);
        setAvailability(av);
      });
    } catch (err) {
      setError(errorMessage(err, "Could not load your schedule"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function withdraw(id: string) {
    try {
      await authenticatedFetch((s) => cancelMySwap(s, id));
      setNotice("Swap request withdrawn.");
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not withdraw the request"));
    }
  }

  async function removeAvailability(id: string) {
    try {
      await authenticatedFetch((s) => deleteMyAvailability(s, id));
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not remove the entry"));
    }
  }

  const pendingShiftIds = new Set(swaps.filter((s) => s.status === "PENDING").map((s) => s.scheduleId));
  const totalMinutes = (shifts ?? []).reduce((sum, s) => sum + s.paidMinutes, 0);

  return (
    <div className="grid gap-6">
      <PageHeader eyebrow="Human Resources" title="My Schedule" icon="calendar" description="Your time clock, published shifts for the next four weeks, swap requests and availability." />
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      <MyTimeClock />

      <MyTimesheets />
      <MyInterviews />

      <section className="panel grid gap-3">
        <SectionHeader title="Upcoming shifts" description={shifts ? `${shifts.length} shift${shifts.length === 1 ? "" : "s"} · ${formatMinutes(totalMinutes)} paid` : undefined} />
        {!shifts ? <Skeleton className="h-32 w-full" /> : shifts.length === 0 ? <EmptyState compact icon="calendar" title="No published shifts in the next four weeks" /> : (
          <ul className="grid gap-2">
            {shifts.map((s) => (
              <li key={s.id} className="card flex flex-wrap items-center justify-between gap-3 p-3">
                <div>
                  <p className="font-semibold">{new Date(s.startsAt).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "short" })}</p>
                  <p className="text-sm text-ink-soft tabular">{shiftLabel(s)} · {formatMinutes(s.paidMinutes)} paid{s.templateName ? ` · ${s.templateName}` : ""}</p>
                  {s.notes ? <p className="text-xs text-ink-muted">{s.notes}</p> : null}
                </div>
                {pendingShiftIds.has(s.id) ? (
                  <span className="badge badge-pending">Swap pending</span>
                ) : new Date(s.startsAt) > new Date() ? (
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => setSwapFor(s)}>Request swap</button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel grid gap-3">
        <SectionHeader title="Swap requests" />
        {swaps.length === 0 ? <p className="text-sm text-ink-soft">No swap requests.</p> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Shift</th><th>With</th><th>Status</th><th className="num" /></tr></thead>
              <tbody>
                {swaps.map((r) => (
                  <tr key={r.id}>
                    <td>{r.startsAt ? `${formatDate(r.startsAt)} ${shiftLabel({ startsAt: r.startsAt, endsAt: r.endsAt! })}` : "—"}</td>
                    <td>{r.requesterName} → {r.colleagueName}{r.colleagueStartsAt ? <p className="text-xs text-ink-muted">in exchange for {formatDate(r.colleagueStartsAt)}</p> : null}</td>
                    <td><span className={`badge ${r.status === "APPROVED" ? "badge-success" : r.status === "PENDING" ? "badge-pending" : "badge-neutral"}`}>{humanize(r.status)}</span>{r.decisionNote ? <p className="text-xs text-ink-muted">{r.decisionNote}</p> : null}</td>
                    <td className="num">{r.status === "PENDING" && r.requesterEmployeeId === access?.employeeId ? <button type="button" className="btn btn-ghost btn-sm" onClick={() => withdraw(r.id)}>Withdraw</button> : null}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel grid gap-3">
        <SectionHeader
          title="Availability"
          description="Managers see this when building the schedule and get a warning if they put you on a shift you can't do."
          actions={<button type="button" className="btn btn-primary btn-sm" onClick={() => setAdding(true)}><Icon name="plus" size={16} /> Add</button>}
        />
        {availability.length === 0 ? <p className="text-sm text-ink-soft">No availability recorded.</p> : (
          <ul className="grid gap-2">
            {availability.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-line p-3 text-sm">
                <div>
                  <span className={`badge ${a.kind === "UNAVAILABLE" ? "badge-danger" : a.kind === "PREFERRED" ? "badge-success" : "badge-info"}`}>{humanize(a.kind)}</span>{" "}
                  <span className="font-semibold">{a.specificDate ? formatDate(a.specificDate) : `Every ${humanize(a.dayOfWeek ?? "")}`}</span>
                  {a.startTime ? <span className="tabular"> {trimTime(a.startTime)}–{trimTime(a.endTime)}</span> : <span> all day</span>}
                  {a.note ? <p className="text-xs text-ink-muted">{a.note}</p> : null}
                </div>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => removeAvailability(a.id)} aria-label="Remove"><Icon name="trash" size={16} /></button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {swapFor ? <SwapDialog shift={swapFor} onClose={() => setSwapFor(null)} onDone={() => { setSwapFor(null); setNotice("Swap requested. Your manager will review it."); void load(); }} /> : null}
      {adding ? <AvailabilityDialog onClose={() => setAdding(false)} onDone={() => { setAdding(false); void load(); }} /> : null}
    </div>
  );
}

function SwapDialog({ shift, onClose, onDone }: { shift: Shift; onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [options, setOptions] = useState<SwapOption[] | null>(null);
  const [colleague, setColleague] = useState("");
  const [theirShift, setTheirShift] = useState("");
  const [reason, setReason] = useState("");
  const [issues, setIssues] = useState<ScheduleIssue[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authenticatedFetch((s) => getSwapOptions(s, shift.id)).then(setOptions).catch((err) => { setOptions([]); setError(errorMessage(err, "Could not load colleagues")); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shift.id]);

  const chosen = options?.find((o) => o.employeeId === colleague);

  async function submit() {
    if (!colleague) return setError("Choose a colleague.");
    if (!reason.trim()) return setError("Give a reason.");
    setBusy(true);
    setError("");
    setIssues([]);
    try {
      await authenticatedFetch((s) => requestSwap(s, { scheduleId: shift.id, colleagueEmployeeId: colleague, colleagueScheduleId: theirShift || null, reason: reason.trim() }));
      onDone();
    } catch (err) {
      if (err instanceof ScheduleIssuesError) setIssues(err.issues);
      else setError(errorMessage(err, "Could not request the swap"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title="Request a shift swap"
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary btn-md" onClick={submit} disabled={busy || !options?.length}>{busy ? "Sending…" : "Send request"}</button>
        </>
      )}
    >
      <div className="grid gap-3 text-sm">
        <p>Your shift: <span className="font-semibold">{new Date(shift.startsAt).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "short" })} {shiftLabel(shift)}</span></p>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {issues.length ? <IssuesList issues={issues} /> : null}
        {options === null ? <Skeleton className="h-10 w-full" /> : options.length === 0 ? (
          <p className="text-ink-soft">No colleagues are assigned to this branch on that day.</p>
        ) : (
          <>
            <Field label="Colleague">
              <select className="select" value={colleague} onChange={(e) => { setColleague(e.target.value); setTheirShift(""); setIssues([]); }}>
                <option value="">Choose…</option>
                {options.map((o) => <option key={o.employeeId} value={o.employeeId}>{o.name}{o.positionTitle ? ` · ${o.positionTitle}` : ""}</option>)}
              </select>
            </Field>
            {chosen ? (
              <Field label="In exchange for" hint="Leave as handover if they simply take your shift">
                <select className="select" value={theirShift} onChange={(e) => setTheirShift(e.target.value)}>
                  <option value="">Nothing (handover)</option>
                  {chosen.shifts.map((s) => <option key={s.id} value={s.id}>{new Date(s.startsAt).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })} {shiftLabel(s)}</option>)}
                </select>
              </Field>
            ) : null}
            <Field label="Reason *"><input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} /></Field>
          </>
        )}
      </div>
    </Modal>
  );
}

function AvailabilityDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState({ kind: "UNAVAILABLE" as AvailabilityKind, repeat: "weekly" as "weekly" | "date", day: "MON" as WeekDay, date: isoDate(addDays(new Date(), 1)), allDay: true, start: "17:00", end: "22:00", note: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.allDay && form.start === form.end) return setError("Start and end time cannot be the same.");
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => addMyAvailability(s, {
        kind: form.kind,
        dayOfWeek: form.repeat === "weekly" ? form.day : null,
        specificDate: form.repeat === "date" ? form.date : null,
        startTime: form.allDay ? null : form.start,
        endTime: form.allDay ? null : form.end,
        note: form.note.trim() || undefined,
      }));
      onDone();
    } catch (err) {
      setError(errorMessage(err, "Could not save"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title="Add availability"
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="availability-form" className="btn btn-primary btn-md" disabled={busy}>{busy ? "Saving…" : "Save"}</button>
        </>
      )}
    >
      <form id="availability-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="I am">
          <select className="select" value={form.kind} onChange={(e) => setForm((f) => ({ ...f, kind: e.target.value as AvailabilityKind }))}>
            <option value="UNAVAILABLE">Not available</option>
            <option value="AVAILABLE">Available</option>
            <option value="PREFERRED">Preferred to work</option>
          </select>
        </Field>
        <Field label="When">
          <select className="select" value={form.repeat} onChange={(e) => setForm((f) => ({ ...f, repeat: e.target.value as "weekly" | "date" }))}>
            <option value="weekly">Every week</option>
            <option value="date">On one date</option>
          </select>
        </Field>
        {form.repeat === "weekly" ? (
          <Field label="Day">
            <select className="select" value={form.day} onChange={(e) => setForm((f) => ({ ...f, day: e.target.value as WeekDay }))}>
              {DAY_CODES.map((d) => <option key={d} value={d}>{humanize(d)}</option>)}
            </select>
          </Field>
        ) : (
          <Field label="Date"><input type="date" className="input" value={form.date} onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))} /></Field>
        )}
        <label className="flex items-center gap-2 self-end pb-3 text-sm">
          <input type="checkbox" checked={form.allDay} onChange={(e) => setForm((f) => ({ ...f, allDay: e.target.checked }))} /> All day
        </label>
        {!form.allDay ? (
          <>
            <Field label="From"><input type="time" className="input" value={form.start} onChange={(e) => setForm((f) => ({ ...f, start: e.target.value }))} /></Field>
            <Field label="To"><input type="time" className="input" value={form.end} onChange={(e) => setForm((f) => ({ ...f, end: e.target.value }))} /></Field>
          </>
        ) : null}
        <Field label="Note" className="sm:col-span-2"><input className="input" value={form.note} maxLength={300} onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))} placeholder="e.g. Evening classes" /></Field>
      </form>
    </Modal>
  );
}
