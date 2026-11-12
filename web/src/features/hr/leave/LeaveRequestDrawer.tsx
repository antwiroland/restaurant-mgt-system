"use client";

import { useCallback, useEffect, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Detail, Field, errorMessage, formatDateTime } from "@/features/hr/ui";
import { hhmm } from "@/features/hr/schedule/scheduleUtils";
import {
  attachLeaveDocument,
  cancelLeaveFor,
  cancelMyLeave,
  decideLeave,
  getLeaveRequest,
  type LeaveRequest,
} from "@/lib/hrApi";
import { leaveDates, leaveTone, plural, stepLabel } from "./leaveUtils";

/**
 * One leave request: approval chain, clashing shifts, and the actions open to the signed-in user. With `own`, it acts
 * through the self-service endpoints (cancel own leave, attach own sick note).
 */
export function LeaveRequestDrawer({ id, own = false, initial, onClose, onChanged }: {
  id: string;
  own?: boolean;
  initial?: LeaveRequest;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [request, setRequest] = useState<LeaveRequest | null>(initial ?? null);
  const [note, setNote] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (own) return; // self-service lists already hold the full request
    try {
      setRequest(await authenticatedFetch((s) => getLeaveRequest(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the request"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, own]);

  useEffect(() => { void load(); }, [load]);

  async function run(label: string, action: () => Promise<LeaveRequest>, done: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      setRequest(await action());
      setNote("");
      setFile(null);
      setNotice(done);
      onChanged();
    } catch (err) {
      setError(errorMessage(err, `Could not ${label}`));
    } finally {
      setBusy(false);
    }
  }

  const r = request;
  const manager = !own && can("hr.leave.approve", "hr.leave.manage");
  const active = r && (r.status === "PENDING" || r.status === "APPROVED");
  const today = new Date().toISOString().slice(0, 10);
  const selfCancellable = own && r && (r.status === "PENDING" || (r.status === "APPROVED" && r.startDate > today));

  const footer = r ? (
    <div className="flex w-full flex-wrap justify-end gap-2">
      {r.canDecide ? (
        <>
          <button type="button" className="btn btn-ghost btn-md" disabled={busy || !note.trim()} title="Give a reason in the note field"
            onClick={() => run("decline the request", () => authenticatedFetch((s) => decideLeave(s, r.id, { approve: false, note })), "Request declined.")}>
            Decline
          </button>
          <button type="button" className="btn btn-success btn-md" disabled={busy}
            onClick={() => run("approve the request", () => authenticatedFetch((s) => decideLeave(s, r.id, { approve: true, note: note || undefined })),
              r.steps.length - r.steps.filter((x) => x.decision).length > 1 ? "Approved; sent to the next approver." : "Leave approved.")}>
            Approve
          </button>
        </>
      ) : null}
      {selfCancellable ? (
        <button type="button" className="btn btn-ghost btn-md" disabled={busy}
          onClick={() => run("cancel the request", () => authenticatedFetch((s) => cancelMyLeave(s, r.id, note || undefined)), "Request cancelled.")}>
          Cancel request
        </button>
      ) : null}
      {manager && active && !r.canDecide ? (
        <button type="button" className="btn btn-ghost btn-md" disabled={busy || !note.trim()} title="Give a reason in the note field"
          onClick={() => run("cancel the leave", () => authenticatedFetch((s) => cancelLeaveFor(s, r.id, note)), "Leave cancelled; the days were returned to the balance.")}>
          Cancel leave
        </button>
      ) : null}
    </div>
  ) : null;

  return (
    <Drawer open title={r ? `${r.employeeName ?? "Leave"} · ${r.leaveTypeName ?? "Leave"}` : "Leave request"} onClose={onClose} footer={footer}>
      {!r ? (error ? <p className="alert alert-danger">{error}</p> : <Skeleton className="h-64 w-full" />) : (
        <div className="grid gap-5">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {notice ? <p className="alert alert-info" role="status">{notice}</p> : null}
          <div className="flex flex-wrap items-center gap-2">
            <span className={`badge badge-dot ${leaveTone[r.status]}`}>{humanize(r.status)}</span>
            {!r.paid ? <span className="badge badge-neutral">Unpaid</span> : null}
            <span className="text-xs text-ink-muted">Requested {formatDateTime(r.createdAt)}</span>
          </div>
          <dl className="grid grid-cols-2 gap-4">
            <Detail label="Dates" value={leaveDates(r)} />
            <Detail label="Counts as" value={plural(r.days)} />
            <Detail label="Reason" value={r.reason || "—"} />
            <Detail label="Supporting document" value={r.documentId ? "Attached" : r.documentRequired ? "Required — not attached yet" : "Not needed"} />
          </dl>
          {r.cancelReason ? <p className="rounded-xl bg-sunken p-3 text-sm text-ink-soft">Cancelled: {r.cancelReason}</p> : null}

          <section className="grid gap-2">
            <h3 className="text-sm font-semibold">Approval</h3>
            <ol className="grid gap-2">
              {r.steps.map((step) => (
                <li key={step.step} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-line p-3 text-sm">
                  <span className="font-semibold">{step.step + 1}. {stepLabel[step.code]}</span>
                  {step.decision ? (
                    <span className="text-ink-soft">
                      <span className={`badge ${step.decision === "APPROVED" ? "badge-success" : "badge-danger"} mr-2`}>{humanize(step.decision)}</span>
                      {step.decidedByName} · {formatDateTime(step.decidedAt)}{step.note ? ` — “${step.note}”` : ""}
                    </span>
                  ) : r.status === "PENDING" && r.pendingStep === step.code ? (
                    <span className="badge badge-pending">Waiting</span>
                  ) : <span className="text-ink-muted">—</span>}
                </li>
              ))}
            </ol>
          </section>

          {r.conflictingShifts && r.conflictingShifts.length > 0 ? (
            <section className="alert alert-warning grid gap-1" role="status">
              <p className="font-semibold">{r.conflictingShifts.length} shift{r.conflictingShifts.length === 1 ? "" : "s"} during this leave</p>
              <ul className="list-disc pl-5 text-sm">
                {r.conflictingShifts.map((s) => <li key={s.scheduleId}>{new Date(s.startsAt).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })} {hhmm(s.startsAt)}–{hhmm(s.endsAt)} ({humanize(s.status)})</li>)}
              </ul>
              <p className="text-xs">Reassign or cancel these on the shift schedule.</p>
            </section>
          ) : null}

          {active && (own || manager) && (r.documentRequired || r.documentId) ? (
            <Field label={r.documentId ? "Replace supporting document" : "Attach supporting document"} hint="PDF, PNG, JPEG or DOCX, up to 5 MB. Stored privately on the employee record.">
              <div className="flex flex-wrap items-center gap-2">
                <input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
                <button type="button" className="btn btn-ghost btn-sm" disabled={!file || busy}
                  onClick={() => file && run("upload the document", () => authenticatedFetch((s) => attachLeaveDocument(s, r.id, file, own)), "Document attached.")}>
                  Upload
                </button>
              </div>
            </Field>
          ) : null}

          {r.canDecide || (manager && active) || selfCancellable ? (
            <Field label={r.canDecide ? "Note (required to decline)" : own ? "Reason for cancelling (optional)" : "Reason (required to cancel)"}>
              <input className="input" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />
            </Field>
          ) : null}
        </div>
      )}
    </Drawer>
  );
}
