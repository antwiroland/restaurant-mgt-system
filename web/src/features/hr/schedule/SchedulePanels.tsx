"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDate } from "@/features/hr/ui";
import { addDays, formatMinutes, isoDate, startOfWeek } from "@/features/workforce/time";
import {
  SHIFT_TYPES,
  ScheduleIssuesError,
  decideSwap,
  getSchedulingPolicy,
  getShiftTemplates,
  getSwapRequests,
  saveSchedulingPolicy,
  saveShiftTemplate,
  setShiftTemplateActive,
  type ScheduleBoard,
  type ScheduleIssue,
  type SchedulingPolicy,
  type ShiftTemplate,
  type ShiftType,
  type SwapRequest,
  type WeekDay,
} from "@/lib/hrApi";
import { IssuesList } from "./ShiftDialog";
import { DAY_CODES, shiftDay, shiftLabel, trimTime } from "./scheduleUtils";

// ------------------------------------------------------------------ month overview

export function MonthView({ board, month, onPickWeek }: { board: ScheduleBoard; month: Date; onPickWeek: (weekStart: Date) => void }) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const gridStart = startOfWeek(first);
  const cells = Array.from({ length: 42 }, (_, i) => addDays(gridStart, i));
  const byDay = new Map<string, { published: number; drafts: number; open: number }>();
  board.shifts.filter((s) => s.status !== "CANCELLED").forEach((s) => {
    const key = shiftDay(s);
    const entry = byDay.get(key) ?? { published: 0, drafts: 0, open: 0 };
    if (s.status === "DRAFT") entry.drafts += 1; else entry.published += 1;
    if (!s.employeeId) entry.open += 1;
    byDay.set(key, entry);
  });
  const understaffed = new Set(board.coverage.filter((c) => c.state === "UNDERSTAFFED").map((c) => c.date));
  return (
    <div className="grid grid-cols-7 gap-1 text-sm">
      {DAY_CODES.map((d) => <p key={d} className="px-2 text-[11px] font-bold uppercase tracking-[0.1em] text-ink-soft">{d}</p>)}
      {cells.map((d) => {
        const key = isoDate(d);
        const info = byDay.get(key);
        const inMonth = d.getMonth() === month.getMonth();
        return (
          <button
            key={key}
            type="button"
            onClick={() => onPickWeek(startOfWeek(d))}
            className={`min-h-20 rounded-lg border p-2 text-left ${inMonth ? "border-line bg-surface hover:border-brand" : "border-transparent bg-sunken/50 text-ink-muted"}`}
          >
            <p className="text-xs font-bold">{d.getDate()}</p>
            {info ? (
              <div className="mt-1 grid gap-0.5 text-[11px]">
                {info.published ? <span className="text-ink-soft">{info.published} published</span> : null}
                {info.drafts ? <span className="text-accent-hover">{info.drafts} draft</span> : null}
                {info.open ? <span className="text-ink-soft">{info.open} open</span> : null}
              </div>
            ) : null}
            {understaffed.has(key) ? <span className="mt-1 inline-block badge badge-danger text-[10px]">Understaffed</span> : null}
          </button>
        );
      })}
    </div>
  );
}

// ------------------------------------------------------------------ day list

