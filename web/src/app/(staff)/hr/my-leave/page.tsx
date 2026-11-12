"use client";

import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { EmptyState, PageHeader, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { HrGuard, errorMessage } from "@/features/hr/ui";
import { LeaveBalancesPanel } from "@/features/hr/leave/LeaveBalancesPanel";
import { LeaveRequestDrawer } from "@/features/hr/leave/LeaveRequestDrawer";
import { LeaveRequestForm } from "@/features/hr/leave/LeaveRequestForm";
import { leaveDates, leaveTone, plural, waitingFor } from "@/features/hr/leave/leaveUtils";
import { getMyLeaveRequests, type LeaveRequest } from "@/lib/hrApi";

export default function MyLeavePage() {
  return (
    <HrGuard anyOf={["self"]} title="No employee record linked">
      <MyLeave />
    </HrGuard>
  );
}

function MyLeave() {
  const { authenticatedFetch } = useStaffSession();
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [requesting, setRequesting] = useState(false);
  const [open, setOpen] = useState<LeaveRequest | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setRequests(await authenticatedFetch((s) => getMyLeaveRequests(s)));
    } catch (err) {
      setError(errorMessage(err, "Could not load your leave"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  function changed() {
    void load();
    setRefreshKey((k) => k + 1);
  }

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="My HR"
        title="My leave"
        icon="calendar"
        description="Check your balances, request time off and follow your requests through approval."
        actions={<button type="button" className="btn btn-primary btn-md" onClick={() => setRequesting(true)}><Icon name="plus" size={16} /> Request leave</button>}
      />
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}

      <LeaveBalancesPanel refreshKey={refreshKey} />

      <section className="panel grid gap-3">
        <SectionHeader title="My requests" description="Open a request to see who has approved it, attach a supporting document, or cancel it." />
        {requests.length === 0 ? <EmptyState compact icon="calendar" title="No leave requests yet" /> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Leave</th><th>Dates</th><th className="num">Days</th><th>Status</th></tr></thead>
              <tbody>
                {requests.map((r) => (
                  <tr key={r.id} className="cursor-pointer" onClick={() => setOpen(r)}>
                    <td className="font-semibold">{r.leaveTypeName}</td>
                    <td className="text-sm">{leaveDates(r)}</td>
                    <td className="num tabular">{plural(r.days)}</td>
                    <td>
                      <span className={`badge badge-dot ${leaveTone[r.status]}`}>{humanize(r.status)}</span>
                      {r.status === "PENDING" ? <p className="text-xs text-ink-muted">{waitingFor(r)}</p> : null}
                      {r.documentRequired && !r.documentId && r.status === "PENDING" ? <p className="text-xs font-semibold text-danger">Attach a supporting document</p> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {requesting ? (
        <LeaveRequestForm onClose={() => setRequesting(false)}
          onSaved={(r) => { setRequesting(false); setNotice(`Request sent: ${plural(r.days)} of ${r.leaveTypeName?.toLowerCase()}.`); changed(); }} />
      ) : null}
      {open ? <LeaveRequestDrawer id={open.id} own initial={open} onClose={() => setOpen(null)} onChanged={changed} /> : null}
    </div>
  );
}
