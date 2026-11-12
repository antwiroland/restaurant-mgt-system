"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { SectionHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDate } from "@/features/hr/ui";
import { addTipRule, getPositions, getTipRules, type Position, type TipMethod, type TipRule, type TipSource } from "@/lib/hrApi";
import { methodLabel, sourceLabel, sourcePermission } from "./tipUtils";

/** Sharing rules for tips and service charge. Without a rule, a pool is shared by hours worked and the house keeps nothing. */
export function TipRulesPanel({ branchId }: { branchId?: string }) {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [rules, setRules] = useState<TipRule[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      await authenticatedFetch(async (s) => {
        const [r, p] = await Promise.all([getTipRules(s), getPositions(s)]);
        setRules(r);
        setPositions(p);
      });
    } catch (err) {
      setError(errorMessage(err, "Could not load sharing rules"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  const title = (id: string) => positions.find((p) => p.id === id)?.title ?? "Unknown position";
  const canAdd = can("hr.tips.manage", "hr.service_charge.manage");

  return (
    <section className="panel grid gap-3">
      <SectionHeader
        title="Sharing rules"
        description="How each pool is shared and what the house keeps. These are business and legal decisions; nothing is preset. A branch rule replaces the organisation rule; a change is a new rule from a date."
        actions={canAdd ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setAdding(true)}>Add rule</button> : null}
      />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {rules.length === 0 ? <p className="text-sm text-ink-soft">No rules yet: pools are shared by hours worked and the house keeps nothing.</p> : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Pool</th><th>Applies to</th><th>Sharing</th><th>House</th><th>From</th></tr></thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.id}>
                  <td className="font-semibold">{sourceLabel[r.source]}<p className="text-xs font-normal text-ink-muted">{r.taxable ? "Taxable" : "Not taxable"}</p></td>
                  <td className="text-sm">{r.branchId ? "This branch" : "All branches"}</td>
                  <td className="text-sm">
                    {methodLabel[r.method]}{r.minMinutes ? `, at least ${r.minMinutes} min` : ""}
                    {Object.keys(r.positionPoints).length ? (
                      <p className="text-xs text-ink-muted">{Object.entries(r.positionPoints).map(([id, pts]) => `${title(id)} ${Number(pts)}`).join(" · ")}</p>
                    ) : null}
                  </td>
                  <td className="num tabular">{Number(r.housePercent)}%</td>
                  <td className="text-sm">{formatDate(r.effectiveFrom)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adding ? (
        <RuleModal positions={positions.filter((p) => p.active)} branchId={branchId} central={!!access?.allBranches}
          sources={(["TIPS", "SERVICE_CHARGE"] as TipSource[]).filter((s) => can(sourcePermission[s]))}
          onClose={() => setAdding(false)} onSaved={() => { setAdding(false); void load(); }} />
      ) : null}
    </section>
  );
}

function RuleModal({ positions, branchId, central, sources, onClose, onSaved }: {
  positions: Position[]; branchId?: string; central: boolean; sources: TipSource[]; onClose: () => void; onSaved: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [source, setSource] = useState<TipSource>(sources[0] ?? "TIPS");
  const [method, setMethod] = useState<TipMethod>("HOURS");
  const [housePercent, setHousePercent] = useState("0");
  const [taxable, setTaxable] = useState(true);
  const [minMinutes, setMinMinutes] = useState("0");
  const [points, setPoints] = useState<Record<string, string>>({});
  const [scope, setScope] = useState(central ? "" : branchId ?? "");
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 10));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const positionPoints = Object.fromEntries(Object.entries(points).filter(([, v]) => v !== "").map(([k, v]) => [k, Number(v)]));
    try {
      await authenticatedFetch((s) => addTipRule(s, {
        branchId: scope || null, source, method, housePercent: Number(housePercent || 0), taxable, minMinutes: Number(minMinutes || 0),
        positionPoints, effectiveFrom,
      }));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the rule"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title="New sharing rule" onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="tip-rule-form" className="btn btn-primary btn-md" disabled={busy}>Save</button>
        </div>
      )}>
      <form id="tip-rule-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <div className="grid grid-cols-2 gap-3">
          <Field label="Pool">
            <select className="select" value={source} onChange={(e) => setSource(e.target.value as TipSource)}>
              {sources.map((s) => <option key={s} value={s}>{sourceLabel[s]}</option>)}
            </select>
          </Field>
          <Field label="Branch">
            <select className="select" value={scope} onChange={(e) => setScope(e.target.value)}>
              {central ? <option value="">All branches</option> : null}
              {branchId ? <option value={branchId}>This branch only</option> : null}
            </select>
          </Field>
          <Field label="Share">
            <select className="select" value={method} onChange={(e) => setMethod(e.target.value as TipMethod)}>
              <option value="HOURS">By hours worked</option>
              <option value="POINTS">By hours × position points</option>
              <option value="EQUAL">Equally</option>
            </select>
          </Field>
          <Field label="House keeps (%)"><input className="input tabular" inputMode="decimal" value={housePercent} onChange={(e) => setHousePercent(e.target.value.replace(/[^0-9.]/g, ""))} /></Field>
          <Field label="Minimum time worked (minutes)"><input className="input tabular" inputMode="numeric" value={minMinutes} onChange={(e) => setMinMinutes(e.target.value.replace(/\D/g, ""))} /></Field>
          <Field label="Takes effect from"><input type="date" className="input" value={effectiveFrom} required onChange={(e) => setEffectiveFrom(e.target.value)} /></Field>
        </div>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={taxable} onChange={(e) => setTaxable(e.target.checked)} /> Taxable when paid through payroll</label>
        <fieldset className="grid gap-2">
          <legend className="field-label">Position points {method === "POINTS" ? "" : "(optional: limits who takes part)"}</legend>
          <p className="text-xs text-ink-muted">Leave all empty for everyone with 1 point. Once any position has points, only listed positions take part; 0 excludes a position.</p>
          <div className="grid max-h-48 gap-1 overflow-y-auto sm:grid-cols-2">
            {positions.map((p) => (
              <label key={p.id} className="flex items-center justify-between gap-2 text-sm">
                <span>{p.title}</span>
                <input className="input w-20 tabular" inputMode="decimal" aria-label={`Points for ${p.title}`} value={points[p.id] ?? ""}
                  onChange={(e) => setPoints({ ...points, [p.id]: e.target.value.replace(/[^0-9.]/g, "") })} />
              </label>
            ))}
          </div>
        </fieldset>
      </form>
    </Modal>
  );
}
