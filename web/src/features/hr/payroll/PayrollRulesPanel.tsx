"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDate } from "@/features/hr/ui";
import {
  EMPLOYMENT_TYPES,
  REPORT_AS_LABELS,
  addGhanaPayrollPreset,
  addPayrollRule,
  getPayrollRules,
  type EmploymentType,
  type PayrollBracket,
  type PayrollRule,
  type PayrollRuleBase,
  type PayrollReportAs,
  type PayrollRuleKind,
  type PayrollRuleMethod,
} from "@/lib/hrApi";
import { money } from "./payrollUtils";

function describe(r: PayrollRule): string {
  if (!r.active) return "Ended";
  const base = r.base === "TAXABLE" ? "taxable pay" : r.base === "GROSS" ? "gross pay" : "basic pay";
  const amount = r.method === "PERCENT" ? `${Number(r.ratePercent)}% of ${base}`
    : r.method === "FIXED" ? money(r.fixedAmount)
      : `Bands on ${base}: ${r.brackets.map((b) => `${Number(b.ratePercent)}%${b.upTo != null ? ` to ${Number(b.upTo).toLocaleString()}` : " above"}`).join(", ")}`;
  return `${amount}${r.capAmount != null ? `, at most ${money(r.capAmount)}` : ""}${r.preTax ? " · before tax" : ""}`;
}

