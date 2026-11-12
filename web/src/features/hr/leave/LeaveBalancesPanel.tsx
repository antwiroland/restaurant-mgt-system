"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDateTime } from "@/features/hr/ui";
import {
  adjustLeaveBalance,
  getLeaveBalances,
  getLeaveTransactions,
  getMyLeaveBalances,
  type LeaveBalance,
  type LeaveTransaction,
} from "@/lib/hrApi";
import { formatDays } from "./leaveUtils";

/** Leave balances for a year with their ledger. Without `employeeId` it shows the signed-in employee's own balances. */
export function LeaveBalancesPanel({ employeeId, refreshKey = 0 }: { employeeId?: string; refreshKey?: number }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const [rows, setRows] = useState<LeaveBalance[] | null>(null);
  const [ledger, setLedger] = useState<{ balance: LeaveBalance; rows: LeaveTransaction[] } | null>(null);
  const [adjusting, setAdjusting] = useState<LeaveBalance | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await authenticatedFetch((s) => (employeeId ? getLeaveBalances(s, employeeId, year) : getMyLeaveBalances(s, year))));
    } catch (err) {
      setError(errorMessage(err, "Could not load leave balances"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, year, refreshKey]);

  useEffect(() => { void load(); }, [load]);

  async function openLedger(balance: LeaveBalance) {
    if (!balance.id) {
      setLedger({ balance, rows: [] });
      return;
    }
    try {
      const txs = await authenticatedFetch((s) => getLeaveTransactions(s, balance.id!));
      setLedger({ balance, rows: txs });
    } catch (err) {
      setError(errorMessage(err, "Could not load the balance history"));
    }
  }

  return (
    <section className="panel grid gap-3">
      <SectionHeader
        title="Leave balances"
        description="Available = earned + carried over + adjustments − taken − waiting for approval."
        actions={(
          <select className="select w-auto" value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label="Leave year">
            {[thisYear - 1, thisYear, thisYear + 1].map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        )}
      />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {rows && rows.length === 0 ? <p className="text-sm text-ink-soft">No leave types with a balance are set up yet.</p> : null}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {(rows ?? []).map((b) => (
          <article key={b.leaveTypeId} className="rounded-xl border border-line p-4">
            <div className="flex items-start justify-between gap-2">
              <h3 className="font-semibold">{b.leaveTypeName}</h3>
              <p className={`tabular text-2xl font-bold ${Number(b.availableDays) < 0 ? "text-danger" : ""}`}>{formatDays(b.availableDays)}</p>
            </div>
            <p className="text-xs text-ink-muted">days available</p>
            {!b.hasPolicy ? <p className="mt-2 text-xs text-ink-soft">No entitlement configured for this employee.</p> : null}
            <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
              <dt className="text-ink-muted">Entitled</dt><dd className="tabular text-right">{formatDays(b.entitledDays)}</dd>
              {b.accrual === "MONTHLY" ? (<><dt className="text-ink-muted">Earned so far</dt><dd className="tabular text-right">{formatDays(b.earnedDays)}</dd></>) : null}
              {Number(b.carriedOverDays) ? (<><dt className="text-ink-muted">Carried over</dt><dd className="tabular text-right">{formatDays(b.carriedOverDays)}</dd></>) : null}
              {Number(b.adjustmentDays) ? (<><dt className="text-ink-muted">Adjustments</dt><dd className="tabular text-right">{formatDays(b.adjustmentDays)}</dd></>) : null}
              <dt className="text-ink-muted">Taken</dt><dd className="tabular text-right">{formatDays(b.usedDays)}</dd>
              <dt className="text-ink-muted">Waiting for approval</dt><dd className="tabular text-right">{formatDays(b.pendingDays)}</dd>
            </dl>
            <div className="mt-3 flex gap-2">
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => openLedger(b)}>History</button>
              {employeeId && can("hr.leave.manage") ? (
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAdjusting(b)}>Adjust</button>
              ) : null}
            </div>
          </article>
        ))}
      </div>

      {ledger ? (
        <Modal open title={`${ledger.balance.leaveTypeName} ${ledger.balance.year}`} onClose={() => setLedger(null)}>
          {ledger.rows.length === 0 ? <p className="text-sm text-ink-soft">Nothing recorded yet; the balance opens with the first request or adjustment.</p> : (
            <table className="data-table">
              <thead><tr><th>When</th><th>Change</th><th className="num">Days</th><th>By</th></tr></thead>
              <tbody>
                {ledger.rows.map((t) => (
                  <tr key={t.id}>
                    <td className="whitespace-nowrap text-xs">{formatDateTime(t.createdAt)}</td>
                    <td>{humanize(t.kind)}{t.note ? <p className="text-xs text-ink-muted">{t.note}</p> : null}</td>
                    <td className="num tabular">{Number(t.days) > 0 ? "+" : ""}{formatDays(t.days)}</td>
                    <td className="text-xs">{t.actorName ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Modal>
      ) : null}

      {adjusting && employeeId ? (
        <AdjustModal employeeId={employeeId} balance={adjusting} year={year} onClose={() => setAdjusting(null)}
          onSaved={() => { setAdjusting(null); void load(); }} />
      ) : null}
    </section>
  );
}

function AdjustModal({ employeeId, balance, year, onClose, onSaved }: {
  employeeId: string; balance: LeaveBalance; year: number; onClose: () => void; onSaved: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [days, setDays] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => adjustLeaveBalance(s, employeeId, { leaveTypeId: balance.leaveTypeId, year, days: Number(days), note }));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not adjust the balance"));
    } finally {
      setBusy(false);
    }
  }

  const valid = days !== "" && !Number.isNaN(Number(days)) && Number(days) !== 0 && note.trim().length > 0;
  return (
    <Modal open title={`Adjust ${balance.leaveTypeName} ${year}`} onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="leave-adjust-form" className="btn btn-primary btn-md" disabled={busy || !valid}>Save adjustment</button>
        </div>
      )}>
      <form id="leave-adjust-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <Field label="Days" hint="Positive adds days, negative removes them (for example days taken before the system was used). Half days allowed.">
          <input className="input w-40 tabular" inputMode="decimal" value={days} onChange={(e) => setDays(e.target.value.replace(/[^0-9.-]/g, ""))} />
        </Field>
        <Field label="Reason (kept in the balance history and audit log)">
          <input className="input" value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </form>
    </Modal>
  );
}
