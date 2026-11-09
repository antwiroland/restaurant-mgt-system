"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDate } from "@/features/hr/ui";
import { DAY_CODES, trimTime } from "@/features/hr/schedule/scheduleUtils";
import { formatMinutes, isoDate } from "@/features/workforce/time";
import {
  addHoliday,
  addOvertimeRule,
  decideOvertime,
  deleteHoliday,
  getHolidays,
  getOvertimeRequests,
  getOvertimeRules,
  listEmployees,
  requestOvertime,
  type EmployeeSummary,
  type OvertimeRequest,
  type OvertimeRule,
  type PublicHoliday,
} from "@/lib/hrApi";

// ------------------------------------------------------------------ pre-authorised overtime

export function OvertimeRequestsPanel({ branchId }: { branchId: string }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [rows, setRows] = useState<OvertimeRequest[] | null>(null);
  const [error, setError] = useState("");
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getOvertimeRequests(s)));
    } catch (err) {
      setError(errorMessage(err, "Could not load overtime requests"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function decide(r: OvertimeRequest, approve: boolean) {
    try {
      await authenticatedFetch((s) => decideOvertime(s, r.id, { approve }));
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not record the decision"));
    }
  }

  return (
    <section className="grid gap-3">
      <SectionHeader
        title="Overtime pre-authorisation"
        description="Approve extra hours before they are worked. Approved minutes are pre-filled when the timesheet is approved. You can't approve your own request."
        actions={can("hr.attendance.manage", "hr.schedule.manage", "hr.timesheet.review") ? (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setAdding(true)}><Icon name="plus" size={16} /> Request</button>
        ) : null}
      />
      {error ? <ErrorState message={error} onRetry={load} /> : null}
      {!rows ? <Skeleton className="h-32 w-full" /> : rows.length === 0 ? <EmptyState compact icon="timer" title="No overtime requests" /> : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Employee</th><th>Date</th><th className="num">Hours</th><th>Reason</th><th>Status</th><th className="num" /></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="font-semibold">{r.employeeName}</td>
                  <td>{formatDate(r.workDate)}</td>
                  <td className="num tabular">{formatMinutes(r.minutes)}</td>
                  <td className="max-w-xs text-sm">{r.reason}</td>
                  <td><span className={`badge ${r.status === "APPROVED" ? "badge-success" : r.status === "PENDING" ? "badge-pending" : "badge-neutral"}`}>{humanize(r.status)}</span></td>
                  <td className="num">
                    {r.status === "PENDING" && can("hr.overtime.approve") ? (
                      <div className="flex justify-end gap-1">
                        <button type="button" className="btn btn-success btn-sm" onClick={() => decide(r, true)}>Approve</button>
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => decide(r, false)}>Reject</button>
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adding ? <OvertimeRequestDialog branchId={branchId} onClose={() => setAdding(false)} onDone={() => { setAdding(false); void load(); }} /> : null}
    </section>
  );
}

function OvertimeRequestDialog({ branchId, onClose, onDone }: { branchId: string; onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [employees, setEmployees] = useState<EmployeeSummary[]>([]);
  const [form, setForm] = useState({ employeeId: "", workDate: isoDate(new Date()), hours: "1", reason: "" });
  const [error, setError] = useState("");

  useEffect(() => {
    authenticatedFetch((s) => listEmployees(s, { branchId, size: 100 })).then((p) => setEmployees(p.items)).catch(() => setEmployees([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const minutes = Math.round(Number(form.hours) * 60);
    if (!form.employeeId || !form.reason.trim() || !(minutes >= 15)) return setError("Choose an employee, at least 15 minutes and a reason.");
    try {
      await authenticatedFetch((s) => requestOvertime(s, { employeeId: form.employeeId, branchId, workDate: form.workDate, minutes, reason: form.reason.trim() }));
      onDone();
    } catch (err) {
      setError(errorMessage(err, "Could not request overtime"));
    }
  }

  return (
    <Modal open title="Request overtime" onClose={onClose} footer={(
      <>
        <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
        <button type="submit" form="ot-form" className="btn btn-primary btn-md">Send for approval</button>
      </>
    )}>
      <form id="ot-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="Employee" className="sm:col-span-2">
          <select className="select" value={form.employeeId} onChange={(e) => setForm((f) => ({ ...f, employeeId: e.target.value }))}>
            <option value="">Choose…</option>
            {employees.map((e) => <option key={e.id} value={e.id}>{e.fullName}</option>)}
          </select>
        </Field>
        <Field label="Date"><input type="date" className="input" value={form.workDate} onChange={(e) => setForm((f) => ({ ...f, workDate: e.target.value }))} /></Field>
        <Field label="Extra hours"><input className="input" inputMode="decimal" value={form.hours} onChange={(e) => setForm((f) => ({ ...f, hours: e.target.value }))} /></Field>
        <Field label="Reason" className="sm:col-span-2"><input className="input" value={form.reason} maxLength={500} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))} /></Field>
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ rules & holidays

export function OvertimeRulesPanel({ branchId }: { branchId: string }) {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [rules, setRules] = useState<OvertimeRule[] | null>(null);
  const [holidays, setHolidays] = useState<PublicHoliday[]>([]);
  const [year, setYear] = useState(new Date().getFullYear());
  const [error, setError] = useState("");
  const [adding, setAdding] = useState(false);
  const [holiday, setHoliday] = useState({ date: "", name: "", everywhere: true });
  const canEdit = can("hr.settings.manage");

  const load = useCallback(async () => {
    setError("");
    try {
      await authenticatedFetch(async (s) => {
        const [r, h] = await Promise.all([getOvertimeRules(s, branchId), getHolidays(s, year)]);
        setRules(r);
        setHolidays(h);
      });
    } catch (err) {
      setError(errorMessage(err, "Could not load overtime settings"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, year]);

  useEffect(() => { void load(); }, [load]);

  async function saveHoliday(event: FormEvent) {
    event.preventDefault();
    if (!holiday.date || !holiday.name.trim()) return setError("Give the holiday a date and a name.");
    try {
      await authenticatedFetch((s) => addHoliday(s, { date: holiday.date, name: holiday.name.trim(), branchId: holiday.everywhere ? null : branchId }));
      setHoliday({ date: "", name: "", everywhere: true });
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not add the holiday"));
    }
  }

  async function removeHoliday(id: string) {
    try {
      await authenticatedFetch((s) => deleteHoliday(s, id));
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not remove the holiday"));
    }
  }

  const describe = (r: OvertimeRule) => [
    r.dailyThresholdMinutes ? `after ${formatMinutes(r.dailyThresholdMinutes)}/day ×${r.dailyMultiplier}` : null,
    r.weeklyThresholdMinutes ? `after ${formatMinutes(r.weeklyThresholdMinutes)}/week ×${r.weeklyMultiplier}` : null,
    r.weekendDays.length ? `weekend ${r.weekendDays.join(",")}${r.weekendMultiplier ? ` ×${r.weekendMultiplier}` : ""}` : null,
    r.holidayMultiplier ? `holidays ×${r.holidayMultiplier}` : null,
    r.nightStart ? `night ${trimTime(r.nightStart)}–${trimTime(r.nightEnd)}${r.nightMultiplier ? ` ×${r.nightMultiplier}` : ""}` : null,
    r.requiresApproval ? "needs approval" : "paid without approval",
  ].filter(Boolean).join(" · ");

  return (
    <section className="grid gap-5">
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <div className="grid gap-3">
        <SectionHeader
          title="Overtime rules"
          description="Rules take effect from a date and are never edited, so past timesheets stay explainable. Multipliers are used by payroll; set them to the rules that apply to you."
          actions={canEdit ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setAdding(true)}><Icon name="plus" size={16} /> New rule</button> : null}
        />
        {!rules ? <Skeleton className="h-24 w-full" /> : (
          <ul className="grid gap-2">
            {rules.map((r, i) => (
              <li key={r.id ?? `current-${i}`} className={`rounded-xl border p-3 text-sm ${i === 0 ? "border-brand/40 bg-brand-subtle" : "border-line"}`}>
                <p className="font-semibold">{i === 0 ? r.note : `From ${formatDate(r.effectiveFrom)} · ${r.branchId ? "this branch" : "all branches"}`}</p>
                <p className="text-ink-soft">{describe(r)}</p>
                {i > 0 && r.note ? <p className="text-xs text-ink-muted">{r.note}</p> : null}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="grid gap-3">
        <SectionHeader
          title="Public holidays"
          actions={(
            <select className="select w-auto" value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label="Year">
              {[year - 1, year, year + 1].map((y) => <option key={y} value={y}>{y}</option>)}
            </select>
          )}
        />
        {holidays.length === 0 ? <p className="text-sm text-ink-soft">No holidays recorded for {year}.</p> : (
          <ul className="grid gap-1 text-sm">
            {holidays.map((h) => (
              <li key={h.id} className="flex items-center justify-between rounded-lg border border-line px-3 py-2">
                <span><span className="font-semibold tabular">{formatDate(h.date)}</span> · {h.name}{h.branchId ? " (this branch)" : ""}</span>
                {canEdit && (h.branchId || access?.allBranches) ? (
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => removeHoliday(h.id)} aria-label={`Remove ${h.name}`}><Icon name="trash" size={16} /></button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        {canEdit ? (
          <form onSubmit={saveHoliday} className="flex flex-wrap items-end gap-2">
            <Field label="Date"><input type="date" className="input" value={holiday.date} onChange={(e) => setHoliday((h) => ({ ...h, date: e.target.value }))} /></Field>
            <Field label="Name"><input className="input" value={holiday.name} maxLength={120} onChange={(e) => setHoliday((h) => ({ ...h, name: e.target.value }))} /></Field>
            {access?.allBranches ? (
              <label className="flex items-center gap-2 pb-3 text-sm">
                <input type="checkbox" checked={holiday.everywhere} onChange={(e) => setHoliday((h) => ({ ...h, everywhere: e.target.checked }))} /> All branches
              </label>
            ) : null}
            <button type="submit" className="btn btn-secondary btn-md">Add holiday</button>
          </form>
        ) : null}
      </div>

      {adding ? <RuleDialog branchId={branchId} central={!!access?.allBranches} onClose={() => setAdding(false)} onDone={() => { setAdding(false); void load(); }} /> : null}
    </section>
  );
}

function RuleDialog({ branchId, central, onClose, onDone }: { branchId: string; central: boolean; onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState({
    scope: "branch" as "branch" | "org", effectiveFrom: isoDate(new Date()), dailyHours: "8", weeklyHours: "48", dailyMultiplier: "1.5",
    weeklyMultiplier: "1.5", weekendDays: new Set<string>(), weekendMultiplier: "", holidayMultiplier: "2", nightStart: "", nightEnd: "",
    nightMultiplier: "", requiresApproval: true, note: "",
  });
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    const minutes = (v: string) => (v.trim() ? Math.round(Number(v) * 60) : null);
    if (!form.dailyHours.trim() && !form.weeklyHours.trim()) return setError("Set a daily threshold, a weekly threshold, or both.");
    if (!!form.nightStart !== !!form.nightEnd) return setError("Give both night start and end, or neither.");
    try {
      await authenticatedFetch((s) => addOvertimeRule(s, {
        branchId: form.scope === "org" ? null : branchId, effectiveFrom: form.effectiveFrom,
        dailyThresholdMinutes: minutes(form.dailyHours), weeklyThresholdMinutes: minutes(form.weeklyHours),
        dailyMultiplier: form.dailyMultiplier, weeklyMultiplier: form.weeklyMultiplier,
        weekendDays: DAY_CODES.filter((d) => form.weekendDays.has(d)).join(","), weekendMultiplier: form.weekendMultiplier || null,
        holidayMultiplier: form.holidayMultiplier || null, nightStart: form.nightStart || null, nightEnd: form.nightEnd || null,
        nightMultiplier: form.nightMultiplier || null, requiresApproval: form.requiresApproval, note: form.note.trim() || undefined,
      }));
      onDone();
    } catch (err) {
      setError(errorMessage(err, "Could not save the rule"));
    }
  }

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));
  return (
    <Modal open title="New overtime rule" onClose={onClose} footer={(
      <>
        <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
        <button type="submit" form="rule-form" className="btn btn-primary btn-md">Save rule</button>
      </>
    )}>
      <form id="rule-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="Takes effect from"><input type="date" className="input" value={form.effectiveFrom} onChange={set("effectiveFrom")} /></Field>
        {central ? (
          <Field label="Applies to">
            <select className="select" value={form.scope} onChange={set("scope")}>
              <option value="branch">This branch</option>
              <option value="org">All branches (default)</option>
            </select>
          </Field>
        ) : <div />}
        <Field label="Daily overtime after (hours)"><input className="input" inputMode="decimal" value={form.dailyHours} onChange={set("dailyHours")} /></Field>
        <Field label="Daily multiplier"><input className="input" inputMode="decimal" value={form.dailyMultiplier} onChange={set("dailyMultiplier")} /></Field>
        <Field label="Weekly overtime after (hours)"><input className="input" inputMode="decimal" value={form.weeklyHours} onChange={set("weeklyHours")} /></Field>
        <Field label="Weekly multiplier"><input className="input" inputMode="decimal" value={form.weeklyMultiplier} onChange={set("weeklyMultiplier")} /></Field>
        <fieldset className="sm:col-span-2">
          <legend className="field-label">Weekend days (premium)</legend>
          <div className="mt-1 flex flex-wrap gap-1">
            {DAY_CODES.map((d) => (
              <button key={d} type="button" aria-pressed={form.weekendDays.has(d)} className={`chip ${form.weekendDays.has(d) ? "bg-brand text-white" : ""}`}
                onClick={() => setForm((f) => { const days = new Set(f.weekendDays); if (days.has(d)) days.delete(d); else days.add(d); return { ...f, weekendDays: days }; })}>
                {d}
              </button>
            ))}
          </div>
        </fieldset>
        <Field label="Weekend multiplier"><input className="input" inputMode="decimal" value={form.weekendMultiplier} onChange={set("weekendMultiplier")} /></Field>
        <Field label="Holiday multiplier"><input className="input" inputMode="decimal" value={form.holidayMultiplier} onChange={set("holidayMultiplier")} /></Field>
        <Field label="Night from"><input type="time" className="input" value={form.nightStart} onChange={set("nightStart")} /></Field>
        <Field label="Night until"><input type="time" className="input" value={form.nightEnd} onChange={set("nightEnd")} /></Field>
        <Field label="Night multiplier"><input className="input" inputMode="decimal" value={form.nightMultiplier} onChange={set("nightMultiplier")} /></Field>
        <label className="flex items-center gap-2 self-end pb-3 text-sm">
          <input type="checkbox" checked={form.requiresApproval} onChange={(e) => setForm((f) => ({ ...f, requiresApproval: e.target.checked }))} /> Overtime needs approval
        </label>
        <Field label="Note" className="sm:col-span-2"><input className="input" value={form.note} maxLength={300} onChange={set("note")} /></Field>
      </form>
    </Modal>
  );
}
