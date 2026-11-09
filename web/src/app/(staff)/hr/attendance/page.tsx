"use client";

import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, StatCard, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, errorMessage } from "@/features/hr/ui";
import { AttendanceRulesPanel, CorrectionDialog, CorrectionsPanel, metricsLine, statusTone, timeRange } from "@/features/hr/attendance/AttendancePanels";
import { addDays, isoDate } from "@/features/workforce/time";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import { clockForEmployee, getAttendanceDay, newRequestKey, type AttendanceDay, type AttendanceEntry, type ClockAction, type DayRow } from "@/lib/hrApi";

type Tab = "day" | "corrections" | "rules";

export default function AttendancePage() {
  return (
    <HrGuard anyOf={["hr.attendance.view"]}>
      <Attendance />
    </HrGuard>
  );
}

function Attendance() {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState("");
  const [date, setDate] = useState(isoDate(new Date()));
  const [tab, setTab] = useState<Tab>("day");
  const [day, setDay] = useState<AttendanceDay | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busyRow, setBusyRow] = useState<string | null>(null);
  const [correcting, setCorrecting] = useState<{ entry?: AttendanceEntry | null; employee?: { id: string; name: string } | null } | null>(null);

  useEffect(() => {
    authenticatedFetch((s) => getBranches(s)).then((list) => {
      const visible = list.filter((b) => b.active && (access?.allBranches || access?.branchIds.includes(b.id)));
      setBranches(visible);
      setBranchId((current) => current || visible[0]?.id || "");
    }).catch((err) => setError(errorMessage(err, "Could not load branches")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access?.allBranches, access?.branchIds.join(",")]);

  const load = useCallback(async () => {
    if (!branchId) return;
    setError("");
    try {
      setDay(await authenticatedFetch((s) => getAttendanceDay(s, branchId, date)));
    } catch (err) {
      setError(errorMessage(err, "Could not load attendance"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, date]);

  useEffect(() => { void load(); }, [load]);

  // keep the live board fresh while it is open on today
  useEffect(() => {
    if (date !== isoDate(new Date()) || tab !== "day") return;
    const timer = window.setInterval(() => void load(), 60000);
    return () => window.clearInterval(timer);
  }, [date, tab, load]);

  async function clockFor(row: DayRow, action: ClockAction) {
    if (!row.employeeId) return;
    setBusyRow(row.employeeId);
    setNotice("");
    setError("");
    try {
      const result = await authenticatedFetch((s) => clockForEmployee(s, {
        employeeId: row.employeeId!, branchId, action, idempotencyKey: newRequestKey(), reason: "Recorded by manager on the attendance board",
      }));
      setNotice(result.message);
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not record that"));
    } finally {
      setBusyRow(null);
    }
  }

  const canManage = can("hr.attendance.manage");
  const canCorrect = can("hr.attendance.correct");
  const branchName = branches.find((b) => b.id === branchId)?.name ?? "";
  const count = (key: string) => day?.counts[key] ?? 0;

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Attendance"
        icon="clock"
        description="Who is in, who is late or missing, and the record of every clock-in, break and correction."
        actions={(
          <div className="flex flex-wrap gap-2">
            {branches.length > 1 ? (
              <select className="select w-auto" value={branchId} onChange={(e) => setBranchId(e.target.value)} aria-label="Branch">
                {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            ) : null}
          </div>
        )}
      />

      <div className="segmented max-w-full overflow-x-auto" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "day"} onClick={() => setTab("day")}>Day board</button>
        <button type="button" role="tab" aria-selected={tab === "corrections"} onClick={() => setTab("corrections")}>Corrections</button>
        <button type="button" role="tab" aria-selected={tab === "rules"} onClick={() => setTab("rules")}>Rules</button>
      </div>

      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {tab === "day" ? (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" className="btn btn-secondary btn-icon btn-sm" aria-label="Previous day" onClick={() => setDate(isoDate(addDays(new Date(`${date}T00:00:00`), -1)))}><Icon name="chevronLeft" size={16} /></button>
            <input type="date" className="input w-auto" value={date} max={isoDate(new Date())} onChange={(e) => setDate(e.target.value)} aria-label="Date" />
            <button type="button" className="btn btn-secondary btn-icon btn-sm" aria-label="Next day" disabled={date >= isoDate(new Date())} onClick={() => setDate(isoDate(addDays(new Date(`${date}T00:00:00`), 1)))}><Icon name="chevronRight" size={16} /></button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setDate(isoDate(new Date()))}>Today</button>
          </div>

          {!day ? <Skeleton className="h-64 w-full" /> : (
            <>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
                <StatCard label="Working now" value={count("ON_SHIFT") + count("UNSCHEDULED")} icon="users" tone="sage" />
                <StatCard label="On break" value={count("ON_BREAK")} icon="clock" tone="info" />
                <StatCard label="Late arrivals" value={day.lateArrivals} icon="alert" tone="gold" />
                <StatCard label="Absent" value={count("ABSENT")} icon="close" tone="danger" />
                <StatCard label="Missed clock-out" value={count("MISSED_CLOCK_OUT")} icon="timer" tone="danger" />
              </div>
              <section className="panel">
                {day.rows.length === 0 ? <EmptyState compact icon="clock" title="Nobody scheduled or clocked in on this day" /> : (
                  <div className="overflow-x-auto">
                    <table className="data-table">
                      <thead><tr><th>Employee</th><th>Shift</th><th>Clocked</th><th>Time</th><th>Status</th><th className="num">Actions</th></tr></thead>
                      <tbody>
                        {day.rows.map((row, i) => {
                          const e = row.entry;
                          return (
                            <tr key={`${row.employeeId}-${e?.id ?? i}`}>
                              <td className="font-semibold">{row.employeeName ?? "—"}</td>
                              <td className="tabular">{row.shiftStartsAt ? timeRange(row.shiftStartsAt, row.shiftEndsAt) : <span className="text-ink-muted">No shift</span>}</td>
                              <td className="tabular">
                                {e ? timeRange(e.clockInAt, e.clockOutAt) : "—"}
                                {e ? <p className="text-xs text-ink-muted">{humanize(e.source)}{e.terminalId ? ` · ${e.terminalId}` : ""}{e.editReason ? " · corrected" : ""}</p> : null}
                              </td>
                              <td className="text-sm">
                                {e ? metricsLine(e) : "—"}
                                {e && e.breaks.length ? <p className="text-xs text-ink-muted">{e.breaks.map((b) => `${humanize(b.type)} ${b.minutes} min${b.paid ? " (paid)" : ""}`).join(", ")}</p> : null}
                              </td>
                              <td>
                                <span className={`badge ${statusTone[row.status] ?? "badge-neutral"}`}>{humanize(row.status)}</span>
                                {row.late ? <span className="ml-1 badge badge-warning">Late</span> : null}
                              </td>
                              <td className="num">
                                <div className="flex justify-end gap-1">
                                  {canManage && row.employeeId && date === isoDate(new Date()) ? (
                                    !e || e.clockOutAt ? (
                                      row.status !== "COMPLETED" && row.status !== "LEFT_EARLY" ? (
                                        <button type="button" className="btn btn-secondary btn-sm" disabled={busyRow === row.employeeId} onClick={() => clockFor(row, "CLOCK_IN")}>Clock in</button>
                                      ) : null
                                    ) : (
                                      <button type="button" className="btn btn-secondary btn-sm" disabled={busyRow === row.employeeId} onClick={() => clockFor(row, "CLOCK_OUT")}>Clock out</button>
                                    )
                                  ) : null}
                                  {canCorrect && row.employeeId ? (
                                    <button
                                      type="button"
                                      className="btn btn-ghost btn-sm"
                                      onClick={() => setCorrecting(e ? { entry: e } : { employee: { id: row.employeeId!, name: row.employeeName ?? "" } })}
                                    >
                                      {e ? "Correct" : "Add record"}
                                    </button>
                                  ) : null}
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
              <p className="text-xs text-ink-muted">
                Late after {day.policy.lateGraceMinutes} min · missed clock-out {day.policy.missedClockOutMinutes} min after shift end · overtime shown is provisional until timesheets are approved.
              </p>
            </>
          )}
        </>
      ) : null}

      {tab === "corrections" ? <section className="panel"><CorrectionsPanel onChanged={() => void load()} /></section> : null}
      {tab === "rules" && branchId ? <section className="panel"><AttendanceRulesPanel branchId={branchId} branchName={branchName} /></section> : null}

      {correcting ? (
        <CorrectionDialog
          entry={correcting.entry}
          employee={correcting.employee}
          branchId={branchId}
          onClose={() => setCorrecting(null)}
          onDone={(message) => { setCorrecting(null); setNotice(message); void load(); }}
        />
      ) : null}
    </div>
  );
}
