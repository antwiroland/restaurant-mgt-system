"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Detail, Field, errorMessage, formatDate, formatDateTime } from "@/features/hr/ui";
import {
  PAYMENT_METHODS,
  addPayrollAdjustment,
  getPayrollRun,
  listEmployees,
  payrollRunAction,
  removePayrollAdjustment,
  type AdjustmentKind,
  type EmployeeSummary,
  type PaymentMethod,
  type PayrollRunDetail,
} from "@/lib/hrApi";
import { PayslipDrawer } from "./PayslipDrawer";
import { PaymentFilesPanel } from "./PaymentFilesPanel";
import { RunReportsPanel } from "./RunReportsPanel";
import { BLOCKING_ISSUES, issueLabel, money, runTone } from "./payrollUtils";

type Action = "calculate" | "approve" | "reopen" | "finalize" | "pay" | "cancel";

/** A payroll run: payslips, adjustments and the workflow actions the signed-in user may take. */
export function PayrollRunDrawer({ id, onClose, onChanged }: { id: string; onClose: () => void; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [run, setRun] = useState<PayrollRunDetail | null>(null);
  const [slip, setSlip] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("BANK");
  const [reference, setReference] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setRun(await authenticatedFetch((s) => getPayrollRun(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the payroll run"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  async function act(action: Action, body: Record<string, unknown> = {}, done = "") {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      setRun(await authenticatedFetch((s) => payrollRunAction(s, id, action, body)));
      setReason("");
      setNotice(done);
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not update the run"));
    } finally {
      setBusy(false);
    }
  }

  async function removeAdjustment(adjustmentId: string) {
    setBusy(true);
    setError("");
    try {
      setRun(await authenticatedFetch((s) => removePayrollAdjustment(s, id, adjustmentId)));
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not remove the adjustment"));
    } finally {
      setBusy(false);
    }
  }

  const s = run?.summary;
  const editable = s?.status === "DRAFT" || s?.status === "CALCULATED";
  const process = can("hr.payroll.process");

  const footer = s ? (
    <div className="flex w-full flex-wrap justify-end gap-2">
      {editable && process ? (
        <button type="button" className="btn btn-primary btn-md" disabled={busy}
          onClick={() => act("calculate", {}, "Calculated from contracts, locked timesheets, leave and rules.")}>
          {s.calculatedAt ? "Recalculate" : "Calculate"}
        </button>
      ) : null}
      {s.status === "CALCULATED" && can("hr.payroll.approve") ? (
        <button type="button" className="btn btn-success btn-md" disabled={busy || run!.calculatedByMe || run!.blockingPayslips > 0}
          title={run!.calculatedByMe ? "Someone other than the person who calculated this run must approve it" : undefined}
          onClick={() => act("approve", {}, "Run approved.")}>
          Approve
        </button>
      ) : null}
      {s.status === "APPROVED" && can("hr.payroll.finalize") ? (
        <button type="button" className="btn btn-accent btn-md" disabled={busy} onClick={() => act("finalize", {}, "Finalized. Employees can now see their payslips.")}>
          Finalize
        </button>
      ) : null}
      {(s.status === "CALCULATED" || s.status === "APPROVED") && (process || can("hr.payroll.approve")) ? (
        <button type="button" className="btn btn-ghost btn-md" disabled={busy || !reason.trim()} title="Give a reason below"
          onClick={() => act("reopen", { reason }, "Returned to draft.")}>
          Return to draft
        </button>
      ) : null}
      {editable || s.status === "APPROVED" ? (process ? (
        <button type="button" className="btn btn-ghost btn-md" disabled={busy || !reason.trim()} title="Give a reason below"
          onClick={() => act("cancel", { reason }, "Run cancelled.")}>
          Cancel run
        </button>
      ) : null) : null}
    </div>
  ) : null;

  return (
    <Drawer open title={s ? `${s.runNumber} · ${s.branchName ?? ""}` : "Payroll run"} onClose={onClose} footer={footer}>
      {!run || !s ? (error ? <p className="alert alert-danger">{error}</p> : <Skeleton className="h-64 w-full" />) : (
        <div className="grid gap-5">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {notice ? <p className="alert alert-info" role="status">{notice}</p> : null}
          <div className="flex flex-wrap items-center gap-2">
            <span className={`badge badge-dot ${runTone[s.status]}`}>{humanize(s.status)}</span>
            <span className="text-sm text-ink-muted">{formatDate(s.periodStart)} – {formatDate(s.periodEnd)}{s.payDate ? ` · pay date ${formatDate(s.payDate)}` : ""}</span>
          </div>
          {s.status === "DRAFT" && s.calculatedAt ? <p className="alert alert-warning text-sm">Something changed after the last calculation. Recalculate before approval.</p> : null}
          {run.blockingPayslips > 0 ? <p className="alert alert-danger text-sm">{run.blockingPayslips} payslip{run.blockingPayslips === 1 ? "" : "s"} must be fixed before approval.</p> : null}
          {s.cancelReason ? <p className="alert alert-danger text-sm">Cancelled: {s.cancelReason}</p> : null}

          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Detail label="Employees" value={s.employeeCount} />
            <Detail label="Gross" value={money(s.grossTotal, s.currency)} />
            <Detail label="Deductions" value={money(s.deductionsTotal, s.currency)} />
            <Detail label="Net to pay" value={money(s.netTotal, s.currency)} />
            <Detail label="Employer contributions" value={money(s.employerTotal, s.currency)} />
            <Detail label="Calculated" value={s.calculatedAt ? formatDateTime(s.calculatedAt) : "—"} />
            <Detail label="Approved" value={s.approvedAt ? formatDateTime(s.approvedAt) : "—"} />
            <Detail label="Paid" value={s.paidCount != null ? `${s.paidCount} of ${run.payslips.length}` : "—"} />
          </dl>

          <section className="grid gap-2">
            <h3 className="text-sm font-semibold">Payslips</h3>
            {run.payslips.length === 0 ? <p className="text-sm text-ink-soft">{s.calculatedAt ? "Nobody to pay in this period." : "Calculate the run to create payslips."}</p> : (
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead><tr><th>Employee</th><th className="num">Gross</th><th className="num">Deductions</th><th className="num">Net</th><th>Notes</th></tr></thead>
                  <tbody>
                    {run.payslips.map((p) => (
                      <tr key={p.id} className="cursor-pointer" onClick={() => setSlip(p.id)}>
                        <td className="font-semibold">{p.employeeName}<p className="text-xs font-normal text-ink-muted">{humanize(p.salaryType ?? "")}</p></td>
                        <td className="num tabular">{money(p.gross, s.currency)}</td>
                        <td className="num tabular">{money(p.employeeDeductions, s.currency)}</td>
                        <td className="num tabular font-semibold">{money(p.net, s.currency)}</td>
                        <td className="text-xs">
                          {p.issues.map((i) => (
                            <span key={i} className={`badge mr-1 text-[10px] ${BLOCKING_ISSUES.has(i) ? "badge-danger" : "badge-warning"}`} title={issueLabel[i]}>{humanize(i)}</span>
                          ))}
                          {p.paidAt ? <span className="badge badge-success text-[10px]">Paid</span> : null}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {run.skipped.length ? (
            <section className="grid gap-1">
              <h3 className="text-sm font-semibold">Not paid in this run</h3>
              <ul className="list-disc pl-5 text-sm text-ink-soft">
                {run.skipped.map((k) => <li key={k.employeeId}>{k.name ?? "Employee"} — {k.reason}</li>)}
              </ul>
            </section>
          ) : null}

          <section className="grid gap-2">
            <h3 className="text-sm font-semibold">One-off adjustments</h3>
            {run.adjustments.length === 0 ? <p className="text-sm text-ink-soft">None.</p> : (
              <ul className="grid gap-1 text-sm">
                {run.adjustments.map((a) => (
                  <li key={a.id} className="flex items-center justify-between gap-2 rounded-lg border border-line px-3 py-2">
                    <span>{a.employeeName}: {a.description} <span className="text-ink-muted">({a.kind === "EARNING" ? (a.taxable ? "taxable earning" : "tax-free earning") : "deduction"})</span></span>
                    <span className="flex items-center gap-2">
                      <span className="tabular">{a.kind === "DEDUCTION" ? "−" : "+"}{money(a.amount, s.currency)}</span>
                      {editable && process ? <button type="button" className="btn btn-ghost btn-sm" disabled={busy} onClick={() => removeAdjustment(a.id)}>Remove</button> : null}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {editable && process ? <AdjustmentForm runId={id} branchId={s.branchId} onSaved={(r) => { setRun(r); onChanged(); }} /> : null}
          </section>

          {s.status !== "DRAFT" && s.status !== "CANCELLED" && run.payslips.length ? (
            <RunReportsPanel runId={id} currency={s.currency} refreshKey={s.calculatedAt ?? ""} />
          ) : null}

          {(s.status === "FINALIZED" || s.status === "PAID") && can("hr.payroll.pay") ? <PaymentFilesPanel runId={id} refreshKey={run.summary.paidCount ?? 0} /> : null}

          {s.status === "FINALIZED" && can("hr.payroll.pay") ? (
            <section className="grid gap-2 rounded-xl border border-line p-3">
              <h3 className="text-sm font-semibold">Record payment of all unpaid payslips</h3>
              <div className="flex flex-wrap items-end gap-2">
                <Field label="Method">
                  <select className="select" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
                    {PAYMENT_METHODS.map((m) => <option key={m} value={m}>{humanize(m)}</option>)}
                  </select>
                </Field>
                <Field label="Reference (optional)"><input className="input" value={reference} maxLength={120} onChange={(e) => setReference(e.target.value)} /></Field>
                <button type="button" className="btn btn-primary btn-md" disabled={busy}
                  onClick={() => act("pay", { method, reference: reference || undefined }, "Payment recorded.")}>
                  Mark as paid
                </button>
              </div>
            </section>
          ) : null}

          {(s.status === "CALCULATED" || s.status === "APPROVED" || editable) && process ? (
            <Field label="Reason (to return to draft or cancel)">
              <input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
            </Field>
          ) : null}
        </div>
      )}
      {slip ? <PayslipDrawer id={slip} onClose={() => setSlip(null)} /> : null}
    </Drawer>
  );
}

function AdjustmentForm({ runId, branchId, onSaved }: { runId: string; branchId: string; onSaved: (run: PayrollRunDetail) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [employees, setEmployees] = useState<EmployeeSummary[]>([]);
  const [employeeId, setEmployeeId] = useState("");
  const [kind, setKind] = useState<AdjustmentKind>("EARNING");
  const [taxable, setTaxable] = useState(true);
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authenticatedFetch((s) => listEmployees(s, { branchId, size: 200, sort: "name" }))
      .then((page) => setEmployees(page.items))
      .catch(() => setEmployees([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      onSaved(await authenticatedFetch((s) => addPayrollAdjustment(s, runId, { employeeId, kind, taxable: kind === "EARNING" && taxable, description, amount: Number(amount) })));
      setDescription("");
      setAmount("");
    } catch (err) {
      setError(errorMessage(err, "Could not add the adjustment"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="grid gap-2 rounded-xl border border-line p-3" onSubmit={submit}>
      {error ? <p className="alert alert-danger text-sm" role="alert">{error}</p> : null}
      <div className="grid gap-2 sm:grid-cols-2">
        <Field label="Employee">
          <select className="select" value={employeeId} required onChange={(e) => setEmployeeId(e.target.value)}>
            <option value="">Choose…</option>
            {employees.map((e) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
          </select>
        </Field>
        <Field label="Type">
          <select className="select" value={kind} onChange={(e) => setKind(e.target.value as AdjustmentKind)}>
            <option value="EARNING">Earning (bonus, allowance)</option>
            <option value="DEDUCTION">Deduction (advance, loan)</option>
          </select>
        </Field>
        <Field label="Description"><input className="input" value={description} required maxLength={200} onChange={(e) => setDescription(e.target.value)} /></Field>
        <Field label="Amount"><input className="input tabular" inputMode="decimal" value={amount} required onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))} /></Field>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        {kind === "EARNING" ? (
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={taxable} onChange={(e) => setTaxable(e.target.checked)} /> Taxable</label>
        ) : <span className="text-xs text-ink-muted">Deductions are taken after tax.</span>}
        <button type="submit" className="btn btn-secondary btn-sm" disabled={busy || !employeeId || !amount || Number(amount) <= 0}>Add adjustment</button>
      </div>
    </form>
  );
}
