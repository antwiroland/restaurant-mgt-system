"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, StatCard, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, Field, errorMessage } from "@/features/hr/ui";
import { OvertimeRequestsPanel, OvertimeRulesPanel } from "@/features/hr/timesheets/OvertimePanels";
import { TimesheetDrawer, timesheetTone } from "@/features/hr/timesheets/TimesheetDrawer";
import { addDays, formatMinutes, isoDate, startOfWeek } from "@/features/workforce/time";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import { generateTimesheets, getTimesheets, type TimesheetStatus, type TimesheetSummary } from "@/lib/hrApi";

type Tab = "sheets" | "requests" | "rules";

function presets() {
  const thisWeek = startOfWeek(new Date());
  const lastWeek = addDays(thisWeek, -7);
  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
  const lastMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const lastMonthEnd = new Date(now.getFullYear(), now.getMonth(), 0);
  return [
    { key: "last-week", label: "Last week", from: isoDate(lastWeek), to: isoDate(addDays(lastWeek, 6)) },
    { key: "this-week", label: "This week", from: isoDate(thisWeek), to: isoDate(addDays(thisWeek, 6)) },
    { key: "last-month", label: "Last month", from: isoDate(lastMonthStart), to: isoDate(lastMonthEnd) },
    { key: "this-month", label: "This month", from: isoDate(monthStart), to: isoDate(new Date(now.getFullYear(), now.getMonth() + 1, 0)) },
  ];
}

export default function TimesheetsPage() {
  return (
    <HrGuard anyOf={["hr.timesheet.review", "hr.payroll.view", "hr.payroll.process"]}>
      <Timesheets />
    </HrGuard>
  );
}

