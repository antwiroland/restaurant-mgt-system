"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDateTime } from "@/features/hr/ui";
import { hhmm } from "@/features/hr/schedule/scheduleUtils";
import { formatMinutes, toLocalInput } from "@/features/workforce/time";
import {
  clearClockPin,
  decideCorrection,
  getAttendancePolicy,
  getClockPin,
  getCorrections,
  requestCorrection,
  saveAttendancePolicy,
  setClockPin,
  type AttendanceCorrection,
  type AttendanceEntry,
  type AttendancePolicy,
  type PinStatus,
} from "@/lib/hrApi";

export const statusTone: Record<string, string> = {
  ON_SHIFT: "badge-success",
  ON_BREAK: "badge-info",
  COMPLETED: "badge-neutral",
  LEFT_EARLY: "badge-warning",
  MISSED_CLOCK_OUT: "badge-danger",
  ABSENT: "badge-danger",
  NOT_STARTED: "badge-neutral",
  UNSCHEDULED: "badge-gold",
};

export function timeRange(from?: string | null, to?: string | null): string {
  if (!from) return "—";
  return `${hhmm(from)}–${to ? hhmm(to) : "…"}`;
}

// ------------------------------------------------------------------ correction request

export function CorrectionDialog({ entry, employee, branchId, onClose, onDone }: {
  entry?: AttendanceEntry | null;
  employee?: { id: string; name: string } | null;
  branchId: string;
  onClose: () => void;
  onDone: (message: string) => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const defaultIn = entry?.clockInAt ?? (employee ? new Date(Date.now() - 8 * 3600 * 1000).toISOString() : "");
  const [clockIn, setClockIn] = useState(toLocalInput(defaultIn));
  const [clockOut, setClockOut] = useState(toLocalInput(entry?.clockOutAt ?? (entry ? null : new Date().toISOString())));
  const [breakMinutes, setBreakMinutes] = useState(String(entry?.metrics.unpaidBreakMinutes ?? 0));
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!clockIn) return setError("Enter the clock-in time.");
    if (!entry && !clockOut) return setError("A missing record needs a clock-out time.");
    if (clockOut && new Date(clockOut) <= new Date(clockIn)) return setError("Clock-out must be after clock-in.");
    if (!reason.trim()) return setError("Give a reason; it is kept with the record.");
    setBusy(true);
    setError("");
    try {
      const result = await authenticatedFetch((s) => requestCorrection(s, {
        timeEntryId: entry?.id,
        employeeId: entry ? undefined : employee?.id,
        branchId: entry ? undefined : branchId,
        clockInAt: new Date(clockIn).toISOString(),
        clockOutAt: clockOut ? new Date(clockOut).toISOString() : null,
        breakMinutes: Number(breakMinutes || 0),
        reason: reason.trim(),
      }));
      onDone(result.status === "APPLIED" ? "Correction applied." : "Correction sent for approval. Someone else with correction rights must approve it.");
    } catch (err) {
      setError(errorMessage(err, "Could not submit the correction"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={entry ? `Correct ${entry.employeeName ?? "attendance"}` : `Add missing record — ${employee?.name ?? ""}`}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="correction-form" className="btn btn-primary btn-md" disabled={busy}>{busy ? "Sending…" : "Submit correction"}</button>
        </>
      )}
    >
      <form id="correction-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        {entry ? (
          <p className="text-sm text-ink-soft sm:col-span-2">
            Recorded: {formatDateTime(entry.clockInAt)} → {entry.clockOutAt ? formatDateTime(entry.clockOutAt) : "still clocked in"}. The original stays on record.
          </p>
        ) : null}
        <Field label="Clock in *"><input type="datetime-local" className="input" value={clockIn} onChange={(e) => setClockIn(e.target.value)} /></Field>
        <Field label={entry ? "Clock out" : "Clock out *"}><input type="datetime-local" className="input" value={clockOut} onChange={(e) => setClockOut(e.target.value)} /></Field>
        <Field label="Unpaid break (minutes)"><input className="input" inputMode="numeric" value={breakMinutes} onChange={(e) => setBreakMinutes(e.target.value.replace(/\D/g, ""))} /></Field>
        <Field label="Reason *"><input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} /></Field>
      </form>
    </Modal>
  );
}

// ------------------------------------------------------------------ approvals

