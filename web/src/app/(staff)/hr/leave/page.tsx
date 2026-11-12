"use client";

import { useCallback, useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, StatCard, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, Field, errorMessage } from "@/features/hr/ui";
import { LeaveRequestDrawer } from "@/features/hr/leave/LeaveRequestDrawer";
import { LeaveSetupPanels } from "@/features/hr/leave/LeaveSetupPanels";
import { leaveDates, leaveTone, plural, waitingFor } from "@/features/hr/leave/leaveUtils";
import { addDays, isoDate } from "@/features/workforce/time";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import { getLeaveCalendar, getLeaveRequests, type LeaveRequest, type LeaveStatus } from "@/lib/hrApi";

type Tab = "decide" | "all" | "calendar" | "setup";

export default function LeavePage() {
  return (
    <HrGuard anyOf={["hr.leave.approve", "hr.leave.manage"]}>
      <Leave />
    </HrGuard>
  );
}

function Leave() {
  const { authenticatedFetch } = useStaffSession();
  const { access } = useHrAccess();
  const [tab, setTab] = useState<Tab>("decide");
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState("");
  const [status, setStatus] = useState<LeaveStatus | "">("PENDING");
  const [awaiting, setAwaiting] = useState<LeaveRequest[] | null>(null);
  const [rows, setRows] = useState<LeaveRequest[] | null>(null);
  const [calendarFrom, setCalendarFrom] = useState(isoDate(new Date()));
  const [calendar, setCalendar] = useState<LeaveRequest[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    authenticatedFetch((s) => getBranches(s)).then((list) => {
      const visible = list.filter((b) => b.active && (access?.allBranches || access?.branchIds.includes(b.id)));
      setBranches(visible);
      setBranchId((c) => c || visible[0]?.id || "");
    }).catch((err) => setError(errorMessage(err, "Could not load branches")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access?.allBranches, access?.branchIds.join(",")]);

  const loadAwaiting = useCallback(async () => {
    try {
      setAwaiting(await authenticatedFetch((s) => getLeaveRequests(s, { awaitingMe: true })));
    } catch (err) {
      setError(errorMessage(err, "Could not load requests"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadAll = useCallback(async () => {
    if (!branchId) return;
    try {
      setRows(await authenticatedFetch((s) => getLeaveRequests(s, { branchId, status: status || undefined })));
    } catch (err) {
      setError(errorMessage(err, "Could not load requests"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, status]);

  const loadCalendar = useCallback(async () => {
    if (!branchId) return;
    try {
      setCalendar(await authenticatedFetch((s) => getLeaveCalendar(s, branchId, calendarFrom, isoDate(addDays(new Date(`${calendarFrom}T00:00:00`), 41)))));
    } catch (err) {
      setError(errorMessage(err, "Could not load the leave calendar"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, calendarFrom]);

  useEffect(() => { void loadAwaiting(); }, [loadAwaiting]);
  useEffect(() => { if (tab === "all") void loadAll(); }, [tab, loadAll]);
  useEffect(() => { if (tab === "calendar") void loadCalendar(); }, [tab, loadCalendar]);

  function changed() {
    void loadAwaiting();
    if (tab === "all") void loadAll();
    if (tab === "calendar") void loadCalendar();
  }

  const today = isoDate(new Date());
  const awayToday = (calendar ?? []).filter((r) => r.status === "APPROVED" && r.startDate <= today && r.endDate >= today).length;

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Leave"
        icon="calendar"
        description="Leave requests move through each approver in turn. Approved leave blocks scheduling on those days and shows on timesheets instead of an absence."
        actions={branches.length > 1 && tab !== "decide" && tab !== "setup" ? (
          <select className="select w-auto" value={branchId} onChange={(e) => setBranchId(e.target.value)} aria-label="Branch">
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        ) : null}
      />
      <div className="segmented max-w-full overflow-x-auto" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "decide"} onClick={() => setTab("decide")}>
          To decide{awaiting && awaiting.length ? ` (${awaiting.length})` : ""}
        </button>
        <button type="button" role="tab" aria-selected={tab === "all"} onClick={() => setTab("all")}>All requests</button>
        <button type="button" role="tab" aria-selected={tab === "calendar"} onClick={() => setTab("calendar")}>Who&apos;s away</button>
        <button type="button" role="tab" aria-selected={tab === "setup"} onClick={() => setTab("setup")}>Types &amp; entitlements</button>
      </div>
      {error ? <ErrorState message={error} onRetry={changed} /> : null}

      {tab === "decide" ? (
        <section className="panel">
          {!awaiting ? <Skeleton className="h-40 w-full" /> : awaiting.length === 0 ? (
            <EmptyState compact icon="calendar" title="Nothing waiting for you" description="Requests appear here when they reach a step you can approve." />
          ) : <RequestTable rows={awaiting} onOpen={setOpen} showWaiting={false} />}
        </section>
      ) : null}

      {tab === "all" ? (
        <section className="panel grid gap-3">
          <div className="flex flex-wrap items-end gap-2">
            <Field label="Status">
              <select className="select" value={status} onChange={(e) => setStatus(e.target.value as LeaveStatus | "")}>
                <option value="">Any</option>
                {(["PENDING", "APPROVED", "REJECTED", "CANCELLED"] as LeaveStatus[]).map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
              </select>
            </Field>
            <p className="pb-2 text-xs text-ink-muted">Leave from the last month and the year ahead.</p>
          </div>
          {!rows ? <Skeleton className="h-40 w-full" /> : rows.length === 0 ? (
            <EmptyState compact icon="calendar" title="No leave requests" />
          ) : <RequestTable rows={rows} onOpen={setOpen} showWaiting />}
        </section>
      ) : null}

      {tab === "calendar" ? (
        <>
          <section className="panel flex flex-wrap items-end gap-2">
            <Field label="From"><input type="date" className="input" value={calendarFrom} onChange={(e) => setCalendarFrom(e.target.value || today)} /></Field>
            <p className="pb-2 text-xs text-ink-muted">Approved and pending leave for the next six weeks.</p>
          </section>
          {calendar ? (
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <StatCard label="Away today" value={awayToday} icon="calendar" tone="brand" />
              <StatCard label="Approved in period" value={calendar.filter((r) => r.status === "APPROVED").length} icon="check" tone="sage" />
              <StatCard label="Pending in period" value={calendar.filter((r) => r.status === "PENDING").length} icon="clock" tone="gold" />
            </div>
          ) : null}
          <section className="panel">
            {!calendar ? <Skeleton className="h-40 w-full" /> : calendar.length === 0 ? (
              <EmptyState compact icon="calendar" title="Nobody is away in this period" />
            ) : <RequestTable rows={calendar} onOpen={setOpen} showWaiting />}
          </section>
        </>
      ) : null}

      {tab === "setup" ? <LeaveSetupPanels /> : null}

      {open ? <LeaveRequestDrawer id={open} onClose={() => setOpen(null)} onChanged={changed} /> : null}
    </div>
  );
}

function RequestTable({ rows, onOpen, showWaiting }: { rows: LeaveRequest[]; onOpen: (id: string) => void; showWaiting: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="data-table">
        <thead><tr><th>Employee</th><th>Leave</th><th>Dates</th><th className="num">Days</th><th>Status</th></tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="cursor-pointer" onClick={() => onOpen(r.id)}>
              <td className="font-semibold">{r.employeeName}</td>
              <td className="text-sm">{r.leaveTypeName}{r.documentRequired && !r.documentId && r.status === "PENDING" ? <span className="badge badge-warning ml-2 text-[10px]">Document missing</span> : null}</td>
              <td className="text-sm">{leaveDates(r)}</td>
              <td className="num tabular">{plural(r.days)}</td>
              <td>
                <span className={`badge badge-dot ${leaveTone[r.status]}`}>{humanize(r.status)}</span>
                {showWaiting && r.status === "PENDING" ? <p className="text-xs text-ink-muted">{waitingFor(r)}</p> : null}
                {r.canDecide ? <p className="text-xs font-semibold">Your decision</p> : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
