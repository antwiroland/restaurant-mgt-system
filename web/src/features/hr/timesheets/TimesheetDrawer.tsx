"use client";

import { useCallback, useEffect, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Detail, Field, errorMessage, formatDate, formatDateTime } from "@/features/hr/ui";
import { hhmm } from "@/features/hr/schedule/scheduleUtils";
import { formatMinutes } from "@/features/workforce/time";
import { getTimesheet, timesheetAction, type TimesheetDetail, type TimesheetStatus } from "@/lib/hrApi";

export const timesheetTone: Record<TimesheetStatus, string> = {
  DRAFT: "badge-neutral",
  SUBMITTED: "badge-pending",
  REVIEWED: "badge-info",
  APPROVED: "badge-success",
  LOCKED: "badge-gold",
};

const flagTone: Record<string, string> = {
  ABSENT: "badge-danger",
  OPEN: "badge-warning",
  UNSCHEDULED: "badge-gold",
  CORRECTED: "badge-info",
  HOLIDAY: "badge-success",
  WEEKEND: "badge-neutral",
};

/** One timesheet with its day lines and the workflow actions the signed-in user may take. */
export function TimesheetDrawer({ id, own = false, onClose, onChanged }: { id: string; own?: boolean; onClose: () => void; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [sheet, setSheet] = useState<TimesheetDetail | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const [overtime, setOvertime] = useState("");

  const load = useCallback(async () => {
    try {
      const detail = await authenticatedFetch((s) => getTimesheet(s, id));
      setSheet(detail);
      setOvertime(String(Math.min(detail.summary.overtimeComputedMinutes, detail.preApprovedOvertimeMinutes)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the timesheet"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  async function run(action: "refresh" | "submit" | "review" | "approve" | "reopen" | "lock", body: Record<string, unknown> = {}) {
    setBusy(true);
    setError("");
    try {
      setSheet(await authenticatedFetch((s) => timesheetAction(s, id, action, body)));
      setNote("");
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not update the timesheet"));
    } finally {
      setBusy(false);
    }
  }

  const s = sheet?.summary;
  const review = can("hr.timesheet.review");
  const needsOtDecision = !!s && s.overtimeRequiresApproval && s.overtimeComputedMinutes > 0;

  const footer = s ? (
    <div className="flex w-full flex-wrap justify-end gap-2">
      {s.status === "DRAFT" && review && !own ? <button type="button" className="btn btn-ghost btn-md" disabled={busy} onClick={() => run("refresh")}>Refresh from attendance</button> : null}
      {s.status === "DRAFT" && (own || review) ? <button type="button" className="btn btn-primary btn-md" disabled={busy} onClick={() => run("submit")}>Submit</button> : null}
      {s.status === "SUBMITTED" && review && !own ? <button type="button" className="btn btn-primary btn-md" disabled={busy} onClick={() => run("review", { note })}>Mark reviewed</button> : null}
      {s.status === "REVIEWED" && review && !own ? (
        <button type="button" className="btn btn-success btn-md" disabled={busy || (needsOtDecision && !can("hr.overtime.approve"))}
          onClick={() => run("approve", { note, overtimeApprovedMinutes: needsOtDecision ? Number(overtime || 0) : undefined })}>
          Approve
        </button>
      ) : null}
      {s.status === "APPROVED" && can("hr.payroll.process") ? <button type="button" className="btn btn-accent btn-md" disabled={busy} onClick={() => run("lock")}>Lock for payroll</button> : null}
      {["SUBMITTED", "REVIEWED", "APPROVED"].includes(s.status) && review && !own ? (
        <button type="button" className="btn btn-ghost btn-md" disabled={busy || !note.trim()} title="Give a reason in the note field" onClick={() => run("reopen", { reason: note })}>Reopen</button>
      ) : null}
    </div>
  ) : null;

  return (
    <Drawer open title={s ? `${s.employeeName ?? "Timesheet"} · ${formatDate(s.periodStart)} – ${formatDate(s.periodEnd)}` : "Timesheet"} onClose={onClose} footer={footer}>
      {!sheet ? (error ? <p className="alert alert-danger">{error}</p> : <Skeleton className="h-64 w-full" />) : (
        <div className="grid gap-5">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          <div className="flex flex-wrap items-center gap-2">
            <span className={`badge badge-dot ${timesheetTone[s!.status]}`}>{humanize(s!.status)}</span>
            <span className="text-xs text-ink-muted">Generated {formatDateTime(sheet.generatedAt)}</span>
          </div>
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Detail label="Scheduled" value={formatMinutes(s!.scheduledMinutes)} />
            <Detail label="Paid" value={formatMinutes(s!.paidMinutes)} />
            <Detail label="Regular" value={formatMinutes(s!.regularMinutes)} />
            <Detail label="Unpaid breaks" value={formatMinutes(sheet.unpaidBreakMinutes)} />
            <Detail label="Overtime (computed)" value={`${formatMinutes(s!.overtimeComputedMinutes)}${sheet.weeklyOvertimeMinutes ? ` · ${formatMinutes(sheet.weeklyOvertimeMinutes)} weekly` : ""}`} />
            <Detail label="Overtime payable" value={s!.overtimeRequiresApproval && s!.overtimeApprovedMinutes == null && s!.overtimeComputedMinutes > 0 ? "Awaiting approval" : formatMinutes(s!.payableOvertimeMinutes)} />
            <Detail label="Holiday / weekend / night" value={`${formatMinutes(s!.holidayMinutes)} / ${formatMinutes(s!.weekendMinutes)} / ${formatMinutes(s!.nightMinutes)}`} />
            <Detail label="Late / absent" value={`${s!.lateCount} / ${s!.absenceCount}`} />
          </dl>

          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Day</th><th>Shift</th><th>Clocked</th><th className="num">Paid</th><th>Notes</th></tr></thead>
              <tbody>
                {sheet.lines.map((l) => (
                  <tr key={l.id}>
                    <td className="whitespace-nowrap">{new Date(`${l.workDate}T00:00:00`).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })}</td>
                    <td className="tabular">{l.shiftStart ? `${hhmm(l.shiftStart)}–${l.shiftEnd ? hhmm(l.shiftEnd) : ""}` : "—"}</td>
                    <td className="tabular">{l.clockIn ? `${hhmm(l.clockIn)}–${l.clockOut ? hhmm(l.clockOut) : "…"}` : "—"}</td>
                    <td className="num tabular">{formatMinutes(l.paidMinutes)}</td>
                    <td>
                      <div className="flex flex-wrap gap-1">
                        {l.flags.map((f) => <span key={f} className={`badge text-[10px] ${flagTone[f] ?? "badge-neutral"}`}>{humanize(f)}</span>)}
                        {l.lateMinutes ? <span className="badge badge-warning text-[10px]">{l.lateMinutes}m late</span> : null}
                        {l.earlyLeaveMinutes ? <span className="badge badge-warning text-[10px]">left {l.earlyLeaveMinutes}m early</span> : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {s!.status === "REVIEWED" && needsOtDecision && review && !own ? (
            <Field label={`Overtime to approve (minutes, up to ${s!.overtimeComputedMinutes})`} hint={sheet.preApprovedOvertimeMinutes ? `${sheet.preApprovedOvertimeMinutes} min were pre-authorised` : "Nothing was pre-authorised"}>
              <input className="input w-40 tabular" inputMode="numeric" value={overtime} onChange={(e) => setOvertime(e.target.value.replace(/\D/g, ""))} />
            </Field>
          ) : null}
          {!own && ["SUBMITTED", "REVIEWED", "APPROVED"].includes(s!.status) ? (
            <Field label="Note (required to reopen)"><input className="input" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} /></Field>
          ) : null}
          {sheet.notes ? <p className="whitespace-pre-line rounded-xl bg-sunken p-3 text-sm text-ink-soft">{sheet.notes}</p> : null}
          {s!.status === "LOCKED" ? <p className="text-sm text-ink-soft">Locked for payroll {sheet.lockedAt ? formatDateTime(sheet.lockedAt) : ""}. Changes now need a payroll adjustment.</p> : null}
        </div>
      )}
    </Drawer>
  );
}
