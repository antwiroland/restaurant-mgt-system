"use client";

import { useCallback, useEffect, useState } from "react";
import { SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { errorMessage } from "@/features/hr/ui";
import { formatMinutes } from "@/features/workforce/time";
import { getMyTimesheets, type TimesheetSummary } from "@/lib/hrApi";
import { TimesheetDrawer, timesheetTone } from "./TimesheetDrawer";

/** Employee self-service: own timesheets, submit drafts, see what was approved. */
export function MyTimesheets() {
  const { authenticatedFetch } = useStaffSession();
  const [rows, setRows] = useState<TimesheetSummary[]>([]);
  const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setRows(await authenticatedFetch((s) => getMyTimesheets(s)));
    } catch (err) {
      setError(errorMessage(err, "Could not load your timesheets"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  return (
    <section className="panel grid gap-3">
      <SectionHeader title="My timesheets" description="Check your hours and submit draft timesheets. Your manager reviews and approves them." />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {rows.length === 0 ? <p className="text-sm text-ink-soft">No timesheets yet.</p> : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Period</th><th className="num">Paid</th><th className="num">Overtime</th><th>Status</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="cursor-pointer" onClick={() => setOpen(r.id)}>
                  <td className="tabular">{r.periodStart} – {r.periodEnd}</td>
                  <td className="num tabular">{formatMinutes(r.paidMinutes)}</td>
                  <td className="num tabular">{r.overtimeComputedMinutes ? `${formatMinutes(r.overtimeComputedMinutes)}${r.overtimeApprovedMinutes != null ? ` (${formatMinutes(r.payableOvertimeMinutes)} approved)` : ""}` : "—"}</td>
                  <td><span className={`badge badge-dot ${timesheetTone[r.status]}`}>{humanize(r.status)}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {open ? <TimesheetDrawer id={open} own onClose={() => setOpen(null)} onChanged={() => void load()} /> : null}
    </section>
  );
}
