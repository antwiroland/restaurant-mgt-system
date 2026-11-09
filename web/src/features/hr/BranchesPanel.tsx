"use client";

import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, errorMessage, formatDate, todayIso } from "@/features/hr/ui";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import {
  assignBranch,
  endAssignment,
  getAssignments,
  getPositions,
  transferEmployee,
  type AssignmentType,
  type BranchAssignment,
  type EmployeeDetail,
  type Position,
} from "@/lib/hrApi";

const typeTone: Record<AssignmentType, string> = { PRIMARY: "badge-success", SECONDARY: "badge-info", TEMPORARY: "badge-gold" };

/** Branch history plus transfer / extra-branch actions for one employee. */
export function BranchesPanel({ employee, onEmployeeChanged }: { employee: EmployeeDetail; onEmployeeChanged: (e: EmployeeDetail) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [rows, setRows] = useState<BranchAssignment[] | null>(null);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [dialog, setDialog] = useState<"assign" | "transfer" | null>(null);
  const [ending, setEnding] = useState<BranchAssignment | null>(null);
  const id = employee.summary.id;
  const isFormer = ["RESIGNED", "TERMINATED", "RETIRED"].includes(employee.summary.employmentStatus);
  const canAssign = employee.manageable && can("hr.branch.assign") && !isFormer;
  const canTransfer = employee.manageable && can("hr.employee.transfer") && !isFormer;

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getAssignments(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load branch history"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!canAssign && !canTransfer) return;
    authenticatedFetch(async (s) => {
      const [b, p] = await Promise.all([getBranches(s).catch(() => [] as BranchRecord[]), getPositions(s).catch(() => [] as Position[])]);
      setBranches(b.filter((branch) => branch.active && (access?.allBranches || access?.branchIds.includes(branch.id))));
      setPositions(p);
    }).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canAssign, canTransfer, access?.allBranches]);

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!rows) return <Skeleton className="h-40 w-full" />;

  return (
    <section className="grid gap-4">
      <SectionHeader
        title="Branches"
        description="Home branch history and any extra branches this person works at. Managers of an extra branch can see the employee but not edit their record."
        actions={(
          <>
            {canTransfer ? <button type="button" className="btn btn-secondary btn-sm" onClick={() => setDialog("transfer")}>Transfer</button> : null}
            {canAssign ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setDialog("assign")}><Icon name="plus" size={16} /> Add branch</button> : null}
          </>
        )}
      />
      {actionError ? <p className="alert alert-danger" role="alert">{actionError}</p> : null}
      {rows.length === 0 ? (
        <EmptyState compact icon="store" title="No branch history" description="This employee is not attached to a branch (head office)." />
      ) : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Branch</th><th>Type</th><th>From</th><th>Until</th><th>Notes</th><th className="num" /></tr></thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id} className={a.current ? "" : "text-ink-soft"}>
                  <td><span className="font-semibold">{a.branchName ?? "Branch"}</span>{a.positionTitle ? <p className="text-xs text-ink-muted">as {a.positionTitle}</p> : null}</td>
                  <td><span className={`badge ${typeTone[a.assignmentType]}`}>{humanize(a.assignmentType)}</span>{a.current ? <span className="ml-1 badge badge-dot badge-success">Current</span> : null}</td>
                  <td className="tabular">{formatDate(a.startDate)}</td>
                  <td className="tabular">{a.endDate ? formatDate(a.endDate) : "—"}</td>
                  <td className="max-w-xs text-sm">{[a.reason, a.endReason && `Ended: ${a.endReason}`].filter(Boolean).join(" · ") || "—"}</td>
                  <td className="num">
                    {canAssign && a.assignmentType !== "PRIMARY" && (!a.endDate || a.endDate >= todayIso()) ? (
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => { setActionError(""); setEnding(a); }}>End</button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {dialog === "assign" ? (
        <AssignDialog
          employee={employee}
          branches={branches.filter((b) => b.id !== employee.summary.primaryBranchId)}
          positions={positions}
          onClose={() => setDialog(null)}
          onDone={() => { setDialog(null); void load(); }}
        />
      ) : null}
      {dialog === "transfer" ? (
        <TransferDialog
          employee={employee}
          branches={branches.filter((b) => b.id !== employee.summary.primaryBranchId)}
          onClose={() => setDialog(null)}
          onDone={(updated) => { setDialog(null); onEmployeeChanged(updated); void load(); }}
        />
      ) : null}
      {ending ? (
        <EndDialog assignment={ending} onClose={() => setEnding(null)} onDone={() => { setEnding(null); void load(); }} onError={setActionError} />
      ) : null}
    </section>
  );
}

