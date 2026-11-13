"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDate } from "@/features/hr/ui";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import {
  APPROVAL_STEPS,
  EMPLOYMENT_TYPES,
  addLeavePolicy,
  createLeaveType,
  getLeavePolicies,
  getLeaveTypes,
  updateLeaveType,
  type Accrual,
  type ApprovalStep,
  type EmploymentType,
  type LeavePolicy,
  type LeaveType,
  type LeaveTypeInput,
} from "@/lib/hrApi";
import { formatDays, stepLabel } from "./leaveUtils";

/** Leave types and effective-dated entitlements. Types are organisation-wide (central HR); entitlements can be per branch. */
export function LeaveSetupPanels() {
  const { authenticatedFetch } = useStaffSession();
  const { access, can } = useHrAccess();
  const [types, setTypes] = useState<LeaveType[]>([]);
  const [policies, setPolicies] = useState<LeavePolicy[]>([]);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [editing, setEditing] = useState<LeaveType | "new" | null>(null);
  const [addingPolicy, setAddingPolicy] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      await authenticatedFetch(async (s) => {
        const [t, p, b] = await Promise.all([getLeaveTypes(s), getLeavePolicies(s), getBranches(s)]);
        setTypes(t);
        setPolicies(p);
        setBranches(b);
      });
    } catch (err) {
      setError(errorMessage(err, "Could not load leave settings"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  const central = !!access?.allBranches && can("hr.leave.manage");
  const branchName = (id?: string | null) => (id ? branches.find((b) => b.id === id)?.name ?? "Branch" : "All branches");

  return (
    <div className="grid gap-5">
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <section className="panel grid gap-3">
        <SectionHeader
          title="Leave types"
          description="What staff can request, how days are counted, and who approves. Changes apply to new requests only."
          actions={central ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditing("new")}>Add leave type</button> : null}
        />
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Type</th><th>Counts</th><th>Balance</th><th>Approval</th><th>Rules</th><th /></tr></thead>
            <tbody>
              {types.map((t) => (
                <tr key={t.id} className={t.active ? "" : "opacity-60"}>
                  <td><p className="font-semibold">{t.name}</p><p className="text-xs text-ink-muted">{t.code}{t.paid ? "" : " · unpaid"}{t.active ? "" : " · inactive"}</p></td>
                  <td className="text-sm">{t.countBasis === "WORKDAYS" ? "Working days" : "Calendar days"}</td>
                  <td className="text-sm">{t.deductsBalance ? (t.allowNegative ? "Deducted, may go negative" : "Deducted") : "Not tracked"}</td>
                  <td className="text-sm">{t.approvalSteps.map((s) => stepLabel[s]).join(" → ")}</td>
                  <td className="text-xs">
                    {t.requiresDocument ? <span className="badge badge-info mr-1">Document</span> : null}
                    {t.maxDaysPerRequest ? <span className="badge badge-neutral">Max {formatDays(t.maxDaysPerRequest)} days</span> : null}
                    {t.maxDaysPerYear ? <span className="badge badge-neutral">{formatDays(t.maxDaysPerYear)} days a year</span> : null}
                  </td>
                  <td>{central ? <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditing(t)}>Edit</button> : null}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel grid gap-3">
        <SectionHeader
          title="Entitlements"
          description="Days per year by leave type, optionally for one branch or employment type (the most specific match wins). A change is a new row with a later start date, so past balances stay explainable. Entitlements are business and legal decisions; none are preset."
          actions={can("hr.leave.manage") ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setAddingPolicy(true)}>Add entitlement</button> : null}
        />
        {policies.length === 0 ? <p className="text-sm text-ink-soft">No entitlements yet. Without one, balance-tracked leave has 0 days available.</p> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Leave type</th><th>Applies to</th><th className="num">Days / year</th><th>Accrual</th><th className="num">Carry over</th><th>Eligible after</th><th>From</th></tr></thead>
              <tbody>
                {policies.map((p) => (
                  <tr key={p.id}>
                    <td className="font-semibold">{p.leaveTypeName}</td>
                    <td className="text-sm">{branchName(p.branchId)}{p.employmentType ? ` · ${humanize(p.employmentType)}` : ""}</td>
                    <td className="num tabular">{formatDays(p.annualDays)}</td>
                    <td className="text-sm">{p.accrual === "MONTHLY" ? "Monthly" : "Up front"}</td>
                    <td className="num tabular">{formatDays(p.carryOverMaxDays)}</td>
                    <td className="text-sm">{p.minServiceDays ? `${p.minServiceDays} days` : "—"}</td>
                    <td className="text-sm">{formatDate(p.effectiveFrom)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {editing ? <TypeModal type={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void load(); }} /> : null}
      {addingPolicy ? (
        <PolicyModal types={types.filter((t) => t.active && t.deductsBalance)} branches={branches.filter((b) => access?.allBranches || access?.branchIds.includes(b.id))}
          central={!!access?.allBranches} onClose={() => setAddingPolicy(false)} onSaved={() => { setAddingPolicy(false); void load(); }} />
      ) : null}
    </div>
  );
}

function TypeModal({ type, onClose, onSaved }: { type: LeaveType | null; onClose: () => void; onSaved: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState<LeaveTypeInput>(type ? { ...type } : {
    code: "", name: "", paid: true, deductsBalance: true, requiresDocument: false, allowNegative: false,
    countBasis: "WORKDAYS", approvalSteps: ["BRANCH_MANAGER"], maxDaysPerRequest: null, maxDaysPerYear: null, active: true, displayOrder: 0,
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof LeaveTypeInput>(key: K, value: LeaveTypeInput[K]) => setForm((f) => ({ ...f, [key]: value }));

  function toggleStep(step: ApprovalStep) {
    const has = form.approvalSteps.includes(step);
    const next = has ? form.approvalSteps.filter((s) => s !== step) : [...form.approvalSteps, step];
    set("approvalSteps", APPROVAL_STEPS.filter((s) => next.includes(s)));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => (type ? updateLeaveType(s, type.id, form) : createLeaveType(s, form)));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the leave type"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title={type ? `Edit ${type.name}` : "New leave type"} onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="leave-type-form" className="btn btn-primary btn-md" disabled={busy || form.approvalSteps.length === 0}>Save</button>
        </div>
      )}>
      <form id="leave-type-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <div className="grid grid-cols-2 gap-3">
          <Field label="Code"><input className="input uppercase" value={form.code} disabled={!!type} required maxLength={30} pattern="[A-Za-z0-9_]{2,30}" onChange={(e) => set("code", e.target.value.toUpperCase())} /></Field>
          <Field label="Name"><input className="input" value={form.name} required maxLength={80} onChange={(e) => set("name", e.target.value)} /></Field>
        </div>
        <Field label="Count">
          <select className="select" value={form.countBasis} onChange={(e) => set("countBasis", e.target.value as LeaveTypeInput["countBasis"])}>
            <option value="WORKDAYS">Working days (skip non-working weekdays and public holidays)</option>
            <option value="CALENDAR">Calendar days</option>
          </select>
        </Field>
        <div className="grid gap-2 text-sm">
          <label className="flex items-center gap-2"><input type="checkbox" checked={form.paid} onChange={(e) => set("paid", e.target.checked)} /> Paid leave</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={form.deductsBalance} onChange={(e) => set("deductsBalance", e.target.checked)} /> Deducted from a yearly balance</label>
          {form.deductsBalance ? (
            <label className="flex items-center gap-2"><input type="checkbox" checked={form.allowNegative} onChange={(e) => set("allowNegative", e.target.checked)} /> Allow the balance to go negative</label>
          ) : null}
          <label className="flex items-center gap-2"><input type="checkbox" checked={form.requiresDocument} onChange={(e) => set("requiresDocument", e.target.checked)} /> Needs a supporting document before final approval</label>
          {type ? <label className="flex items-center gap-2"><input type="checkbox" checked={form.active} onChange={(e) => set("active", e.target.checked)} /> Offered to staff</label> : null}
        </div>
        <fieldset className="grid gap-2 text-sm">
          <legend className="field-label">Approval chain (in this order)</legend>
          {APPROVAL_STEPS.map((step) => (
            <label key={step} className="flex items-center gap-2">
              <input type="checkbox" checked={form.approvalSteps.includes(step)} onChange={() => toggleStep(step)} /> {stepLabel[step]}
            </label>
          ))}
          <p className="text-xs text-ink-muted">The reporting-manager step is skipped for employees without a reporting manager who has a login. Each step needs a different person.</p>
        </fieldset>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Max days per request (optional)">
            <input className="input tabular" inputMode="decimal" value={form.maxDaysPerRequest ?? ""}
              onChange={(e) => set("maxDaysPerRequest", e.target.value ? Number(e.target.value.replace(/[^0-9.]/g, "")) : null)} />
          </Field>
          <Field label="Max days per year (optional)">
            <input className="input tabular" inputMode="decimal" value={form.maxDaysPerYear ?? ""}
              onChange={(e) => set("maxDaysPerYear", e.target.value ? Number(e.target.value.replace(/[^0-9.]/g, "")) : null)} />
          </Field>
          <Field label="Display order">
            <input className="input tabular" inputMode="numeric" value={form.displayOrder} onChange={(e) => set("displayOrder", Number(e.target.value.replace(/\D/g, "") || 0))} />
          </Field>
        </div>
      </form>
    </Modal>
  );
}

function PolicyModal({ types, branches, central, onClose, onSaved }: {
  types: LeaveType[]; branches: BranchRecord[]; central: boolean; onClose: () => void; onSaved: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [leaveTypeId, setLeaveTypeId] = useState(types[0]?.id ?? "");
  const [branchId, setBranchId] = useState(central ? "" : branches[0]?.id ?? "");
  const [employmentType, setEmploymentType] = useState<EmploymentType | "">("");
  const [annualDays, setAnnualDays] = useState("");
  const [accrual, setAccrual] = useState<Accrual>("UPFRONT");
  const [carryOver, setCarryOver] = useState("0");
  const [minService, setMinService] = useState("0");
  const [effectiveFrom, setEffectiveFrom] = useState(`${new Date().getFullYear()}-01-01`);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => addLeavePolicy(s, {
        leaveTypeId, branchId: branchId || null, employmentType: employmentType || null, annualDays: Number(annualDays), accrual,
        carryOverMaxDays: Number(carryOver || 0), minServiceDays: Number(minService || 0), effectiveFrom,
      }));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the entitlement"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title="Add entitlement" onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="leave-policy-form" className="btn btn-primary btn-md" disabled={busy || !leaveTypeId || annualDays === ""}>Save</button>
        </div>
      )}>
      <form id="leave-policy-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <Field label="Leave type">
          <select className="select" value={leaveTypeId} onChange={(e) => setLeaveTypeId(e.target.value)}>
            {types.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Branch">
            <select className="select" value={branchId} onChange={(e) => setBranchId(e.target.value)}>
              {central ? <option value="">All branches</option> : null}
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </Field>
          <Field label="Employment type">
            <select className="select" value={employmentType} onChange={(e) => setEmploymentType(e.target.value as EmploymentType | "")}>
              <option value="">Everyone</option>
              {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
            </select>
          </Field>
          <Field label="Days per year" hint="Pro-rated by month for people hired during the year">
            <input className="input tabular" inputMode="decimal" required value={annualDays} onChange={(e) => setAnnualDays(e.target.value.replace(/[^0-9.]/g, ""))} />
          </Field>
          <Field label="Accrual">
            <select className="select" value={accrual} onChange={(e) => setAccrual(e.target.value as Accrual)}>
              <option value="UPFRONT">All days on 1 January</option>
              <option value="MONTHLY">A twelfth each month</option>
            </select>
          </Field>
          <Field label="Max days carried into next year">
            <input className="input tabular" inputMode="decimal" value={carryOver} onChange={(e) => setCarryOver(e.target.value.replace(/[^0-9.]/g, ""))} />
          </Field>
          <Field label="Eligible after (days of service)">
            <input className="input tabular" inputMode="numeric" value={minService} onChange={(e) => setMinService(e.target.value.replace(/\D/g, ""))} />
          </Field>
        </div>
        <Field label="Takes effect from">
          <input type="date" className="input" value={effectiveFrom} required onChange={(e) => setEffectiveFrom(e.target.value)} />
        </Field>
      </form>
    </Modal>
  );
}
