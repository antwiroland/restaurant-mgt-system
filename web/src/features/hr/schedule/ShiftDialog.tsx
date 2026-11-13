"use client";

import { useState } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { useStaffSession } from "@/components/SessionProvider";
import { Field, errorMessage } from "@/features/hr/ui";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { formatMinutes } from "@/features/workforce/time";
import {
  ScheduleIssuesError,
  cancelShift,
  deleteDraftShift,
  publishOneShift,
  saveShift,
  type BoardEmployee,
  type ScheduleIssue,
  type Shift,
  type ShiftInput,
  type ShiftTemplate,
} from "@/lib/hrApi";
import { dayCode, hhmm, shiftDay, shiftInstants, trimTime } from "./scheduleUtils";

export function IssuesList({ issues }: { issues: ScheduleIssue[] }) {
  const blocking = issues.filter((i) => i.severity === "BLOCKING");
  const warnings = issues.filter((i) => i.severity === "WARNING");
  return (
    <div className="grid gap-2" role="alert">
      {blocking.length ? (
        <div className="alert alert-danger">
          <p className="font-semibold">This can&apos;t be saved:</p>
          <ul className="mt-1 list-disc pl-5 text-sm">{blocking.map((i, n) => <li key={n}>{i.message}</li>)}</ul>
        </div>
      ) : null}
      {warnings.length ? (
        <div className="alert alert-warning">
          <p className="font-semibold">Please check:</p>
          <ul className="mt-1 list-disc pl-5 text-sm">{warnings.map((i, n) => <li key={n}>{i.message}</li>)}</ul>
        </div>
      ) : null}
    </div>
  );
}

type Props = {
  branchId: string;
  employees: BoardEmployee[];
  templates: ShiftTemplate[];
  shift?: Shift | null;
  defaults?: { employeeId?: string | null; date?: string };
  canManage: boolean;
  onClose: () => void;
  onSaved: (message: string) => void;
};

