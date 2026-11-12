"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, Field, errorMessage, formatDate } from "@/features/hr/ui";
import { PayrollRulesPanel } from "@/features/hr/payroll/PayrollRulesPanel";
import { PayrollRunDrawer } from "@/features/hr/payroll/PayrollRunDrawer";
import { money, runTone } from "@/features/hr/payroll/payrollUtils";
import { isoDate } from "@/features/workforce/time";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import { createPayrollRun, getPayrollRuns, type PayrollRunSummary } from "@/lib/hrApi";

type Tab = "runs" | "rules";

export default function PayrollPage() {
  return (
    <HrGuard anyOf={["hr.payroll.view", "hr.payroll.process", "hr.payroll.approve", "hr.payroll.finalize", "hr.payroll.pay"]}>
      <Payroll />
    </HrGuard>
  );
}

function Payroll() {
  const { authenticatedFetch } = useStaffSession();
  const { access, can } = useHrAccess();
  const [tab, setTab] = useState<Tab>("runs");
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState("");
  const [runs, setRuns] = useState<PayrollRunSummary[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    authenticatedFetch((s) => getBranches(s)).then((list) => {
      const visible = list.filter((b) => b.active && (access?.allBranches || access?.branchIds.includes(b.id)));
      setBranches(visible);
      setBranchId((c) => c || visible[0]?.id || "");
    }).catch((err) => setError(errorMessage(err, "Could not load branches")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access?.allBranches, access?.branchIds.join(",")]);

  const load = useCallback(async () => {
    if (!branchId) return;
    setError("");
    try {
      setRuns(await authenticatedFetch((s) => getPayrollRuns(s, branchId)));
    } catch (err) {
      setError(errorMessage(err, "Could not load payroll runs"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  useEffect(() => { void load(); }, [load]);

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Payroll"
        icon="cash"
        description="Pay is calculated from contracts, locked timesheets, unpaid leave and your deduction rules: draft → calculated → approved by someone else → finalized → paid."
        actions={(
          <div className="flex flex-wrap gap-2">
            {branches.length > 1 ? (
              <select className="select w-auto" value={branchId} onChange={(e) => setBranchId(e.target.value)} aria-label="Branch">
                {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            ) : null}
            {can("hr.payroll.process") && tab === "runs" ? (
              <button type="button" className="btn btn-primary btn-md" disabled={!branchId} onClick={() => setCreating(true)}><Icon name="plus" size={16} /> New run</button>
            ) : null}
          </div>
        )}
      />
      <div className="segmented max-w-full overflow-x-auto" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "runs"} onClick={() => setTab("runs")}>Payroll runs</button>
        <button type="button" role="tab" aria-selected={tab === "rules"} onClick={() => setTab("rules")}>Deductions &amp; contributions</button>
      </div>
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {tab === "runs" ? (
        <section className="panel">
          {!runs ? <Skeleton className="h-40 w-full" /> : runs.length === 0 ? (
            <EmptyState compact icon="cash" title="No payroll runs yet" description={can("hr.payroll.process") ? "Lock the period's timesheets, then start a run." : undefined} />
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead><tr><th>Run</th><th>Period</th><th className="num">Staff</th><th className="num">Gross</th><th className="num">Net</th><th>Status</th></tr></thead>
                <tbody>
                  {runs.map((r) => (
                    <tr key={r.id} className="cursor-pointer" onClick={() => setOpen(r.id)}>
                      <td className="font-semibold">{r.runNumber}</td>
                      <td className="text-sm">{formatDate(r.periodStart)} – {formatDate(r.periodEnd)}</td>
                      <td className="num tabular">{r.employeeCount}</td>
                      <td className="num tabular">{money(r.grossTotal, r.currency)}</td>
                      <td className="num tabular">{money(r.netTotal, r.currency)}</td>
                      <td><span className={`badge badge-dot ${runTone[r.status]}`}>{humanize(r.status)}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
      {tab === "rules" ? <PayrollRulesPanel branchId={branchId || undefined} /> : null}

      {creating ? (
        <NewRunModal branchId={branchId} onClose={() => setCreating(false)}
          onCreated={(id) => { setCreating(false); void load(); setOpen(id); }} />
      ) : null}
      {open ? <PayrollRunDrawer id={open} onClose={() => setOpen(null)} onChanged={() => void load()} /> : null}
    </div>
  );
}

function NewRunModal({ branchId, onClose, onCreated }: { branchId: string; onClose: () => void; onCreated: (id: string) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const now = new Date();
  const lastMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const lastMonthEnd = new Date(now.getFullYear(), now.getMonth(), 0);
  const [periodStart, setPeriodStart] = useState(isoDate(lastMonthStart));
  const [periodEnd, setPeriodEnd] = useState(isoDate(lastMonthEnd));
  const [payDate, setPayDate] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const run = await authenticatedFetch((s) => createPayrollRun(s, { branchId, periodStart, periodEnd, payDate: payDate || undefined, notes: notes || undefined }));
      onCreated(run.summary.id);
    } catch (err) {
      setError(errorMessage(err, "Could not create the run"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title="New payroll run" onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="payroll-run-form" className="btn btn-primary btn-md" disabled={busy}>Create run</button>
        </div>
      )}>
      <form id="payroll-run-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <div className="grid grid-cols-2 gap-3">
          <Field label="Period from"><input type="date" className="input" value={periodStart} required onChange={(e) => setPeriodStart(e.target.value)} /></Field>
          <Field label="Period to"><input type="date" className="input" value={periodEnd} min={periodStart} required onChange={(e) => setPeriodEnd(e.target.value)} /></Field>
          <Field label="Pay date (optional)"><input type="date" className="input" value={payDate} min={periodStart} onChange={(e) => setPayDate(e.target.value)} /></Field>
        </div>
        <Field label="Notes (optional)"><input className="input" value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} /></Field>
        <p className="text-xs text-ink-muted">Up to 31 days. Hourly and daily staff are paid from timesheets that lie inside the period and are locked for payroll.</p>
      </form>
    </Modal>
  );
}
