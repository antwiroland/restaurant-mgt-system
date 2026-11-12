"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, Field, errorMessage, formatDate } from "@/features/hr/ui";
import { money } from "@/features/hr/payroll/payrollUtils";
import { TipPoolDrawer } from "@/features/hr/tips/TipPoolDrawer";
import { TipRulesPanel } from "@/features/hr/tips/TipRulesPanel";
import { poolTone, sourceLabel, sourcePermission } from "@/features/hr/tips/tipUtils";
import { isoDate } from "@/features/workforce/time";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import { createTipPool, getTipPools, type TipPoolSummary, type TipSource } from "@/lib/hrApi";

type Tab = "pools" | "rules";

export default function TipsPage() {
  return (
    <HrGuard anyOf={["hr.tips.manage", "hr.service_charge.manage", "hr.payroll.view", "hr.payroll.process"]}>
      <Tips />
    </HrGuard>
  );
}

function Tips() {
  const { authenticatedFetch } = useStaffSession();
  const { access, can } = useHrAccess();
  const [tab, setTab] = useState<Tab>("pools");
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState("");
  const [pools, setPools] = useState<TipPoolSummary[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");
  const sources = (["TIPS", "SERVICE_CHARGE"] as TipSource[]).filter((s) => can(sourcePermission[s]));

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
      setPools(await authenticatedFetch((s) => getTipPools(s, branchId)));
    } catch (err) {
      setError(errorMessage(err, "Could not load pools"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  useEffect(() => { void load(); }, [load]);

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Tips & service charge"
        icon="star"
        description="Pools collect tips or service charge from paid orders at a branch and share them with the people who worked there. Approved shares are paid with each person's payroll."
        actions={(
          <div className="flex flex-wrap gap-2">
            {branches.length > 1 ? (
              <select className="select w-auto" value={branchId} onChange={(e) => setBranchId(e.target.value)} aria-label="Branch">
                {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            ) : null}
            {sources.length && tab === "pools" ? (
              <button type="button" className="btn btn-primary btn-md" disabled={!branchId} onClick={() => setCreating(true)}><Icon name="plus" size={16} /> New pool</button>
            ) : null}
          </div>
        )}
      />
      <div className="segmented max-w-full overflow-x-auto" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "pools"} onClick={() => setTab("pools")}>Pools</button>
        <button type="button" role="tab" aria-selected={tab === "rules"} onClick={() => setTab("rules")}>Sharing rules</button>
      </div>
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {tab === "pools" ? (
        <section className="panel">
          {!pools ? <Skeleton className="h-40 w-full" /> : pools.length === 0 ? (
            <EmptyState compact icon="star" title="No pools yet" description={sources.length ? "Create a pool for a period that has ended." : undefined} />
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead><tr><th>Pool</th><th>Period</th><th className="num">Orders</th><th className="num">Collected</th><th className="num">Shared</th><th>Status</th></tr></thead>
                <tbody>
                  {pools.map((p) => (
                    <tr key={p.id} className="cursor-pointer" onClick={() => setOpen(p.id)}>
                      <td className="font-semibold">{p.poolNumber}<p className="text-xs font-normal text-ink-muted">{sourceLabel[p.source]}</p></td>
                      <td className="text-sm">{formatDate(p.periodStart)} – {formatDate(p.periodEnd)}</td>
                      <td className="num tabular">{p.orderCount}</td>
                      <td className="num tabular">{money(p.collectedAmount)}</td>
                      <td className="num tabular">{money(p.distributableAmount)}</td>
                      <td><span className={`badge badge-dot ${poolTone[p.status]}`}>{humanize(p.status)}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : <TipRulesPanel branchId={branchId || undefined} />}

      {creating ? <NewPoolModal branchId={branchId} sources={sources} onClose={() => setCreating(false)} onCreated={(id) => { setCreating(false); void load(); setOpen(id); }} /> : null}
      {open ? <TipPoolDrawer id={open} onClose={() => setOpen(null)} onChanged={() => void load()} /> : null}
    </div>
  );
}

function NewPoolModal({ branchId, sources, onClose, onCreated }: { branchId: string; sources: TipSource[]; onClose: () => void; onCreated: (id: string) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const now = new Date();
  const [source, setSource] = useState<TipSource>(sources[0]);
  const [periodStart, setPeriodStart] = useState(isoDate(new Date(now.getFullYear(), now.getMonth() - 1, 1)));
  const [periodEnd, setPeriodEnd] = useState(isoDate(new Date(now.getFullYear(), now.getMonth(), 0)));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      onCreated((await authenticatedFetch((s) => createTipPool(s, { branchId, source, periodStart, periodEnd }))).summary.id);
    } catch (err) {
      setError(errorMessage(err, "Could not create the pool"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title="New pool" onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="tip-pool-form" className="btn btn-primary btn-md" disabled={busy}>Create pool</button>
        </div>
      )}>
      <form id="tip-pool-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <Field label="Pool">
          <select className="select" value={source} onChange={(e) => setSource(e.target.value as TipSource)}>
            {sources.map((s) => <option key={s} value={s}>{sourceLabel[s]}</option>)}
          </select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="From"><input type="date" className="input" value={periodStart} required onChange={(e) => setPeriodStart(e.target.value)} /></Field>
          <Field label="To"><input type="date" className="input" value={periodEnd} min={periodStart} required onChange={(e) => setPeriodEnd(e.target.value)} /></Field>
        </div>
        <p className="text-xs text-ink-muted">Up to 31 days that have already ended. Orders count in the period of their first successful payment; fully refunded orders are left out.</p>
      </form>
    </Modal>
  );
}
