"use client";

import Link from "next/link";
import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import {
  clockIn,
  clockOut,
  editTimeEntry,
  getClockStatus,
  getMySchedules,
  getMyTimesheet,
  getSchedules,
  getTimesheet,
  type ScheduleRecord,
  type TimeEntryRecord,
  type TimesheetRowRecord,
} from "@/lib/apiClient";
import { addDays, formatMinutes, formatTime, isoDate, sameDay, startOfWeek, toLocalInput } from "@/features/workforce/time";

type EntryDraft = { entry: TimeEntryRecord; staffName: string; clockInAt: string; clockOutAt: string; breakMinutes: string; reason: string };

export default function SchedulePage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const isManager = session?.user.role === "ADMIN" || session?.user.role === "MANAGER";

  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()));
  const [shifts, setShifts] = useState<ScheduleRecord[]>([]);
  const [clock, setClock] = useState<TimeEntryRecord | null>(null);
  const [breakMinutes, setBreakMinutes] = useState("0");
  const [clockNote, setClockNote] = useState("");
  const [timesheet, setTimesheet] = useState<TimesheetRowRecord[]>([]);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [entryDraft, setEntryDraft] = useState<EntryDraft | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)), [weekStart]);
  const weekEnd = addDays(weekStart, 7);

  const load = useCallback(async () => {
    if (!session) return;
    setError("");
    const from = weekStart.toISOString();
    const to = addDays(weekStart, 7).toISOString();
    const fromDate = isoDate(weekStart);
    const toDate = isoDate(addDays(weekStart, 6));
    try {
      const [nextShifts, status, sheet] = await Promise.all([
        authenticatedFetch((s) => (isManager ? getSchedules(s, from, to) : getMySchedules(s, from, to))),
        authenticatedFetch((s) => getClockStatus(s)),
        authenticatedFetch((s) => (isManager ? getTimesheet(s, fromDate, toDate) : getMyTimesheet(s, fromDate, toDate))),
      ]);
      setShifts(nextShifts);
      setClock(status ?? null);
      setTimesheet(sheet);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the schedule");
    } finally {
      setLoaded(true);
    }
  }, [authenticatedFetch, isManager, session, weekStart]);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(task: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await task();
      setMessage(success);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  function handleClockIn() {
    void run(async () => {
      await authenticatedFetch((s) => clockIn(s, clockNote.trim() || undefined));
      setClockNote("");
    }, "Clocked in");
  }

  function handleClockOut() {
    void run(async () => {
      await authenticatedFetch((s) => clockOut(s, Number(breakMinutes || 0)));
      setBreakMinutes("0");
    }, "Clocked out");
  }

  function saveEntry(event: React.FormEvent) {
    event.preventDefault();
    if (!entryDraft) return;
    const draft = entryDraft;
    void run(async () => {
      await authenticatedFetch((s) =>
        editTimeEntry(s, draft.entry.id, {
          clockInAt: draft.clockInAt ? new Date(draft.clockInAt).toISOString() : undefined,
          clockOutAt: draft.clockOutAt ? new Date(draft.clockOutAt).toISOString() : undefined,
          breakMinutes: Number(draft.breakMinutes || 0),
          reason: draft.reason.trim(),
        }),
      );
      setEntryDraft(null);
    }, "Time entry corrected");
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-48 w-full" /></section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Schedule</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  const today = new Date();
  const totals = timesheet.reduce(
    (acc, row) => ({ worked: acc.worked + row.workedMinutes, scheduled: acc.scheduled + row.scheduledMinutes, overtime: acc.overtime + row.overtimeMinutes }),
    { worked: 0, scheduled: 0, overtime: 0 },
  );

  return (
    <main className="shell grid gap-4">
      <section className="panel grid gap-3 md:grid-cols-[1fr_auto] md:items-center">
        <div>
          <p className="kicker">Workforce</p>
          <h1 className="text-2xl font-semibold">{isManager ? "Staff schedule and timesheets" : "My shifts and hours"}</h1>
          {isManager ? (
            <p className="mt-1 text-sm">
              Plan and change shifts in <Link href="/hr/schedule" className="font-semibold underline">HR › Schedule</Link>: it checks leave,
              rest time, weekly hours and availability, lets you publish the week and tells staff. This page shows the published rota.
            </p>
          ) : null}
          <p className="text-sm text-ink-soft">
            Clock in when you start work and out when you finish. Arriving more than 5 minutes after a scheduled start counts as late;
            over 8 hours in a day counts as overtime.
          </p>
        </div>
        <div className="card grid gap-2 md:min-w-72" aria-live="polite">
          {clock?.open ? (
            <>
              <p className="text-sm">
                <span className="badge badge-success">On the clock</span> since {formatTime(clock.clockInAt)} ·{" "}
                {formatMinutes(clock.workedMinutes)}
                {clock.lateMinutes > 0 ? <span className="text-warning-on"> · {clock.lateMinutes} min late</span> : null}
              </p>
              <label className="grid gap-1 text-sm">
                <span>Break taken (minutes)</span>
                <input className="input" type="number" min={0} max={600} value={breakMinutes} onChange={(e) => setBreakMinutes(e.target.value)} />
              </label>
              <button type="button" className="btn btn-danger" disabled={busy} onClick={handleClockOut}>Clock out</button>
            </>
          ) : (
            <>
              <p className="text-sm"><span className="badge badge-neutral">Off the clock</span></p>
              <label className="grid gap-1 text-sm">
                <span>Note (optional)</span>
                <input className="input" maxLength={255} value={clockNote} onChange={(e) => setClockNote(e.target.value)} />
              </label>
              <button type="button" className="btn btn-primary" disabled={busy} onClick={handleClockIn}>Clock in</button>
            </>
          )}
        </div>
      </section>

      <div aria-live="polite">
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {message ? <p className="alert alert-success">{message}</p> : null}
      </div>

      <section className="panel grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-xl font-semibold">
            Week of {weekStart.toLocaleDateString(undefined, { day: "numeric", month: "short" })} –{" "}
            {addDays(weekEnd, -1).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}
          </h2>
          <div className="flex gap-2">
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setWeekStart(addDays(weekStart, -7))}>Previous</button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setWeekStart(startOfWeek(new Date()))}>This week</button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setWeekStart(addDays(weekStart, 7))}>Next</button>
          </div>
        </div>

        {!loaded ? (
          <Skeleton className="h-48 w-full" />
        ) : (
          <div className="grid gap-2 md:grid-cols-7">
            {days.map((day) => {
              const dayShifts = shifts
                .filter((shift) => sameDay(new Date(shift.startsAt), day))
                .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
              return (
                <section key={day.toISOString()} className={`card grid content-start gap-2 ${sameDay(day, today) ? "border-brand" : ""}`} aria-label={day.toDateString()}>
                  <p className="text-sm font-semibold">
                    {day.toLocaleDateString(undefined, { weekday: "short" })}{" "}
                    <span className="text-ink-soft">{day.getDate()}</span>
                  </p>
                  {dayShifts.length === 0 ? <p className="text-xs text-ink-soft">No shifts</p> : null}
                  {dayShifts.map((shift) => (
                    <div key={shift.id} className="rounded-md bg-brand-subtle p-2 text-xs">
                      <p className="font-semibold text-ink">{shift.userName}</p>
                      <p>{formatTime(shift.startsAt)}–{formatTime(shift.endsAt)}</p>
                      {shift.position ? <p className="text-ink-soft">{shift.position}</p> : null}
                    </div>
                  ))}

                </section>
              );
            })}
          </div>
        )}
      </section>

      <section className="panel grid gap-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-xl font-semibold">Timesheet</h2>
          <p className="text-sm text-ink-soft">
            Worked {formatMinutes(totals.worked)} of {formatMinutes(totals.scheduled)} scheduled · overtime {formatMinutes(totals.overtime)}
            {isManager ? <> · <Link href="/reports" className="underline">Export</Link></> : null}
          </p>
        </div>
        {timesheet.length === 0 ? (
          <p className="text-sm text-ink-soft">No hours recorded this week.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-ink-soft">
                <tr>
                  <th scope="col" className="py-2 pr-3">Staff</th>
                  <th scope="col" className="py-2 pr-3">Worked</th>
                  <th scope="col" className="py-2 pr-3">Scheduled</th>
                  <th scope="col" className="py-2 pr-3">Overtime</th>
                  <th scope="col" className="py-2 pr-3">Late</th>
                  <th scope="col" className="py-2 pr-3">Missed</th>
                  <th scope="col" className="py-2"><span className="sr-only">Details</span></th>
                </tr>
              </thead>
              <tbody>
                {timesheet.map((row) => (
                  <Fragment key={row.userId}>
                    <tr className="border-t border-line">
                      <td className="py-2 pr-3 font-medium">{row.name} <span className="text-xs text-ink-soft">{row.role}</span></td>
                      <td className="py-2 pr-3">{formatMinutes(row.workedMinutes)}</td>
                      <td className="py-2 pr-3">{formatMinutes(row.scheduledMinutes)}</td>
                      <td className="py-2 pr-3">{row.overtimeMinutes > 0 ? formatMinutes(row.overtimeMinutes) : "-"}</td>
                      <td className="py-2 pr-3">{row.lateArrivals || "-"}</td>
                      <td className="py-2 pr-3">{row.missedShifts || "-"}</td>
                      <td className="py-2 text-right">
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          aria-expanded={expanded === row.userId}
                          onClick={() => setExpanded(expanded === row.userId ? null : row.userId)}
                        >
                          {row.entryCount} {row.entryCount === 1 ? "entry" : "entries"}
                        </button>
                      </td>
                    </tr>
                    {expanded === row.userId ? (
                      <tr>
                        <td colSpan={7} className="pb-3">
                          <ul className="grid gap-1">
                            {row.entries.map((entry) => (
                              <li key={entry.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-sunken px-3 py-2">
                                <span>
                                  {new Date(entry.clockInAt).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })}{" "}
                                  {formatTime(entry.clockInAt)}–{entry.clockOutAt ? formatTime(entry.clockOutAt) : "now"} · {formatMinutes(entry.workedMinutes)}
                                  {entry.breakMinutes ? ` (break ${entry.breakMinutes}m)` : ""}
                                  {entry.lateMinutes > 0 ? <span className="text-warning-on"> · {entry.lateMinutes}m late</span> : null}
                                  {entry.editReason ? <span className="block text-xs text-ink-soft">Corrected: {entry.editReason}</span> : null}
                                </span>
                                {isManager ? (
                                  <button
                                    type="button"
                                    className="btn btn-secondary btn-sm"
                                    onClick={() =>
                                      setEntryDraft({
                                        entry,
                                        staffName: row.name,
                                        clockInAt: toLocalInput(entry.clockInAt),
                                        clockOutAt: toLocalInput(entry.clockOutAt),
                                        breakMinutes: String(entry.breakMinutes),
                                        reason: "",
                                      })
                                    }
                                  >
                                    Correct
                                  </button>
                                ) : null}
                              </li>
                            ))}
                          </ul>
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <Drawer open={entryDraft !== null} title={entryDraft ? `Correct ${entryDraft.staffName}'s time` : "Correct time"} onClose={() => setEntryDraft(null)}>
        {entryDraft ? (
          <form className="grid gap-2" onSubmit={saveEntry}>
            <label className="grid gap-1 text-sm">
              <span>Clocked in</span>
              <input className="input" type="datetime-local" required value={entryDraft.clockInAt} onChange={(e) => setEntryDraft({ ...entryDraft, clockInAt: e.target.value })} />
            </label>
            <label className="grid gap-1 text-sm">
              <span>Clocked out</span>
              <input className="input" type="datetime-local" value={entryDraft.clockOutAt} onChange={(e) => setEntryDraft({ ...entryDraft, clockOutAt: e.target.value })} />
            </label>
            <label className="grid gap-1 text-sm">
              <span>Break (minutes)</span>
              <input className="input" type="number" min={0} value={entryDraft.breakMinutes} onChange={(e) => setEntryDraft({ ...entryDraft, breakMinutes: e.target.value })} />
            </label>
            <label className="grid gap-1 text-sm">
              <span>Reason (kept with the entry)</span>
              <input className="input" required maxLength={500} value={entryDraft.reason} onChange={(e) => setEntryDraft({ ...entryDraft, reason: e.target.value })} />
            </label>
            <button type="submit" className="btn btn-primary" disabled={busy}>Save correction</button>
          </form>
        ) : null}
      </Drawer>

    </main>
  );
}