function AssignDialog({ employee, branches, positions, onClose, onDone }: {
  employee: EmployeeDetail; branches: BranchRecord[]; positions: Position[]; onClose: () => void; onDone: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState({ branchId: branches[0]?.id ?? "", type: "SECONDARY" as "SECONDARY" | "TEMPORARY", positionId: "", startDate: todayIso(), endDate: "", reason: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!form.branchId) return setError("Choose a branch.");
    if (form.type === "TEMPORARY" && !form.endDate) return setError("A temporary assignment needs an end date.");
    if (form.endDate && form.endDate < form.startDate) return setError("End date cannot be before the start date.");
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => assignBranch(s, employee.summary.id, {
        branchId: form.branchId, assignmentType: form.type, positionId: form.positionId || null,
        startDate: form.startDate, endDate: form.endDate || null, reason: form.reason.trim() || undefined,
      }));
      onDone();
    } catch (err) {
      setError(errorMessage(err, "Could not add the branch"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={`Add a branch for ${employee.summary.fullName}`}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary btn-md" onClick={submit} disabled={busy || branches.length === 0}>{busy ? "Saving…" : "Add branch"}</button>
        </>
      )}
    >
      {branches.length === 0 ? (
        <p className="text-sm text-ink-soft">There are no other branches in your scope.</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
          <Field label="Branch">
            <select className="select" value={form.branchId} onChange={(e) => setForm((f) => ({ ...f, branchId: e.target.value }))}>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </Field>
          <Field label="Type">
            <select className="select" value={form.type} onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as "SECONDARY" | "TEMPORARY" }))}>
              <option value="SECONDARY">Secondary (ongoing)</option>
              <option value="TEMPORARY">Temporary cover</option>
            </select>
          </Field>
          <Field label="Starts"><input type="date" className="input" value={form.startDate} min={employee.summary.hireDate} onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))} /></Field>
          <Field label={form.type === "TEMPORARY" ? "Ends *" : "Ends"}><input type="date" className="input" value={form.endDate} min={form.startDate} onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))} /></Field>
          <Field label="Role at this branch" hint="Only if different from their main position">
            <select className="select" value={form.positionId} onChange={(e) => setForm((f) => ({ ...f, positionId: e.target.value }))}>
              <option value="">Same as main position</option>
              {positions.map((p) => <option key={p.id} value={p.id}>{p.title}</option>)}
            </select>
          </Field>
          <Field label="Reason"><input className="input" value={form.reason} maxLength={500} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))} /></Field>
        </div>
      )}
    </Modal>
  );
}

function TransferDialog({ employee, branches, onClose, onDone }: {
  employee: EmployeeDetail; branches: BranchRecord[]; onClose: () => void; onDone: (e: EmployeeDetail) => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const { access } = useHrAccess();
  const [toBranchId, setToBranchId] = useState(branches[0]?.id ?? "");
  const [effectiveDate, setEffectiveDate] = useState(todayIso());
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      onDone(await authenticatedFetch((s) => transferEmployee(s, employee.summary.id, { toBranchId: toBranchId || null, effectiveDate, reason: reason.trim() || undefined })));
    } catch (err) {
      setError(errorMessage(err, "Could not transfer the employee"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={`Transfer ${employee.summary.fullName}`}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary btn-md" onClick={submit} disabled={busy}>{busy ? "Transferring…" : "Transfer"}</button>
        </>
      )}
    >
      <div className="grid gap-3">
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <p className="text-sm text-ink-soft">
          Currently based at <span className="font-semibold text-ink">{employee.summary.primaryBranchName ?? "head office"}</span>.
          The current assignment ends the day before the transfer date; history is kept.
        </p>
        <Field label="New home branch">
          <select className="select" value={toBranchId} onChange={(e) => setToBranchId(e.target.value)}>
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            {access?.allBranches && employee.summary.primaryBranchId ? <option value="">Head office (no branch)</option> : null}
          </select>
        </Field>
        <Field label="Effective from"><input type="date" className="input" value={effectiveDate} max={todayIso()} min={employee.summary.hireDate} onChange={(e) => setEffectiveDate(e.target.value)} /></Field>
        <Field label="Reason"><input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} /></Field>
      </div>
    </Modal>
  );
}

function EndDialog({ assignment, onClose, onDone, onError }: {
  assignment: BranchAssignment; onClose: () => void; onDone: () => void; onError: (message: string) => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [endDate, setEndDate] = useState(todayIso() < assignment.startDate ? assignment.startDate : todayIso());
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    try {
      await authenticatedFetch((s) => endAssignment(s, assignment.id, { endDate, reason: reason.trim() || undefined }));
      onDone();
    } catch (err) {
      onError(errorMessage(err, "Could not end the assignment"));
      onClose();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={`End assignment at ${assignment.branchName ?? "branch"}`}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Back</button>
          <button type="button" className="btn btn-danger-solid btn-md" onClick={submit} disabled={busy}>{busy ? "Saving…" : "End assignment"}</button>
        </>
      )}
    >
      <div className="grid gap-3">
        <Field label="Last day"><input type="date" className="input" value={endDate} min={assignment.startDate} max={assignment.endDate ?? undefined} onChange={(e) => setEndDate(e.target.value)} /></Field>
        <Field label="Reason"><input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} /></Field>
      </div>
    </Modal>
  );
}