export function DayList({ board, date, onOpenShift }: { board: ScheduleBoard; date: string; onOpenShift: (id: string) => void }) {
  const shifts = board.shifts.filter((s) => shiftDay(s) === date).sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  if (!shifts.length) return <EmptyState compact icon="calendar" title="No shifts on this day" />;
  return (
    <div className="overflow-x-auto">
      <table className="data-table">
        <thead><tr><th>Time</th><th>Employee</th><th>Shift</th><th>Paid</th><th>Status</th></tr></thead>
        <tbody>
          {shifts.map((s) => (
            <tr key={s.id} className="cursor-pointer" onClick={() => onOpenShift(s.id)}>
              <td className="tabular font-semibold">{shiftLabel(s)}</td>
              <td>{s.employeeName ?? <span className="text-ink-muted">Open shift</span>}</td>
              <td>{s.templateName ?? s.positionTitle ?? "—"}</td>
              <td className="tabular">{formatMinutes(s.paidMinutes)}</td>
              <td><span className={`badge ${s.status === "PUBLISHED" ? "badge-success" : s.status === "DRAFT" ? "badge-gold" : "badge-neutral"}`}>{humanize(s.status)}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ------------------------------------------------------------------ swap approvals

export function SwapApprovals({ onChanged }: { onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [rows, setRows] = useState<SwapRequest[] | null>(null);
  const [error, setError] = useState("");
  const [deciding, setDeciding] = useState<SwapRequest | null>(null);

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getSwapRequests(s, "PENDING")));
    } catch (err) {
      setError(errorMessage(err, "Could not load swap requests"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!rows) return <Skeleton className="h-32 w-full" />;
  return (
    <section className="grid gap-3">
      <SectionHeader title="Swap requests" description="Approving reassigns the shifts and notifies both people. You can't decide a swap you are part of." />
      {rows.length === 0 ? <EmptyState compact icon="route" title="No swap requests waiting" /> : (
        <ul className="grid gap-2">
          {rows.map((r) => (
            <li key={r.id} className="card flex flex-wrap items-center justify-between gap-3 p-3">
              <div className="text-sm">
                <p className="font-semibold">{r.requesterName} → {r.colleagueName}</p>
                <p className="text-ink-soft">
                  {r.startsAt ? `${formatDate(r.startsAt)} ${shiftLabel({ startsAt: r.startsAt, endsAt: r.endsAt! })}` : "Shift"}
                  {r.colleagueStartsAt ? ` in exchange for ${formatDate(r.colleagueStartsAt)} ${shiftLabel({ startsAt: r.colleagueStartsAt, endsAt: r.colleagueEndsAt! })}` : " (handover)"}
                </p>
                <p className="text-ink-muted">“{r.reason}”</p>
              </div>
              <button type="button" className="btn btn-secondary btn-sm" onClick={() => setDeciding(r)}>Review</button>
            </li>
          ))}
        </ul>
      )}
      {deciding ? <SwapDecision swap={deciding} onClose={() => setDeciding(null)} onDone={() => { setDeciding(null); void load(); onChanged(); }} /> : null}
    </section>
  );
}

function SwapDecision({ swap, onClose, onDone }: { swap: SwapRequest; onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [note, setNote] = useState("");
  const [issues, setIssues] = useState<ScheduleIssue[]>([]);
  const [canForce, setCanForce] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function decide(approve: boolean, acknowledge = false) {
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => decideSwap(s, swap.id, { approve, note: note.trim() || undefined, acknowledgeWarnings: acknowledge }));
      onDone();
    } catch (err) {
      if (err instanceof ScheduleIssuesError) {
        setIssues(err.issues);
        setCanForce(!err.blocking);
      } else {
        setError(errorMessage(err, "Could not record the decision"));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title="Review swap request"
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-danger btn-md mr-auto" onClick={() => decide(false)} disabled={busy}>Decline</button>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Close</button>
          {canForce ? (
            <button type="button" className="btn btn-accent btn-md" onClick={() => decide(true, true)} disabled={busy}>Approve anyway</button>
          ) : (
            <button type="button" className="btn btn-success btn-md" onClick={() => decide(true)} disabled={busy}>Approve</button>
          )}
        </>
      )}
    >
      <div className="grid gap-3 text-sm">
        <p><span className="font-semibold">{swap.requesterName}</span> wants <span className="font-semibold">{swap.colleagueName}</span> to take their shift. Reason: “{swap.reason}”</p>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {issues.length ? <IssuesList issues={issues} /> : null}
        <Field label="Note to staff (optional)"><input className="input" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} /></Field>
      </div>
    </Modal>
  );
}

// ------------------------------------------------------------------ templates

export function TemplatesPanel({ branchId, onChanged }: { branchId: string; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [rows, setRows] = useState<ShiftTemplate[] | null>(null);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<ShiftTemplate | "new" | null>(null);
  const canManage = can("hr.schedule.manage");

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getShiftTemplates(s, branchId, true)));
    } catch (err) {
      setError(errorMessage(err, "Could not load templates"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  useEffect(() => { void load(); }, [load]);

  async function toggle(t: ShiftTemplate) {
    try {
      await authenticatedFetch((s) => setShiftTemplateActive(s, t.id, !t.active));
      await load();
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not update the template"));
    }
  }

  if (!rows && !error) return <Skeleton className="h-32 w-full" />;
  return (
    <section className="grid gap-3">
      <SectionHeader
        title="Shift templates"
        description="Reusable shifts with their own times, days and staffing levels. Coverage on the board is measured against these."
        actions={canManage ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditing("new")}><Icon name="plus" size={16} /> Template</button> : null}
      />
      {error ? <ErrorState message={error} onRetry={load} /> : null}
      {rows && rows.length === 0 ? <EmptyState compact icon="clock" title="No templates yet" description="Shifts can still be added with custom times." /> : null}
      {rows && rows.length ? (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Template</th><th>Times</th><th>Days</th><th className="num">Staff</th><th>Scope</th><th className="num" /></tr></thead>
            <tbody>
              {rows.map((t) => (
                <tr key={t.id} className={t.active ? "" : "opacity-60"}>
                  <td><span className="font-semibold">{t.name}</span><p className="text-xs text-ink-muted">{humanize(t.shiftType)}{t.active ? "" : " · archived"}</p></td>
                  <td className="tabular">{trimTime(t.startTime)}–{trimTime(t.endTime)}{t.overnight ? " (+1)" : ""}<p className="text-xs text-ink-muted">{t.breakMinutes} min break</p></td>
                  <td className="text-xs">{t.days.length === 7 ? "Every day" : t.days.join(", ")}</td>
                  <td className="num">{t.minStaff}{t.maxStaff != null ? `–${t.maxStaff}` : "+"}</td>
                  <td>{t.branchId ? "This branch" : "All branches"}</td>
                  <td className="num">
                    {canManage && (t.branchId || access?.allBranches) ? (
                      <div className="flex justify-end gap-1">
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditing(t)}>Edit</button>
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => toggle(t)}>{t.active ? "Archive" : "Restore"}</button>
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {editing ? (
        <TemplateDialog
          template={editing === "new" ? null : editing}
          branchId={branchId}
          central={!!access?.allBranches}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); void load(); onChanged(); }}
        />
      ) : null}
    </section>
  );
}

function TemplateDialog({ template, branchId, central, onClose, onSaved }: {
  template: ShiftTemplate | null; branchId: string; central: boolean; onClose: () => void; onSaved: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState({
    name: template?.name ?? "",
    shiftType: (template?.shiftType ?? "MORNING") as ShiftType,
    startTime: trimTime(template?.startTime) || "08:00",
    endTime: trimTime(template?.endTime) || "16:00",
    breakMinutes: String(template?.breakMinutes ?? 30),
    days: new Set<WeekDay>(template?.days ?? DAY_CODES),
    minStaff: String(template?.minStaff ?? 1),
    maxStaff: template?.maxStaff != null ? String(template.maxStaff) : "",
    allBranches: template ? !template.branchId : false,
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.name.trim()) return setError("Give the template a name.");
    if (form.startTime === form.endTime) return setError("Start and end time cannot be the same.");
    if (!form.days.size) return setError("Choose at least one day.");
    if (form.maxStaff && Number(form.maxStaff) < Number(form.minStaff)) return setError("Maximum staff cannot be below the minimum.");
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => saveShiftTemplate(s, {
        name: form.name.trim(), shiftType: form.shiftType, branchId: form.allBranches ? null : branchId,
        startTime: form.startTime, endTime: form.endTime, breakMinutes: Number(form.breakMinutes || 0),
        days: DAY_CODES.filter((d) => form.days.has(d)), minStaff: Number(form.minStaff || 0), maxStaff: form.maxStaff ? Number(form.maxStaff) : null,
      }, template?.id));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the template"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={template ? `Edit ${template.name}` : "New shift template"}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="template-form" className="btn btn-primary btn-md" disabled={busy}>{busy ? "Saving…" : "Save"}</button>
        </>
      )}
    >
      <form id="template-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="Name *"><input className="input" value={form.name} maxLength={80} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} /></Field>
        <Field label="Type">
          <select className="select" value={form.shiftType} onChange={(e) => setForm((f) => ({ ...f, shiftType: e.target.value as ShiftType }))}>
            {SHIFT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </Field>
        <Field label="Start"><input type="time" className="input" value={form.startTime} onChange={(e) => setForm((f) => ({ ...f, startTime: e.target.value }))} /></Field>
        <Field label="End" hint={form.endTime <= form.startTime ? "Ends the next day" : undefined}><input type="time" className="input" value={form.endTime} onChange={(e) => setForm((f) => ({ ...f, endTime: e.target.value }))} /></Field>
        <Field label="Unpaid break (minutes)"><input className="input" inputMode="numeric" value={form.breakMinutes} onChange={(e) => setForm((f) => ({ ...f, breakMinutes: e.target.value.replace(/\D/g, "") }))} /></Field>
        <div className="grid grid-cols-2 gap-2">
          <Field label="Min staff"><input className="input" inputMode="numeric" value={form.minStaff} onChange={(e) => setForm((f) => ({ ...f, minStaff: e.target.value.replace(/\D/g, "") }))} /></Field>
          <Field label="Max staff"><input className="input" inputMode="numeric" value={form.maxStaff} onChange={(e) => setForm((f) => ({ ...f, maxStaff: e.target.value.replace(/\D/g, "") }))} /></Field>
        </div>
        <fieldset className="sm:col-span-2">
          <legend className="field-label">Days</legend>
          <div className="mt-1 flex flex-wrap gap-1">
            {DAY_CODES.map((d) => (
              <button
                key={d}
                type="button"
                aria-pressed={form.days.has(d)}
                className={`chip ${form.days.has(d) ? "bg-brand text-white" : ""}`}
                onClick={() => setForm((f) => { const days = new Set(f.days); if (days.has(d)) days.delete(d); else days.add(d); return { ...f, days }; })}
              >
                {d}
              </button>
            ))}
          </div>
        </fieldset>
        {central ? (
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <input type="checkbox" checked={form.allBranches} onChange={(e) => setForm((f) => ({ ...f, allBranches: e.target.checked }))} /> Use at every branch
          </label>
        ) : null}
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ policy

export function PolicyPanel({ branchId, branchName }: { branchId: string; branchName: string }) {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [policy, setPolicy] = useState<SchedulingPolicy | null>(null);
  const [form, setForm] = useState({ rest: "", shift: "", weekly: "", scope: "branch" as "branch" | "org" });
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const canEdit = can("hr.settings.manage");

  const load = useCallback(async () => {
    try {
      const p = await authenticatedFetch((s) => getSchedulingPolicy(s, branchId));
      setPolicy(p);
      setForm((f) => ({ ...f, rest: String(p.minRestMinutes / 60), shift: String(p.maxShiftMinutes / 60), weekly: String(p.maxWeeklyMinutes / 60) }));
    } catch (err) {
      setError(errorMessage(err, "Could not load the policy"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  useEffect(() => { void load(); }, [load]);

  async function save(event: FormEvent) {
    event.preventDefault();
    const toMinutes = (v: string) => Math.round(Number(v) * 60);
    if ([form.rest, form.shift, form.weekly].some((v) => v === "" || Number.isNaN(Number(v)))) return setError("Enter hours as numbers.");
    setError("");
    try {
      await authenticatedFetch((s) => saveSchedulingPolicy(s, {
        branchId: form.scope === "org" ? null : branchId, minRestMinutes: toMinutes(form.rest), maxShiftMinutes: toMinutes(form.shift), maxWeeklyMinutes: toMinutes(form.weekly),
      }));
      setNotice("Policy saved. New and moved shifts are checked against it.");
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not save the policy"));
    }
  }

  if (!policy && !error) return <Skeleton className="h-32 w-full" />;
  const sourceLabel: Record<string, string> = { BRANCH: `${branchName} policy`, ORGANISATION: "organisation default", CONFIGURATION: "system default (not configured yet)" };
  return (
    <section className="grid gap-3">
      <SectionHeader title="Scheduling limits" description="These limits produce warnings on the board; schedulers can confirm and proceed. Set them to the labour rules that apply to you." />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {policy ? (
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div className="stat"><dt className="stat-label">Minimum rest between shifts</dt><dd className="stat-value">{formatMinutes(policy.minRestMinutes)}</dd></div>
          <div className="stat"><dt className="stat-label">Longest shift</dt><dd className="stat-value">{formatMinutes(policy.maxShiftMinutes)}</dd></div>
          <div className="stat"><dt className="stat-label">Weekly paid hours</dt><dd className="stat-value">{formatMinutes(policy.maxWeeklyMinutes)}</dd></div>
        </dl>
      ) : null}
      {policy ? <p className="text-sm text-ink-soft">Source: {sourceLabel[policy.source] ?? policy.source}</p> : null}
      {canEdit ? (
        <form onSubmit={save} className="grid gap-3 sm:grid-cols-4 sm:items-end">
          <Field label="Min rest (hours)"><input className="input" inputMode="decimal" value={form.rest} onChange={(e) => setForm((f) => ({ ...f, rest: e.target.value }))} /></Field>
          <Field label="Max shift (hours)"><input className="input" inputMode="decimal" value={form.shift} onChange={(e) => setForm((f) => ({ ...f, shift: e.target.value }))} /></Field>
          <Field label="Max per week (hours)"><input className="input" inputMode="decimal" value={form.weekly} onChange={(e) => setForm((f) => ({ ...f, weekly: e.target.value }))} /></Field>
          <div className="grid gap-2">
            {access?.allBranches ? (
              <select className="select" value={form.scope} onChange={(e) => setForm((f) => ({ ...f, scope: e.target.value as "branch" | "org" }))} aria-label="Apply to">
                <option value="branch">This branch only</option>
                <option value="org">Organisation default</option>
              </select>
            ) : null}
            <button type="submit" className="btn btn-primary btn-md">Save limits</button>
          </div>
        </form>
      ) : null}
    </section>
  );
}

export function weekRangeLabel(start: Date, days: number): string {
  const end = addDays(start, days - 1);
  return `${start.toLocaleDateString(undefined, { day: "numeric", month: "short" })} – ${end.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}`;
}
