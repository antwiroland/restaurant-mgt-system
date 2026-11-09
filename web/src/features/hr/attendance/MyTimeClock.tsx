"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Field, errorMessage, formatDate } from "@/features/hr/ui";
import { addDays, isoDate } from "@/features/workforce/time";
import {
  getMyAttendance,
  getMyAttendanceStatus,
  getMyClockPin,
  newRequestKey,
  selfClock,
  setMyClockPin,
  type AttendanceEntry,
  type BreakType,
  type ClockAction,
  type PinStatus,
} from "@/lib/hrApi";
import { metricsLine, statusTone, timeRange } from "./AttendancePanels";

/** Employee self-service: clock from their own account (when the branch allows it), their PIN, and recent attendance. */
export function MyTimeClock() {
  const { authenticatedFetch } = useStaffSession();
  const [current, setCurrent] = useState<AttendanceEntry | null>(null);
  const [history, setHistory] = useState<AttendanceEntry[]>([]);
  const [pin, setPin] = useState<PinStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [pinForm, setPinForm] = useState<{ current: string; next: string } | null>(null);

  const load = useCallback(async () => {
    try {
      await authenticatedFetch(async (s) => {
        const [status, recent, pinStatus] = await Promise.all([
          getMyAttendanceStatus(s),
          getMyAttendance(s, isoDate(addDays(new Date(), -13)), isoDate(new Date())),
          getMyClockPin(s),
        ]);
        setCurrent(status ?? null);
        setHistory(recent);
        setPin(pinStatus);
      });
    } catch (err) {
      setError(errorMessage(err, "Could not load your attendance"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function act(action: ClockAction, breakType?: BreakType) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await authenticatedFetch((s) => selfClock(s, { action, breakType, idempotencyKey: newRequestKey() }));
      setMessage(result.message);
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not record that"));
    } finally {
      setBusy(false);
    }
  }

  async function savePin(event: FormEvent) {
    event.preventDefault();
    if (!pinForm) return;
    setError("");
    try {
      setPin(await authenticatedFetch((s) => setMyClockPin(s, { currentPin: pinForm.current || undefined, newPin: pinForm.next })));
      setPinForm(null);
      setMessage("Clock PIN updated. Use it at the POS staff clock.");
    } catch (err) {
      setError(errorMessage(err, "Could not change your PIN"));
    }
  }

  return (
    <section className="panel grid gap-3">
      <SectionHeader
        title="Time clock"
        description={current ? `Clocked in since ${timeRange(current.clockInAt)}${current.onBreak ? " · on break" : ""}` : "Not clocked in"}
        actions={(
          <div className="flex flex-wrap gap-2">
            {!current ? <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={() => act("CLOCK_IN")}>Clock in</button> : null}
            {current && !current.onBreak ? <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => act("BREAK_START", "MEAL")}>Start break</button> : null}
            {current?.onBreak ? <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => act("BREAK_END")}>End break</button> : null}
            {current ? <button type="button" className="btn btn-danger btn-sm" disabled={busy} onClick={() => act("CLOCK_OUT")}>Clock out</button> : null}
          </div>
        )}
      />
      {message ? <p className="alert alert-success" role="status">{message}</p> : null}
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}

      <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-line p-3 text-sm">
        <span>POS clock PIN: <span className="font-semibold">{pin?.set ? "set" : "not set"}</span>{pin?.lockedUntil ? " (locked — try later or ask a manager)" : ""}</span>
        {!pinForm ? <button type="button" className="btn btn-ghost btn-sm" onClick={() => setPinForm({ current: "", next: "" })}>{pin?.set ? "Change PIN" : "Set PIN"}</button> : null}
      </div>
      {pinForm ? (
        <form onSubmit={savePin} className="flex flex-wrap items-end gap-2">
          {pin?.set ? (
            <Field label="Current PIN">
              <input className="input w-32 tabular" type="password" inputMode="numeric" autoComplete="current-password" value={pinForm.current}
                onChange={(e) => setPinForm((f) => (f ? { ...f, current: e.target.value.replace(/\D/g, "").slice(0, 6) } : f))} />
            </Field>
          ) : null}
          <Field label="New PIN (4–6 digits)">
            <input className="input w-32 tabular" type="password" inputMode="numeric" autoComplete="new-password" value={pinForm.next}
              onChange={(e) => setPinForm((f) => (f ? { ...f, next: e.target.value.replace(/\D/g, "").slice(0, 6) } : f))} />
          </Field>
          <button type="submit" className="btn btn-primary btn-md" disabled={pinForm.next.length < 4}>Save</button>
          <button type="button" className="btn btn-ghost btn-md" onClick={() => setPinForm(null)}>Cancel</button>
        </form>
      ) : null}

      {history.length ? (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Date</th><th>Shift</th><th>Clocked</th><th>Time</th><th>Status</th></tr></thead>
            <tbody>
              {history.map((e) => (
                <tr key={e.id}>
                  <td>{formatDate(e.clockInAt)}</td>
                  <td className="tabular">{e.shiftStartsAt ? timeRange(e.shiftStartsAt, e.shiftEndsAt) : "—"}</td>
                  <td className="tabular">{timeRange(e.clockInAt, e.clockOutAt)}{e.editReason ? <p className="text-xs text-ink-muted">Corrected: {e.editReason}</p> : null}</td>
                  <td className="text-sm">{metricsLine(e)}</td>
                  <td><span className={`badge ${statusTone[e.status] ?? "badge-neutral"}`}>{humanize(e.status)}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : <p className="text-sm text-ink-soft">No attendance in the last two weeks.</p>}
      <p className="text-xs text-ink-muted">Something wrong? Ask your manager to correct it — the original times stay on record.</p>
    </section>
  );
}
