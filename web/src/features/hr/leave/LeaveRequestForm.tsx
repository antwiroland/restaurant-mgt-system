"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage } from "@/features/hr/ui";
import {
  getLeaveTypes,
  quoteLeaveFor,
  quoteMyLeave,
  requestLeaveFor,
  requestMyLeave,
  type LeaveQuote,
  type LeaveRequest,
  type LeaveType,
} from "@/lib/hrApi";
import { formatDays, plural } from "./leaveUtils";

/**
 * Leave request form with a live day count and balance check. Without `employee` it requests the signed-in employee's own
 * leave; with one, a manager records leave for that employee (HR may record it as already approved).
 */
export function LeaveRequestForm({ employee, onClose, onSaved }: {
  employee?: { id: string; name: string };
  onClose: () => void;
  onSaved: (request: LeaveRequest) => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [types, setTypes] = useState<LeaveType[]>([]);
  const [typeId, setTypeId] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [startHalf, setStartHalf] = useState(false);
  const [endHalf, setEndHalf] = useState(false);
  const [reason, setReason] = useState("");
  const [recordApproved, setRecordApproved] = useState(false);
  const [quote, setQuote] = useState<LeaveQuote | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authenticatedFetch((s) => getLeaveTypes(s))
      .then((list) => {
        const active = list.filter((t) => t.active);
        setTypes(active);
        setTypeId((c) => c || active[0]?.id || "");
      })
      .catch((err) => setError(errorMessage(err, "Could not load leave types")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const singleDay = startDate !== "" && startDate === endDate;
  const body = useMemo(() => ({
    leaveTypeId: typeId, startDate, endDate, startHalf, endHalf: singleDay ? false : endHalf, reason: reason.trim() || undefined,
  }), [typeId, startDate, endDate, startHalf, endHalf, singleDay, reason]);

  useEffect(() => {
    setQuote(null);
    if (!typeId || !startDate || !endDate || endDate < startDate) return;
    const timer = setTimeout(() => {
      authenticatedFetch((s) => (employee ? quoteLeaveFor(s, { ...body, employeeId: employee.id }) : quoteMyLeave(s, body)))
        .then(setQuote)
        .catch((err) => setQuote({ days: 0, deductsBalance: false, requiresDocument: false, problems: [errorMessage(err, "Could not check these dates")] }));
    }, 250);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [body.leaveTypeId, body.startDate, body.endDate, body.startHalf, body.endHalf, employee?.id]);

  const type = types.find((t) => t.id === typeId);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const saved = await authenticatedFetch((s) => (employee
        ? requestLeaveFor(s, { ...body, employeeId: employee.id, recordApproved })
        : requestMyLeave(s, body)));
      onSaved(saved);
    } catch (err) {
      setError(errorMessage(err, "Could not save the request"));
    } finally {
      setBusy(false);
    }
  }

  const blocked = !quote || quote.problems.length > 0;

  return (
    <Modal
      open
      title={employee ? `Record leave for ${employee.name}` : "Request leave"}
      onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="leave-request-form" className="btn btn-primary btn-md" disabled={busy || blocked}>
            {busy ? "Saving…" : recordApproved ? "Record approved leave" : "Send request"}
          </button>
        </div>
      )}
    >
      <form id="leave-request-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <Field label="Type of leave">
          <select className="select" value={typeId} onChange={(e) => setTypeId(e.target.value)} required>
            {types.map((t) => <option key={t.id} value={t.id}>{t.name}{t.paid ? "" : " (unpaid)"}</option>)}
          </select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="First day">
            <input type="date" className="input" value={startDate} required
              onChange={(e) => { setStartDate(e.target.value); if (!endDate || endDate < e.target.value) setEndDate(e.target.value); }} />
          </Field>
          <Field label="Last day">
            <input type="date" className="input" value={endDate} min={startDate} required onChange={(e) => setEndDate(e.target.value)} />
          </Field>
        </div>
        <div className="flex flex-wrap gap-4 text-sm">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={startHalf} onChange={(e) => setStartHalf(e.target.checked)} />
            {singleDay ? "Half day only" : "Half day on the first day"}
          </label>
          {!singleDay ? (
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={endHalf} onChange={(e) => setEndHalf(e.target.checked)} /> Half day on the last day
            </label>
          ) : null}
        </div>
        <Field label="Reason (optional)">
          <textarea className="input min-h-20" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
        </Field>
        {employee && can("hr.leave.manage") ? (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={recordApproved} onChange={(e) => setRecordApproved(e.target.checked)} />
            Record as already approved (for leave agreed outside the system)
          </label>
        ) : null}

        {quote ? (
          <div className="rounded-xl bg-sunken p-3 text-sm" role="status">
            <p className="font-semibold">
              {plural(quote.days)}
              {type?.countBasis === "WORKDAYS" ? " (working days; weekends and public holidays are not counted)" : " (calendar days)"}
            </p>
            {quote.deductsBalance && quote.availableDays != null ? (
              <p className="text-ink-soft">{formatDays(quote.availableDays)} days available on the first day; {formatDays(Number(quote.availableDays) - Number(quote.days))} left afterwards.</p>
            ) : null}
            {quote.requiresDocument ? <p className="text-ink-soft">A supporting document is needed before final approval; you can attach it after sending.</p> : null}
            {quote.problems.map((p) => <p key={p} className="mt-1 font-semibold text-danger">{p}</p>)}
          </div>
        ) : null}
      </form>
    </Modal>
  );
}