/** Create or edit one shift. Warnings from the server must be confirmed; blocking problems are shown and nothing is saved. */
export function ShiftDialog({ branchId, employees, templates, shift, defaults, canManage, onClose, onSaved }: Props) {
  const { authenticatedFetch } = useStaffSession();
  const editing = !!shift;
  const initialDate = shift ? shiftDay(shift) : defaults?.date ?? "";
  const [employeeId, setEmployeeId] = useState<string>(shift ? shift.employeeId ?? "" : defaults?.employeeId ?? "");
  const [mode, setMode] = useState<"template" | "custom">(shift ? (shift.templateId ? "template" : "custom") : templates.length ? "template" : "custom");
  const [templateId, setTemplateId] = useState<string>(shift?.templateId ?? "");
  const [date, setDate] = useState(initialDate);
  const [start, setStart] = useState(shift ? hhmm(shift.startsAt) : "09:00");
  const [end, setEnd] = useState(shift ? hhmm(shift.endsAt) : "17:00");
  const [breakMinutes, setBreakMinutes] = useState<string>(shift ? String(shift.breakMinutes) : "30");
  const [notes, setNotes] = useState(shift?.notes ?? "");
  const [issues, setIssues] = useState<ScheduleIssue[]>([]);
  const [needsAck, setNeedsAck] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const readOnly = !canManage || shift?.status === "CANCELLED";
  const canPublish = useHrAccess().can("hr.schedule.publish");

  const dayTemplates = templates.filter((t) => t.active && (!date || t.days.includes(dayCode(new Date(`${date}T00:00:00`)))));
  const chosen = templates.find((t) => t.id === templateId);

  function buildInput(acknowledge: boolean): ShiftInput | null {
    if (!date) {
      setError("Choose a date.");
      return null;
    }
    const base: ShiftInput = {
      employeeId: employeeId || null,
      branchId,
      notes: notes.trim() || undefined,
      acknowledgeWarnings: acknowledge,
      version: shift?.version,
    };
    if (mode === "template") {
      if (!templateId) {
        setError("Choose a shift template.");
        return null;
      }
      return { ...base, templateId, date, breakMinutes: breakMinutes === "" ? null : Number(breakMinutes) };
    }
    if (!start || !end || start === end) {
      setError("Enter different start and end times.");
      return null;
    }
    const { startsAt, endsAt } = shiftInstants(date, start, end);
    return { ...base, templateId: null, startsAt, endsAt, breakMinutes: Number(breakMinutes || 0) };
  }

  async function submit(acknowledge: boolean) {
    setError("");
    const input = buildInput(acknowledge);
    if (!input) return;
    setBusy(true);
    try {
      const result = await authenticatedFetch((s) => saveShift(s, input, shift?.id));
      const warned = result.acknowledgedWarnings.length ? ` (${result.acknowledgedWarnings.length} warning${result.acknowledgedWarnings.length === 1 ? "" : "s"} confirmed)` : "";
      onSaved(`${editing ? "Shift updated" : "Shift added as draft"}${warned}.`);
    } catch (err) {
      if (err instanceof ScheduleIssuesError) {
        setIssues(err.issues);
        setNeedsAck(!err.blocking);
      } else {
        setError(errorMessage(err, "Could not save the shift"));
      }
    } finally {
      setBusy(false);
    }
  }

  async function doCancel() {
    if (!shift) return;
    if (shift.status === "PUBLISHED" && !cancelReason.trim()) {
      setError("Give a reason; the employee will be notified.");
      return;
    }
    setBusy(true);
    try {
      await authenticatedFetch((s) => cancelShift(s, shift.id, cancelReason.trim() || undefined));
      onSaved("Shift cancelled.");
    } catch (err) {
      setError(errorMessage(err, "Could not cancel the shift"));
    } finally {
      setBusy(false);
    }
  }

  async function doPublish() {
    if (!shift) return;
    setBusy(true);
    try {
      await authenticatedFetch((s) => publishOneShift(s, shift.id));
      onSaved("Shift published; the employee has been notified.");
    } catch (err) {
      if (err instanceof ScheduleIssuesError) setIssues(err.issues);
      else setError(errorMessage(err, "Could not publish the shift"));
    } finally {
      setBusy(false);
    }
  }

  async function doDelete() {
    if (!shift) return;
    setBusy(true);
    try {
      await authenticatedFetch((s) => deleteDraftShift(s, shift.id));
      onSaved("Draft shift deleted.");
    } catch (err) {
      setError(errorMessage(err, "Could not delete the shift"));
    } finally {
      setBusy(false);
    }
  }

  const footer = readOnly ? (
    <button type="button" className="btn btn-primary btn-md" onClick={onClose}>Close</button>
  ) : (
    <>
      {editing && !cancelling ? (
        shift?.status === "DRAFT" ? (
          <>
            <button type="button" className="btn btn-ghost btn-md mr-auto text-danger" onClick={doDelete} disabled={busy}><Icon name="trash" size={16} /> Delete draft</button>
            {canPublish ? <button type="button" className="btn btn-secondary btn-md" onClick={doPublish} disabled={busy}>Publish this shift</button> : null}
          </>
        ) : (
          <button type="button" className="btn btn-ghost btn-md mr-auto text-danger" onClick={() => setCancelling(true)} disabled={busy}>Cancel shift</button>
        )
      ) : null}
      <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Close</button>
      {cancelling ? (
        <button type="button" className="btn btn-danger-solid btn-md" onClick={doCancel} disabled={busy}>Confirm cancellation</button>
      ) : needsAck ? (
        <button type="button" className="btn btn-accent btn-md" onClick={() => submit(true)} disabled={busy}>{busy ? "Saving…" : "Save anyway"}</button>
      ) : (
        <button type="button" className="btn btn-primary btn-md" onClick={() => submit(false)} disabled={busy}>{busy ? "Saving…" : editing ? "Save changes" : "Add shift"}</button>
      )}
    </>
  );

  return (
    <Modal open title={editing ? (readOnly ? "Shift" : "Edit shift") : "Add shift"} onClose={onClose} footer={footer}>
      <div className="grid gap-3">
        {shift ? (
          <p className="text-sm text-ink-soft">
            Status: <span className="font-semibold text-ink">{shift.status.toLowerCase()}</span>
            {shift.status === "PUBLISHED" ? " — staff can see it; changes notify them." : shift.status === "DRAFT" ? " — not visible to staff until published." : ""}
            {shift.cancelReason ? ` Reason: ${shift.cancelReason}` : ""}
          </p>
        ) : null}
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {issues.length ? <IssuesList issues={issues} /> : null}
        {cancelling ? (
          <Field label={shift?.status === "PUBLISHED" ? "Reason (sent to the employee) *" : "Reason"}>
            <input className="input" value={cancelReason} maxLength={500} onChange={(e) => setCancelReason(e.target.value)} autoFocus />
          </Field>
        ) : (
          <fieldset className="grid gap-3 sm:grid-cols-2" disabled={readOnly}>
            <Field label="Employee" className="sm:col-span-2">
              <select className="select" value={employeeId} onChange={(e) => { setEmployeeId(e.target.value); setIssues([]); setNeedsAck(false); }}>
                <option value="">Open shift (unassigned)</option>
                {employees.map((e) => (
                  <option key={e.id} value={e.id}>{e.name}{e.positionTitle ? ` · ${e.positionTitle}` : ""}{e.homeBranch ? "" : " (covering)"}</option>
                ))}
              </select>
            </Field>
            <Field label="Date *"><input type="date" className="input" value={date} onChange={(e) => { setDate(e.target.value); setIssues([]); setNeedsAck(false); }} /></Field>
            <div className="field">
              <span className="field-label">Times from</span>
              <div className="segmented">
                <button type="button" aria-pressed={mode === "template"} onClick={() => setMode("template")} disabled={!templates.length}>Template</button>
                <button type="button" aria-pressed={mode === "custom"} onClick={() => setMode("custom")}>Custom</button>
              </div>
            </div>
            {mode === "template" ? (
              <Field label="Template *" className="sm:col-span-2" hint={chosen ? `${trimTime(chosen.startTime)}–${trimTime(chosen.endTime)}${chosen.overnight ? " (ends next day)" : ""}` : undefined}>
                <select className="select" value={templateId} onChange={(e) => { setTemplateId(e.target.value); const t = templates.find((x) => x.id === e.target.value); if (t) setBreakMinutes(String(t.breakMinutes)); setIssues([]); setNeedsAck(false); }}>
                  <option value="">Choose…</option>
                  {(dayTemplates.length ? dayTemplates : templates.filter((t) => t.active)).map((t) => (
                    <option key={t.id} value={t.id}>{t.name} · {trimTime(t.startTime)}–{trimTime(t.endTime)}</option>
                  ))}
                </select>
              </Field>
            ) : (
              <>
                <Field label="Start"><input type="time" className="input" value={start} onChange={(e) => { setStart(e.target.value); setIssues([]); setNeedsAck(false); }} /></Field>
                <Field label="End" hint={end && start && end <= start ? "Ends the next day" : undefined}>
                  <input type="time" className="input" value={end} onChange={(e) => { setEnd(e.target.value); setIssues([]); setNeedsAck(false); }} />
                </Field>
              </>
            )}
            <Field label="Unpaid break (minutes)" hint={breakMinutes ? formatMinutes(Number(breakMinutes)) : undefined}>
              <input className="input" inputMode="numeric" value={breakMinutes} onChange={(e) => setBreakMinutes(e.target.value.replace(/\D/g, ""))} />
            </Field>
            <Field label="Notes"><input className="input" value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} /></Field>
          </fieldset>
        )}
      </div>
    </Modal>
  );
}
