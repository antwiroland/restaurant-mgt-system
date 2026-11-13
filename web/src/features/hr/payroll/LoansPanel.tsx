"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Field, errorMessage, formatDate, todayIso } from "@/features/hr/ui";
import {
  addRecurringDeduction,
  getRecurringDeductions,
  stopRecurringDeduction,
  type RecurringDeduction,
  type RecurringKind,
} from "@/lib/hrApi";
import { money } from "./payrollUtils";

/**
 * Loans, salary advances and other deductions taken from every payroll run until repaid. Someone who leaves repays the
 * whole balance from their final pay.
 */
export function LoansPanel({ employeeId, canEdit }: { employeeId: string; canEdit: boolean }) {
  const { authenticatedFetch } = useStaffSession();
  const [items, setItems] = useState<RecurringDeduction[] | null>(null);
  const [error, setError] = useState("");
  const [adding, setAdding] = useState(false);
  const [kind, setKind] = useState<RecurringKind>("LOAN");
  const [description, setDescription] = useState("");
  const [installment, setInstallment] = useState("");
  const [total, setTotal] = useState("");
  const [startDate, setStartDate] = useState(todayIso());
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setItems(await authenticatedFetch((s) => getRecurringDeductions(s, employeeId)));
    } catch (err) {
      setItems([]);
      setError(errorMessage(err, "Could not load loans and advances"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId]);

  useEffect(() => { void load(); }, [load]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!description.trim() || !(Number(installment) > 0)) return setError("Give a description and the amount taken each pay run.");
    if (total && Number(total) < Number(installment)) return setError("The total cannot be less than one instalment.");
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => addRecurringDeduction(s, employeeId, {
        kind, description: description.trim(), installmentAmount: Number(installment), totalAmount: total ? Number(total) : null, startDate,
      }));
      setAdding(false);
      setDescription("");
      setInstallment("");
      setTotal("");
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not save"));
    } finally {
      setBusy(false);
    }
  }

  async function stop(item: RecurringDeduction) {
    const reason = window.prompt(`Stop "${item.description}"? Give a reason (e.g. repaid in cash, written off).`);
    if (!reason?.trim()) return;
    try {
      await authenticatedFetch((s) => stopRecurringDeduction(s, item.id, reason.trim()));
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not stop the deduction"));
    }
  }

  return (
    <section className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold">Loans &amp; advances</h3>
          <p className="text-xs text-ink-muted">Taken from every pay run until repaid. Leavers repay the balance from their final pay.</p>
        </div>
        {canEdit && !adding ? <button type="button" className="btn btn-secondary btn-sm" onClick={() => setAdding(true)}>Add</button> : null}
      </div>
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {adding ? (
        <form onSubmit={submit} className="grid gap-3 rounded-xl border border-line p-3 sm:grid-cols-2">
          <Field label="Type">
            <select className="select" value={kind} onChange={(e) => setKind(e.target.value as RecurringKind)}>
              <option value="LOAN">Loan</option>
              <option value="ADVANCE">Salary advance</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field label="Description"><input className="input" value={description} maxLength={120} onChange={(e) => setDescription(e.target.value)} placeholder="Staff loan, March" /></Field>
          <Field label="Taken each pay run"><input className="input" inputMode="decimal" value={installment} onChange={(e) => setInstallment(e.target.value)} /></Field>
          <Field label="Total to repay" hint="Leave blank for an ongoing deduction"><input className="input" inputMode="decimal" value={total} onChange={(e) => setTotal(e.target.value)} /></Field>
          <Field label="From pay periods ending on or after"><input type="date" className="input" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
          <div className="flex items-end gap-2">
            <button type="submit" className="btn btn-primary btn-sm" disabled={busy}>{busy ? "Saving…" : "Save"}</button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAdding(false)}>Cancel</button>
          </div>
        </form>
      ) : null}
      {items === null ? null : items.length === 0 ? <p className="text-sm text-ink-soft">None.</p> : (
        <table className="data-table">
          <thead><tr><th>Deduction</th><th className="num">Each run</th><th className="num">Repaid</th><th className="num">Balance</th><th /></tr></thead>
          <tbody>
            {items.map((d) => (
              <tr key={d.id}>
                <td>
                  <p className="font-semibold">{d.description}</p>
                  <p className="text-xs text-ink-muted">
                    {humanize(d.kind)} · from {formatDate(d.startDate)}
                    {d.finished ? " · repaid" : !d.active ? ` · stopped${d.stoppedReason ? `: ${d.stoppedReason}` : ""}` : ""}
                  </p>
                </td>
                <td className="num tabular">{money(d.installmentAmount)}</td>
                <td className="num tabular">{money(d.repaid)}</td>
                <td className="num tabular">{d.remaining == null ? "Ongoing" : money(d.remaining)}</td>
                <td>{canEdit && d.active && !d.finished ? <button type="button" className="btn btn-ghost btn-sm" onClick={() => void stop(d)}>Stop</button> : null}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
