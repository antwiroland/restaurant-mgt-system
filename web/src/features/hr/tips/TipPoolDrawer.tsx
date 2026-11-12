"use client";

import { useCallback, useEffect, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Detail, Field, errorMessage, formatDate, formatDateTime } from "@/features/hr/ui";
import { money } from "@/features/hr/payroll/payrollUtils";
import { formatMinutes } from "@/features/workforce/time";
import { getTipPool, tipPoolAction, type TipPoolDetail } from "@/lib/hrApi";
import { methodLabel, poolTone, sourceLabel, sourcePermission } from "./tipUtils";

/** A tips or service-charge pool: what was collected, the rule applied, each person's share and the workflow actions. */
export function TipPoolDrawer({ id, onClose, onChanged }: { id: string; onClose: () => void; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [pool, setPool] = useState<TipPoolDetail | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setPool(await authenticatedFetch((s) => getTipPool(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the pool"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  async function act(action: "calculate" | "approve" | "reopen" | "cancel", body: Record<string, unknown> = {}, done = "") {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      setPool(await authenticatedFetch((s) => tipPoolAction(s, id, action, body)));
      setReason("");
      setNotice(done);
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not update the pool"));
    } finally {
      setBusy(false);
    }
  }

  const s = pool?.summary;
  const manage = !!s && can(sourcePermission[s.source]);
  const unshared = pool ? pool.allocations.filter((a) => Number(a.amount) === 0) : [];

  const footer = s && manage ? (
    <div className="flex w-full flex-wrap justify-end gap-2">
      {s.status === "DRAFT" || s.status === "CALCULATED" ? (
        <button type="button" className="btn btn-primary btn-md" disabled={busy} onClick={() => act("calculate", {}, "Calculated from paid orders and time worked.")}>
          {s.calculatedAt ? "Recalculate" : "Calculate"}
        </button>
      ) : null}
      {s.status === "CALCULATED" ? (
        <button type="button" className="btn btn-success btn-md" disabled={busy || pool!.calculatedByMe}
          title={pool!.calculatedByMe ? "Someone other than the person who calculated this pool must approve it" : undefined}
          onClick={() => act("approve", {}, "Approved. Shares will be paid with each person's next payroll.")}>
          Approve
        </button>
      ) : null}
      {s.status === "CALCULATED" || s.status === "APPROVED" ? (
        <button type="button" className="btn btn-ghost btn-md" disabled={busy || !reason.trim()} title="Give a reason below" onClick={() => act("reopen", { reason }, "Returned to draft.")}>
          Return to draft
        </button>
      ) : null}
      {s.status !== "PAID" && s.status !== "CANCELLED" ? (
        <button type="button" className="btn btn-ghost btn-md" disabled={busy || !reason.trim()} title="Give a reason below" onClick={() => act("cancel", { reason }, "Pool cancelled.")}>
          Cancel pool
        </button>
      ) : null}
    </div>
  ) : null;

  return (
    <Drawer open title={s ? `${s.poolNumber} · ${sourceLabel[s.source]}` : "Pool"} onClose={onClose} footer={footer}>
      {!pool || !s ? (error ? <p className="alert alert-danger">{error}</p> : <Skeleton className="h-64 w-full" />) : (
        <div className="grid gap-5">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {notice ? <p className="alert alert-info" role="status">{notice}</p> : null}
          <div className="flex flex-wrap items-center gap-2">
            <span className={`badge badge-dot ${poolTone[s.status]}`}>{humanize(s.status)}</span>
            <span className="text-sm text-ink-muted">{s.branchName} · {formatDate(s.periodStart)} – {formatDate(s.periodEnd)}</span>
          </div>
          {s.cancelReason ? <p className="alert alert-danger text-sm">Cancelled: {s.cancelReason}</p> : null}
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Detail label="Paid orders" value={s.orderCount} />
            <Detail label="Collected" value={money(s.collectedAmount)} />
            <Detail label="Kept by the house" value={money(s.houseAmount)} />
            <Detail label="Shared with staff" value={money(s.distributableAmount)} />
          </dl>
          {pool.rule ? (
            <p className="rounded-xl bg-sunken p-3 text-sm text-ink-soft">
              Rule ({pool.rule.scope === "DEFAULT" ? "default — no rule configured" : humanize(pool.rule.scope)}): {methodLabel[pool.rule.method]},
              house keeps {Number(pool.rule.housePercent)}%, {pool.rule.minMinutes ? `at least ${formatMinutes(pool.rule.minMinutes)} worked, ` : ""}
              {pool.rule.taxable ? "taxable" : "not taxable"}.
            </p>
          ) : null}

          <section className="grid gap-2">
            <h3 className="text-sm font-semibold">Shares</h3>
            {pool.allocations.length === 0 ? <p className="text-sm text-ink-soft">{s.calculatedAt ? "Nobody clocked time at this branch in the period." : "Calculate the pool to share it."}</p> : (
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead><tr><th>Employee</th><th className="num">Time worked</th><th className="num">Points</th><th className="num">Share</th></tr></thead>
                  <tbody>
                    {pool.allocations.map((a) => (
                      <tr key={a.id}>
                        <td className="font-semibold">{a.employeeName}<p className="text-xs font-normal text-ink-muted">{a.positionTitle ?? "No position"}{a.inPayroll ? " · in payroll" : ""}</p></td>
                        <td className="num tabular">{formatMinutes(a.minutes)}</td>
                        <td className="num tabular">{Number(a.points)}</td>
                        <td className="num tabular font-semibold">{money(a.amount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {unshared.length && s.calculatedAt ? <p className="text-xs text-ink-muted">{unshared.length} {unshared.length === 1 ? "person gets" : "people get"} no share (below the minimum time or no points for their position).</p> : null}
          </section>
          {s.calculatedAt ? <p className="text-xs text-ink-muted">Calculated {formatDateTime(s.calculatedAt)}{s.approvedAt ? ` · approved ${formatDateTime(s.approvedAt)}` : ""}. Shares are paid with each person&apos;s home-branch payroll.</p> : null}
          {manage && s.status !== "PAID" && s.status !== "CANCELLED" ? (
            <Field label="Reason (to return to draft or cancel)"><input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} /></Field>
          ) : null}
        </div>
      )}
    </Drawer>
  );
}
