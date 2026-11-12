"use client";

import { useCallback, useEffect, useState } from "react";
import { SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { errorMessage } from "@/features/hr/ui";
import { getLeaveRequests, type LeaveRequest } from "@/lib/hrApi";
import { LeaveBalancesPanel } from "./LeaveBalancesPanel";
import { LeaveRequestDrawer } from "./LeaveRequestDrawer";
import { LeaveRequestForm } from "./LeaveRequestForm";
import { leaveDates, leaveTone, plural, waitingFor } from "./leaveUtils";

/** Employee profile tab: balances (with HR adjustments), the employee's requests, and recording leave on their behalf. */
export function EmployeeLeavePanel({ employeeId, employeeName, manageable }: { employeeId: string; employeeName: string; manageable: boolean }) {
  const { authenticatedFetch } = useStaffSession();
  const [rows, setRows] = useState<LeaveRequest[]>([]);
  const [recording, setRecording] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setRows(await authenticatedFetch((s) => getLeaveRequests(s, { employeeId })));
    } catch (err) {
      setError(errorMessage(err, "Could not load leave requests"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId]);

  useEffect(() => { void load(); }, [load]);

  function changed() {
    void load();
    setRefreshKey((k) => k + 1);
  }

  return (
    <div className="grid gap-5">
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <LeaveBalancesPanel employeeId={employeeId} refreshKey={refreshKey} />
      <section className="grid gap-3">
        <SectionHeader
          title="Leave requests"
          actions={manageable ? <button type="button" className="btn btn-secondary btn-sm" onClick={() => setRecording(true)}>Record leave</button> : null}
        />
        {rows.length === 0 ? <p className="text-sm text-ink-soft">No leave requests.</p> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Leave</th><th>Dates</th><th className="num">Days</th><th>Status</th></tr></thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id} className="cursor-pointer" onClick={() => setOpen(r.id)}>
                    <td className="font-semibold">{r.leaveTypeName}</td>
                    <td className="text-sm">{leaveDates(r)}</td>
                    <td className="num tabular">{plural(r.days)}</td>
                    <td>
                      <span className={`badge badge-dot ${leaveTone[r.status]}`}>{humanize(r.status)}</span>
                      {r.status === "PENDING" ? <p className="text-xs text-ink-muted">{waitingFor(r)}</p> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {recording ? (
        <LeaveRequestForm employee={{ id: employeeId, name: employeeName }} onClose={() => setRecording(false)}
          onSaved={() => { setRecording(false); changed(); }} />
      ) : null}
      {open ? <LeaveRequestDrawer id={open} onClose={() => setOpen(null)} onChanged={changed} /> : null}
    </div>
  );
}