export function CorrectionsPanel({ onChanged }: { onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [rows, setRows] = useState<AttendanceCorrection[] | null>(null);
  const [filter, setFilter] = useState<"PENDING" | "ALL">("PENDING");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getCorrections(s, filter === "PENDING" ? "PENDING" : undefined)));
    } catch (err) {
      setError(errorMessage(err, "Could not load corrections"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter]);

  useEffect(() => { void load(); }, [load]);

  async function decide(c: AttendanceCorrection, approve: boolean) {
    setNotice("");
    setError("");
    try {
      await authenticatedFetch((s) => decideCorrection(s, c.id, { approve }));
      setNotice(approve ? "Correction applied." : "Correction rejected.");
      await load();
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not record the decision"));
    }
  }

  return (
    <section className="grid gap-3">
      <SectionHeader
        title="Corrections"
        description="Every change keeps the original times. You can't approve your own request or a change to your own attendance."
        actions={(
          <div className="segmented">
            <button type="button" aria-pressed={filter === "PENDING"} onClick={() => setFilter("PENDING")}>Waiting</button>
            <button type="button" aria-pressed={filter === "ALL"} onClick={() => setFilter("ALL")}>All recent</button>
          </div>
        )}
      />
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}
      {!rows ? <Skeleton className="h-32 w-full" /> : rows.length === 0 ? <EmptyState compact icon="checkCircle" title="Nothing waiting" /> : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Employee</th><th>Recorded</th><th>Corrected</th><th>Reason</th><th>Status</th><th className="num" /></tr></thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id}>
                  <td className="font-semibold">{c.employeeName}</td>
                  <td className="text-xs">{c.originalClockIn ? `${formatDateTime(c.originalClockIn)} → ${c.originalClockOut ? hhmm(c.originalClockOut) : "open"}` : "No record"}</td>
                  <td className="text-xs">{formatDateTime(c.correctedClockIn)} → {c.correctedClockOut ? hhmm(c.correctedClockOut) : "open"}{c.correctedBreakMinutes ? ` · ${c.correctedBreakMinutes} min break` : ""}</td>
                  <td className="max-w-xs text-sm">{c.reason}{c.decisionNote ? <p className="text-xs text-ink-muted">{c.decisionNote}</p> : null}</td>
                  <td><span className={`badge ${c.status === "APPLIED" ? "badge-success" : c.status === "PENDING" ? "badge-pending" : "badge-neutral"}`}>{humanize(c.status)}</span></td>
                  <td className="num">
                    {c.status === "PENDING" && can("hr.attendance.correct") ? (
                      <div className="flex justify-end gap-1">
                        <button type="button" className="btn btn-success btn-sm" onClick={() => decide(c, true)}>Approve</button>
                        <button type="button" className="btn btn-ghost btn-sm" onClick={() => decide(c, false)}>Reject</button>
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ------------------------------------------------------------------ policy

export function AttendanceRulesPanel({ branchId, branchName }: { branchId: string; branchName: string }) {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [policy, setPolicy] = useState<AttendancePolicy | null>(null);
  const [form, setForm] = useState<Omit<AttendancePolicy, "source"> | null>(null);
  const [scope, setScope] = useState<"branch" | "org">("branch");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const canEdit = can("hr.settings.manage");

  const load = useCallback(async () => {
    try {
      const p = await authenticatedFetch((s) => getAttendancePolicy(s, branchId));
      setPolicy(p);
      const { source: _source, ...rest } = p;
      void _source;
      setForm(rest);
    } catch (err) {
      setError(errorMessage(err, "Could not load the rules"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  useEffect(() => { void load(); }, [load]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!form) return;
    setError("");
    try {
      await authenticatedFetch((s) => saveAttendancePolicy(s, { ...form, branchId: scope === "org" ? null : branchId }));
      setNotice("Rules saved.");
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not save the rules"));
    }
  }

  if (!form || !policy) return error ? <ErrorState message={error} onRetry={load} /> : <Skeleton className="h-40 w-full" />;
  const num = (key: keyof typeof form) => (
    <input className="input" inputMode="numeric" value={String(form[key])} disabled={!canEdit}
      onChange={(e) => setForm((f) => (f ? { ...f, [key]: Number(e.target.value.replace(/\D/g, "") || 0) } : f))} />
  );
  const toggle = (key: keyof typeof form, label: string) => (
    <label className="flex items-center gap-2 text-sm">
      <input type="checkbox" checked={Boolean(form[key])} disabled={!canEdit} onChange={(e) => setForm((f) => (f ? { ...f, [key]: e.target.checked } : f))} /> {label}
    </label>
  );
  const sourceLabel: Record<string, string> = { BRANCH: `${branchName} rules`, ORGANISATION: "organisation default", CONFIGURATION: "system default (not configured yet)" };
  return (
    <section className="grid gap-3">
      <SectionHeader title="Attendance rules" description={`In use: ${sourceLabel[policy.source] ?? policy.source}`} />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      <form onSubmit={save} className="grid gap-3 sm:grid-cols-3">
        <Field label="Late after (minutes grace)">{num("lateGraceMinutes")}</Field>
        <Field label="Early leave after (minutes grace)">{num("earlyLeaveGraceMinutes")}</Field>
        <Field label="Can clock in before shift (minutes)">{num("earlyClockInMinutes")}</Field>
        <Field label="Missed clock-out after shift end (minutes)">{num("missedClockOutMinutes")}</Field>
        <Field label="Clock-in without a shift">
          <select className="select" value={form.unscheduledClockIn} disabled={!canEdit} onChange={(e) => setForm((f) => (f ? { ...f, unscheduledClockIn: e.target.value as "ALLOW" | "BLOCK" } : f))}>
            <option value="ALLOW">Allow and flag for review</option>
            <option value="BLOCK">Refuse</option>
          </select>
        </Field>
        <div className="grid content-start gap-2 pt-6">
          {toggle("requireCorrectionApproval", "Corrections need a second person's approval")}
          {toggle("mealBreakPaid", "Meal breaks are paid")}
          {toggle("restBreakPaid", "Short breaks are paid")}
          {toggle("allowSelfClock", "Staff may clock in from their own account")}
        </div>
        {canEdit ? (
          <div className="flex flex-wrap items-center gap-2 sm:col-span-3">
            {access?.allBranches ? (
              <select className="select w-auto" value={scope} onChange={(e) => setScope(e.target.value as "branch" | "org")} aria-label="Apply to">
                <option value="branch">This branch only</option>
                <option value="org">Organisation default</option>
              </select>
            ) : null}
            <button type="submit" className="btn btn-primary btn-md">Save rules</button>
          </div>
        ) : null}
      </form>
    </section>
  );
}

// ------------------------------------------------------------------ clock PIN (employee profile)

export function ClockPinCard({ employeeId }: { employeeId: string }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [status, setStatus] = useState<PinStatus | null>(null);
  const [pin, setPin] = useState("");
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const canManage = can("hr.attendance.manage");

  useEffect(() => {
    authenticatedFetch((s) => getClockPin(s, employeeId)).then(setStatus).catch(() => setStatus(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId]);

  async function save(event: FormEvent) {
    event.preventDefault();
    setError("");
    try {
      setStatus(await authenticatedFetch((s) => setClockPin(s, employeeId, pin)));
      setPin("");
      setEditing(false);
      setNotice("Clock PIN saved. Tell the employee in person; it is not shown again.");
    } catch (err) {
      setError(errorMessage(err, "Could not save the PIN"));
    }
  }

  async function clear() {
    setError("");
    try {
      setStatus(await authenticatedFetch((s) => clearClockPin(s, employeeId)));
      setNotice("Clock PIN removed.");
    } catch (err) {
      setError(errorMessage(err, "Could not remove the PIN"));
    }
  }

  if (!status) return null;
  return (
    <section className="grid gap-2">
      <SectionHeader
        title="POS clock PIN"
        description="Used only to clock in and out at the POS. It gives no access to the till."
        actions={canManage && !editing ? (
          <>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => { setEditing(true); setNotice(""); }}>{status.set ? "Reset PIN" : "Set PIN"}</button>
            {status.set ? <button type="button" className="btn btn-ghost btn-sm" onClick={clear}>Remove</button> : null}
          </>
        ) : null}
      />
      <p className="text-sm">
        {status.set ? `Set ${status.setAt ? formatDateTime(status.setAt) : ""}` : "Not set — this person can't use the POS staff clock yet."}
        {status.lockedUntil ? <span className="ml-2 badge badge-danger">Locked until {hhmm(status.lockedUntil)}</span> : null}
      </p>
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {editing ? (
        <form onSubmit={save} className="flex flex-wrap items-end gap-2">
          <Field label="New PIN (4–6 digits)">
            <input className="input w-36 tabular" type="password" inputMode="numeric" autoComplete="new-password" value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))} />
          </Field>
          <button type="submit" className="btn btn-primary btn-md" disabled={pin.length < 4}>Save</button>
          <button type="button" className="btn btn-ghost btn-md" onClick={() => { setEditing(false); setPin(""); }}>Cancel</button>
        </form>
      ) : null}
    </section>
  );
}

export function metricsLine(entry: AttendanceEntry): string {
  const parts = [`${formatMinutes(entry.metrics.paidMinutes)} paid`];
  if (entry.metrics.lateMinutes) parts.push(`${entry.metrics.lateMinutes} min late`);
  if (entry.metrics.earlyLeaveMinutes) parts.push(`left ${entry.metrics.earlyLeaveMinutes} min early`);
  if (entry.metrics.overtimeMinutes) parts.push(`${formatMinutes(entry.metrics.overtimeMinutes)} over`);
  return parts.join(" · ");
}