/** Deduction and employer-contribution rules. Nothing is preset; each change is a new dated row. */
export function PayrollRulesPanel({ branchId }: { branchId?: string }) {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [rules, setRules] = useState<PayrollRule[]>([]);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setRules(await authenticatedFetch((s) => getPayrollRules(s)));
    } catch (err) {
      setError(errorMessage(err, "Could not load payroll rules"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  const [presetBusy, setPresetBusy] = useState(false);
  const [notice, setNotice] = useState("");

  async function addPreset() {
    setPresetBusy(true);
    setError("");
    setNotice("");
    try {
      const added = await authenticatedFetch((s) => addGhanaPayrollPreset(s, `${new Date().getFullYear()}-01-01`));
      setNotice(added.length
        ? `Added ${added.map((r) => r.name).join(", ")}. Check the rates against current SSNIT and GRA figures before running payroll.`
        : "SSNIT and PAYE rules already exist; nothing was added.");
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not add the Ghana rules"));
    } finally {
      setPresetBusy(false);
    }
  }

  async function end(rule: PayrollRule) {
    const date = window.prompt(`End "${rule.name}" from which date? (YYYY-MM-DD)`, new Date().toISOString().slice(0, 10));
    if (!date) return;
    try {
      await authenticatedFetch((s) => addPayrollRule(s, { code: rule.code, branchId: rule.branchId ?? null, active: false, effectiveFrom: date }));
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not end the rule"));
    }
  }

  return (
    <section className="panel grid gap-3">
      <SectionHeader
        title="Deductions & employer contributions"
        description="Income tax, pension, welfare and similar. A change is a new row from a date; branch rows replace organisation rows with the same code. Mark SSNIT and PAYE rules with “Filed as” so the returns and year-to-date totals pick them up."
        actions={can("hr.settings.manage") ? (
          <span className="flex flex-wrap gap-2">
            {access?.allBranches ? (
              <button type="button" className="btn btn-secondary btn-sm" disabled={presetBusy} onClick={() => void addPreset()}>
                {presetBusy ? "Adding…" : "Add Ghana SSNIT & PAYE"}
              </button>
            ) : null}
            <button type="button" className="btn btn-primary btn-sm" onClick={() => setAdding(true)}>Add rule</button>
          </span>
        ) : null}
      />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {notice ? <p className="alert alert-info" role="status">{notice}</p> : null}
      {rules.length > 0 && !rules.some((r) => r.active && r.reportAs === "PAYE") ? (
        <p className="alert alert-warning">No rule is filed as PAYE: payslips will flag that no income tax was deducted.</p>
      ) : null}
      {rules.length === 0 ? <p className="alert alert-warning">No rules yet: payslips will show gross pay with no tax or SSNIT deducted. Start with “Add Ghana SSNIT &amp; PAYE”.</p> : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Rule</th><th>Type</th><th>Amount</th><th>Applies to</th><th>From</th><th /></tr></thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.id} className={r.active ? "" : "opacity-60"}>
                  <td><p className="font-semibold">{r.name}</p><p className="text-xs text-ink-muted">{r.code}{r.reportAs ? ` · filed as ${REPORT_AS_LABELS[r.reportAs]}` : ""}</p></td>
                  <td className="text-sm">{r.kind === "EMPLOYER_CONTRIBUTION" ? "Employer pays" : "Deducted"}</td>
                  <td className="text-sm">{describe(r)}</td>
                  <td className="text-sm">{r.branchId ? "This branch" : "All branches"}{r.employmentTypes.length ? ` · ${r.employmentTypes.map(humanize).join(", ")}` : ""}</td>
                  <td className="text-sm">{formatDate(r.effectiveFrom)}</td>
                  <td>{can("hr.settings.manage") && r.active && (r.branchId || access?.allBranches) ? <button type="button" className="btn btn-ghost btn-sm" onClick={() => end(r)}>End</button> : null}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adding ? <RuleModal branchId={branchId} central={!!access?.allBranches} onClose={() => setAdding(false)} onSaved={() => { setAdding(false); void load(); }} /> : null}
    </section>
  );
}

function RuleModal({ branchId, central, onClose, onSaved }: { branchId?: string; central: boolean; onClose: () => void; onSaved: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [kind, setKind] = useState<PayrollRuleKind>("EMPLOYEE_DEDUCTION");
  const [method, setMethod] = useState<PayrollRuleMethod>("PERCENT");
  const [base, setBase] = useState<PayrollRuleBase>("BASIC");
  const [rate, setRate] = useState("");
  const [fixed, setFixed] = useState("");
  const [cap, setCap] = useState("");
  const [preTax, setPreTax] = useState(false);
  const [reportAs, setReportAs] = useState<PayrollReportAs | "">("");
  const [bands, setBands] = useState<{ upTo: string; rate: string }[]>([{ upTo: "", rate: "0" }, { upTo: "", rate: "" }]);
  const [types, setTypes] = useState<EmploymentType[]>([]);
  const [scope, setScope] = useState(central ? "" : branchId ?? "");
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString().slice(0, 10));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const brackets: PayrollBracket[] = bands.map((b, i) => ({ upTo: i === bands.length - 1 || b.upTo === "" ? null : Number(b.upTo), ratePercent: Number(b.rate || 0) }));
    try {
      await authenticatedFetch((s) => addPayrollRule(s, {
        code, name, kind, method, base, preTax: kind === "EMPLOYEE_DEDUCTION" && base !== "TAXABLE" && preTax,
        ratePercent: method === "PERCENT" ? Number(rate) : null, fixedAmount: method === "FIXED" ? Number(fixed) : null,
        capAmount: cap ? Number(cap) : null, brackets: method === "BRACKETS" ? brackets : undefined, employmentTypes: types,
        branchId: scope || null, effectiveFrom, reportAs: reportAs || null,
      }));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the rule"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title="New payroll rule" onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="payroll-rule-form" className="btn btn-primary btn-md" disabled={busy || !code || !name}>Save</button>
        </div>
      )}>
      <form id="payroll-rule-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <div className="grid grid-cols-2 gap-3">
          <Field label="Code" hint="Same code + later date = new version"><input className="input uppercase" value={code} required maxLength={30} onChange={(e) => setCode(e.target.value.toUpperCase())} /></Field>
          <Field label="Name"><input className="input" value={name} required maxLength={120} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label="Who pays">
            <select className="select" value={kind} onChange={(e) => setKind(e.target.value as PayrollRuleKind)}>
              <option value="EMPLOYEE_DEDUCTION">Deducted from the employee</option>
              <option value="EMPLOYER_CONTRIBUTION">Paid by the employer</option>
            </select>
          </Field>
          <Field label="Calculated on">
            <select className="select" value={base} onChange={(e) => setBase(e.target.value as PayrollRuleBase)}>
              <option value="BASIC">Basic pay</option>
              <option value="GROSS">Gross pay</option>
              <option value="TAXABLE">Taxable pay</option>
            </select>
          </Field>
          <Field label="Method">
            <select className="select" value={method} onChange={(e) => setMethod(e.target.value as PayrollRuleMethod)}>
              <option value="PERCENT">Percentage</option>
              <option value="FIXED">Fixed amount</option>
              <option value="BRACKETS">Progressive bands</option>
            </select>
          </Field>
          {method === "PERCENT" ? <Field label="Rate (%)"><input className="input tabular" inputMode="decimal" required value={rate} onChange={(e) => setRate(e.target.value.replace(/[^0-9.]/g, ""))} /></Field> : null}
          {method === "FIXED" ? <Field label="Amount"><input className="input tabular" inputMode="decimal" required value={fixed} onChange={(e) => setFixed(e.target.value.replace(/[^0-9.]/g, ""))} /></Field> : null}
          {method === "BRACKETS" ? <span /> : null}
          <Field label="Maximum per payslip (optional)"><input className="input tabular" inputMode="decimal" value={cap} onChange={(e) => setCap(e.target.value.replace(/[^0-9.]/g, ""))} /></Field>
          <Field label="Takes effect from"><input type="date" className="input" value={effectiveFrom} required onChange={(e) => setEffectiveFrom(e.target.value)} /></Field>
        </div>
        {method === "BRACKETS" ? (
          <fieldset className="grid gap-2">
            <legend className="field-label">Bands (each rate applies to the part of pay inside the band)</legend>
            {bands.map((b, i) => (
              <div key={i} className="flex items-center gap-2 text-sm">
                <span className="w-24 text-ink-muted">{i === bands.length - 1 ? "Above" : "Up to"}</span>
                {i === bands.length - 1 ? <span className="w-32 text-ink-muted">the rest</span> : (
                  <input className="input w-32 tabular" inputMode="decimal" aria-label={`Band ${i + 1} upper limit`} value={b.upTo}
                    onChange={(e) => setBands(bands.map((x, j) => (j === i ? { ...x, upTo: e.target.value.replace(/[^0-9.]/g, "") } : x)))} />
                )}
                <input className="input w-24 tabular" inputMode="decimal" aria-label={`Band ${i + 1} rate`} value={b.rate}
                  onChange={(e) => setBands(bands.map((x, j) => (j === i ? { ...x, rate: e.target.value.replace(/[^0-9.]/g, "") } : x)))} />
                <span>%</span>
                {bands.length > 2 ? <button type="button" className="btn btn-ghost btn-sm" onClick={() => setBands(bands.filter((_, j) => j !== i))}>Remove</button> : null}
              </div>
            ))}
            <button type="button" className="btn btn-ghost btn-sm w-fit" onClick={() => setBands([...bands.slice(0, -1), { upTo: "", rate: "" }, bands[bands.length - 1]])}>Add band</button>
          </fieldset>
        ) : null}
        <Field label="Filed as" hint="For the SSNIT and PAYE returns and year-to-date totals">
          <select className="select" value={reportAs} onChange={(e) => setReportAs(e.target.value as PayrollReportAs | "")}>
            <option value="">Not filed (other deduction)</option>
            {(kind === "EMPLOYER_CONTRIBUTION" ? (["SSNIT_EMPLOYER", "TIER2"] as PayrollReportAs[]) : (["SSNIT_EMPLOYEE", "PAYE", "TIER2"] as PayrollReportAs[]))
              .map((v) => <option key={v} value={v}>{REPORT_AS_LABELS[v]}</option>)}
          </select>
        </Field>
        {kind === "EMPLOYEE_DEDUCTION" && base !== "TAXABLE" ? (
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={preTax} onChange={(e) => setPreTax(e.target.checked)} /> Taken before tax (reduces taxable pay)</label>
        ) : null}
        <div className="grid grid-cols-2 gap-3">
          <Field label="Branch">
            <select className="select" value={scope} onChange={(e) => setScope(e.target.value)}>
              {central ? <option value="">All branches</option> : null}
              {branchId ? <option value={branchId}>This branch only</option> : null}
            </select>
          </Field>
          <Field label="Employment types" hint="None selected = everyone">
            <select className="select" multiple value={types} onChange={(e) => setTypes(Array.from(e.target.selectedOptions).map((o) => o.value as EmploymentType))}>
              {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
            </select>
          </Field>
        </div>
      </form>
    </Modal>
  );
}