function Timesheets() {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const options = useMemo(() => presets(), []);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState("");
  const [from, setFrom] = useState(options[0].from);
  const [to, setTo] = useState(options[0].to);
  const [status, setStatus] = useState<TimesheetStatus | "">("");
  const [rows, setRows] = useState<TimesheetSummary[] | null>(null);
  const [tab, setTab] = useState<Tab>("sheets");
  const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authenticatedFetch((s) => getBranches(s)).then((list) => {
      const visible = list.filter((b) => b.active && (access?.allBranches || access?.branchIds.includes(b.id)));
      setBranches(visible);
      setBranchId((c) => c || visible[0]?.id || "");
    }).catch((err) => setError(errorMessage(err, "Could not load branches")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access?.allBranches, access?.branchIds.join(",")]);

  const load = useCallback(async () => {
    if (!branchId || !from || !to) return;
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getTimesheets(s, branchId, from, to, status || undefined)));
    } catch (err) {
      setError(errorMessage(err, "Could not load timesheets"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, from, to, status]);

  useEffect(() => { void load(); }, [load]);

  async function generate() {
    setBusy(true);
    setNotice("");
    setError("");
    try {
      const result = await authenticatedFetch((s) => generateTimesheets(s, { branchId, from, to }));
      const skipped = result.skipped.length ? ` ${result.skipped.length} skipped: ${result.skipped.map((x) => `${x.employeeName} (${x.reason.toLowerCase()})`).join("; ")}.` : "";
      setNotice(`${result.created} created, ${result.refreshed} refreshed from attendance.${skipped}`);
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not generate timesheets"));
    } finally {
      setBusy(false);
    }
  }

  const totals = (rows ?? []).reduce((t, r) => ({
    paid: t.paid + r.paidMinutes, ot: t.ot + r.overtimeComputedMinutes, payable: t.payable + r.payableOvertimeMinutes,
    waiting: t.waiting + (r.status === "SUBMITTED" || r.status === "REVIEWED" ? 1 : 0),
  }), { paid: 0, ot: 0, payable: 0, waiting: 0 });

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Timesheets"
        icon="note"
        description="Hours from attendance, frozen for review: draft → submitted → reviewed → approved → locked for payroll. Overtime is only payable once approved."
        actions={branches.length > 1 ? (
          <select className="select w-auto" value={branchId} onChange={(e) => setBranchId(e.target.value)} aria-label="Branch">
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        ) : null}
      />
      <div className="segmented max-w-full overflow-x-auto" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "sheets"} onClick={() => setTab("sheets")}>Timesheets</button>
        <button type="button" role="tab" aria-selected={tab === "requests"} onClick={() => setTab("requests")}>Overtime requests</button>
        <button type="button" role="tab" aria-selected={tab === "rules"} onClick={() => setTab("rules")}>Overtime rules &amp; holidays</button>
      </div>
      {notice ? <p className="alert alert-info" role="status">{notice}</p> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {tab === "sheets" ? (
        <>
          <section className="panel grid gap-3">
            <div className="flex flex-wrap items-end gap-2">
              <div className="segmented">
                {options.map((p) => (
                  <button key={p.key} type="button" aria-pressed={from === p.from && to === p.to} onClick={() => { setFrom(p.from); setTo(p.to); }}>{p.label}</button>
                ))}
              </div>
              <Field label="From"><input type="date" className="input" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
              <Field label="To"><input type="date" className="input" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></Field>
              <Field label="Status">
                <select className="select" value={status} onChange={(e) => setStatus(e.target.value as TimesheetStatus | "")}>
                  <option value="">Any</option>
                  {(["DRAFT", "SUBMITTED", "REVIEWED", "APPROVED", "LOCKED"] as TimesheetStatus[]).map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
                </select>
              </Field>
              {can("hr.timesheet.review") ? (
                <button type="button" className="btn btn-primary btn-md" onClick={generate} disabled={busy || !branchId}>
                  <Icon name="refresh" size={16} /> {busy ? "Generating…" : "Generate / refresh drafts"}
                </button>
              ) : null}
            </div>
            <p className="text-xs text-ink-muted">One timesheet covers up to 31 days. Generating refreshes drafts only; submitted timesheets stay as they were.</p>
          </section>

          {rows ? (
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <StatCard label="Paid hours" value={formatMinutes(totals.paid)} icon="clock" tone="brand" />
              <StatCard label="Overtime computed" value={formatMinutes(totals.ot)} icon="timer" tone="gold" />
              <StatCard label="Overtime payable" value={formatMinutes(totals.payable)} icon="cash" tone="sage" />
              <StatCard label="Waiting for review" value={totals.waiting} icon="note" tone="info" />
            </div>
          ) : null}

          <section className="panel">
            {!rows ? <Skeleton className="h-48 w-full" /> : rows.length === 0 ? (
              <EmptyState compact icon="note" title="No timesheets for this period" description={can("hr.timesheet.review") ? "Generate them from attendance." : undefined} />
            ) : (
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead><tr><th>Employee</th><th>Period</th><th className="num">Scheduled</th><th className="num">Paid</th><th className="num">Overtime</th><th>Exceptions</th><th>Status</th></tr></thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.id} className="cursor-pointer" onClick={() => setOpen(r.id)}>
                        <td className="font-semibold">{r.employeeName}</td>
                        <td className="tabular text-sm">{r.periodStart} – {r.periodEnd}</td>
                        <td className="num tabular">{formatMinutes(r.scheduledMinutes)}</td>
                        <td className="num tabular">{formatMinutes(r.paidMinutes)}</td>
                        <td className="num tabular">
                          {r.overtimeComputedMinutes ? formatMinutes(r.overtimeComputedMinutes) : "—"}
                          {r.overtimeComputedMinutes && r.overtimeApprovedMinutes != null ? <p className="text-xs text-ink-muted">{formatMinutes(r.payableOvertimeMinutes)} approved</p> : null}
                        </td>
                        <td className="text-xs">
                          {r.absenceCount ? <span className="badge badge-danger mr-1">{r.absenceCount} absent</span> : null}
                          {r.lateCount ? <span className="badge badge-warning">{r.lateCount} late</span> : null}
                        </td>
                        <td><span className={`badge badge-dot ${timesheetTone[r.status]}`}>{humanize(r.status)}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      ) : null}
      {tab === "requests" && branchId ? <section className="panel"><OvertimeRequestsPanel branchId={branchId} /></section> : null}
      {tab === "rules" && branchId ? <section className="panel"><OvertimeRulesPanel branchId={branchId} /></section> : null}

      {open ? <TimesheetDrawer id={open} onClose={() => setOpen(null)} onChanged={() => void load()} /> : null}
    </div>
  );
}
