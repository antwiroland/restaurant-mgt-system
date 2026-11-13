"use client";

import { PayoutPanel } from "@/features/hr/payroll/PayoutPanel";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { ErrorState, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { BranchesPanel } from "@/features/hr/BranchesPanel";
import { ClockPinCard } from "@/features/hr/attendance/AttendancePanels";
import { ContractsPanel } from "@/features/hr/ContractsPanel";
import { DocumentsPanel } from "@/features/hr/DocumentsPanel";
import { EmployeeForm } from "@/features/hr/EmployeeForm";
import { EmployeeOverview } from "@/features/hr/EmployeeOverview";
import { EmployeeLeavePanel } from "@/features/hr/leave/EmployeeLeavePanel";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { EmploymentBadge, Field, HrGuard, errorMessage, formatDateTime, todayIso } from "@/features/hr/ui";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import {
  EMPLOYMENT_STATUSES,
  EXIT_STATUSES,
  changeEmployeeStatus,
  getDepartments,
  getDocuments,
  getEmployee,
  getEmployeeAudit,
  getLinkableUsers,
  getPositions,
  createEmployeeLogin,
  linkEmployeeUser,
  unlinkEmployeeUser,
  updateEmployee,
  type AuditEntry,
  type Department,
  type EmployeeDetail,
  type EmployeeDocument,
  type EmployeeInput,
  type EmploymentStatus,
  type LinkableUser,
  type Position,
} from "@/lib/hrApi";

type Tab = "overview" | "branches" | "contracts" | "documents" | "leave" | "payout" | "history";

export default function EmployeeProfilePage() {
  return (
    <HrGuard anyOf={["hr.employee.view"]}>
      <EmployeeProfile />
    </HrGuard>
  );
}

function EmployeeProfile() {
  const { id } = useParams<{ id: string }>();
  const { authenticatedFetch, session } = useStaffSession();
  const { can, access } = useHrAccess();
  const [employee, setEmployee] = useState<EmployeeDetail | null>(null);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<Tab>("overview");
  const [documents, setDocuments] = useState<EmployeeDocument[]>([]);
  const [showArchivedDocs, setShowArchivedDocs] = useState(false);
  const [audit, setAudit] = useState<AuditEntry[] | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [statusOpen, setStatusOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [createLoginOpen, setCreateLoginOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const [departments, setDepartments] = useState<Department[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [branches, setBranches] = useState<BranchRecord[]>([]);

  const load = useCallback(async () => {
    setError("");
    try {
      setEmployee(await authenticatedFetch((s) => getEmployee(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load this employee"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const loadDocuments = useCallback(async () => {
    if (!can("hr.document.view") || employee?.manageable === false) return;
    try {
      setDocuments(await authenticatedFetch((s) => getDocuments(s, id, showArchivedDocs)));
    } catch {
      setDocuments([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, showArchivedDocs, access, employee?.manageable]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { void loadDocuments(); }, [loadDocuments]);

  useEffect(() => {
    if (tab !== "history") return;
    setAudit(null);
    authenticatedFetch((s) => getEmployeeAudit(s, id)).then(setAudit).catch(() => setAudit([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, id]);

  async function openEditor() {
    setFormError("");
    setEditing(true);
    try {
      await authenticatedFetch(async (s) => {
        const [d, p, b] = await Promise.all([getDepartments(s, true), getPositions(s, true), getBranches(s).catch(() => [] as BranchRecord[])]);
        setDepartments(d);
        setPositions(p);
        setBranches(b.filter((branch) => access?.allBranches || access?.branchIds.includes(branch.id)));
      });
    } catch (err) {
      setFormError(errorMessage(err, "Could not load departments and positions"));
    }
  }

  async function save(input: EmployeeInput) {
    setSaving(true);
    setFormError("");
    try {
      setEmployee(await authenticatedFetch((s) => updateEmployee(s, id, input)));
      setEditing(false);
      setNotice("Changes saved.");
    } catch (err) {
      setFormError(errorMessage(err, "Could not save changes"));
    } finally {
      setSaving(false);
    }
  }

  async function unlink() {
    try {
      setEmployee(await authenticatedFetch((s) => unlinkEmployeeUser(s, id)));
      setNotice("Login account unlinked. The account itself was not changed.");
    } catch (err) {
      setNotice(errorMessage(err, "Could not unlink the account"));
    }
  }

  if (error) {
    return (
      <div className="grid gap-4">
        <Link href="/hr/employees" className="btn btn-ghost btn-sm w-fit"><Icon name="arrowLeft" size={16} /> Employees</Link>
        <ErrorState message={error} onRetry={load} />
      </div>
    );
  }
  if (!employee) return <div className="grid gap-3"><Skeleton className="h-24 w-full" /><Skeleton className="h-64 w-full" /></div>;

  const s = employee.summary;
  const tabs: { key: Tab; label: string; show: boolean }[] = [
    { key: "overview", label: "Overview", show: true },
    { key: "branches", label: "Branches", show: true },
    { key: "contracts", label: "Contracts", show: employee.manageable && can("hr.contract.view") },
    { key: "documents", label: "Documents", show: employee.manageable && can("hr.document.view") },
    { key: "leave", label: "Leave", show: can("hr.leave.approve", "hr.leave.manage") },
    { key: "payout", label: "Payout", show: can("hr.payroll.view", "hr.payroll.process", "hr.payroll.pay", "hr.payroll.approve", "hr.payroll.finalize") },
    { key: "history", label: "History", show: employee.manageable },
  ];

  return (
    <div className="grid gap-6">
      <Link href="/hr/employees" className="btn btn-ghost btn-sm w-fit"><Icon name="arrowLeft" size={16} /> Employees</Link>

      <header className="panel flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex min-w-0 items-center gap-4">
          <span className="grid h-14 w-14 shrink-0 place-items-center rounded-full bg-brand-subtle text-lg font-bold text-brand" aria-hidden="true">
            {(employee.firstName[0] ?? "") + (employee.lastName?.[0] ?? "")}
          </span>
          <div className="min-w-0">
            <h1 className="heading-lg truncate">{s.fullName}</h1>
            <p className="text-sm text-ink-soft">
              <span className="tabular">{s.employeeNumber}</span> · {s.positionTitle ?? "No position"} · {s.primaryBranchName ?? "Head office"}
            </p>
            <div className="mt-1.5 flex flex-wrap gap-2"><EmploymentBadge status={s.employmentStatus} /><span className="badge badge-neutral">{humanize(s.employmentType)}</span></div>
          </div>
        </div>
        {employee.manageable && can("hr.employee.update") ? (
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn btn-secondary btn-md" onClick={() => setStatusOpen(true)}>Change status</button>
            <button type="button" className="btn btn-primary btn-md" onClick={openEditor}><Icon name="edit" size={16} /> Edit</button>
          </div>
        ) : null}
      </header>

      {notice ? <p className="alert alert-info" role="status">{notice}</p> : null}
      {!employee.manageable ? (
        <p className="alert alert-info">This employee works at one of your branches but is based elsewhere. Their home branch manages their record.</p>
      ) : null}

      <div className="segmented max-w-full overflow-x-auto" role="tablist" aria-label="Employee sections">
        {tabs.filter((t) => t.show).map((t) => (
          <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}
          </button>
        ))}
      </div>

      <div className="panel">
        {tab === "overview" && employee.manageable && can("hr.attendance.view", "hr.attendance.manage") ? (
          <div className="mb-8"><ClockPinCard employeeId={id} /></div>
        ) : null}
        {tab === "overview" ? (
          <EmployeeOverview
            employee={employee}
            linkActions={employee.manageable && can("hr.employee.update") ? (
              employee.linkedUser ? (
                <button type="button" className="btn btn-ghost btn-sm" onClick={unlink}>Unlink login</button>
              ) : (
                <span className="flex flex-wrap gap-2">
                  {!EXIT_STATUSES.includes(s.employmentStatus) ? (
                    <button type="button" className="btn btn-primary btn-sm" onClick={() => setCreateLoginOpen(true)}>Create login</button>
                  ) : null}
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => setLinkOpen(true)}>Link existing login</button>
                </span>
              )
            ) : null}
          />
        ) : null}
        {tab === "contracts" ? <ContractsPanel employeeId={id} documents={documents} /> : null}
        {tab === "documents" ? (
          <DocumentsPanel employeeId={id} documents={documents} showArchived={showArchivedDocs} onToggleArchived={setShowArchivedDocs} onChanged={loadDocuments} />
        ) : null}
        {tab === "payout" ? <PayoutPanel employeeId={id} canEdit={can("hr.payroll.process", "hr.payroll.pay")} /> : null}
        {tab === "leave" ? <EmployeeLeavePanel employeeId={id} employeeName={s.fullName} manageable={employee.manageable} /> : null}
        {tab === "history" ? <History entries={audit} /> : null}
        {tab === "branches" ? <BranchesPanel employee={employee} onEmployeeChanged={(updated) => { setEmployee(updated); setNotice("Transfer recorded."); }} /> : null}
      </div>

      <Drawer open={editing} title={`Edit ${s.fullName}`} onClose={() => setEditing(false)}>
        {formError ? <div className="alert alert-danger mb-4" role="alert">{formError}</div> : null}
        {editing ? (
          <EmployeeForm
            formId="edit-employee"
            initial={employee}
            departments={departments}
            positions={positions}
            branches={branches}
            canChangeBranch={can("hr.employee.transfer")}
            showIds={can("hr.compensation.view", "hr.payroll.view", "hr.payroll.process")}
            submitting={saving}
            onSubmit={save}
            onCancel={() => setEditing(false)}
          />
        ) : null}
      </Drawer>

      {statusOpen ? (
        <StatusModal
          employee={employee}
          canArchive={can("hr.employee.archive")}
          onClose={() => setStatusOpen(false)}
          onDone={(updated) => { setEmployee(updated); setStatusOpen(false); setNotice(statusNotice(updated)); }}
        />
      ) : null}
      {createLoginOpen ? (
        <CreateLoginModal
          employee={employee}
          isAdmin={session?.user.role === "ADMIN"}
          onClose={() => setCreateLoginOpen(false)}
          onDone={(updated) => {
            setEmployee(updated);
            setCreateLoginOpen(false);
            setNotice(`Login created. ${updated.summary.fullName} signs in with ${updated.summary.phone} and the temporary password you set.`);
          }}
        />
      ) : null}
      {linkOpen ? (
        <LinkUserModal
          employeeId={id}
          onClose={() => setLinkOpen(false)}
          onDone={(updated) => { setEmployee(updated); setLinkOpen(false); setNotice("Login account linked."); }}
        />
      ) : null}
    </div>
  );
}

function History({ entries }: { entries: AuditEntry[] | null }) {
  if (!entries) return <Skeleton className="h-40 w-full" />;
  if (!entries.length) return <p className="text-sm text-ink-soft">No recorded changes.</p>;
  return (
    <ol className="grid gap-3">
      {entries.map((e) => {
        let details = "";
        try {
          const meta = e.metadata ? JSON.parse(e.metadata) as Record<string, unknown> : {};
          details = Object.entries(meta)
            .filter(([k]) => !k.endsWith("Id"))
            .map(([k, v]) => `${humanize(k.replace(/([A-Z])/g, "_$1").toUpperCase())}: ${Array.isArray(v) ? v.join(", ") : String(v)}`)
            .join(" · ");
        } catch {
          details = "";
        }
        return (
          <li key={e.id} className="rounded-xl border border-line p-3">
            <p className="text-sm font-semibold">{humanize(e.action.replace(/^HR_/, ""))}</p>
            <p className="text-xs text-ink-muted">{formatDateTime(e.createdAt)} · {e.actorName ?? "System"}</p>
            {details ? <p className="mt-1 text-sm text-ink-soft">{details}</p> : null}
          </li>
        );
      })}
    </ol>
  );
}

/** "Status changed to Resigned. Login switched off · 2 upcoming shifts are now open shifts · …" */
function statusNotice(updated: EmployeeDetail): string {
  const parts = [`Status changed to ${humanize(updated.summary.employmentStatus)}.`];
  const fx = updated.statusEffects;
  if (fx) {
    const more: string[] = [];
    if (fx.loginDisabled) more.push("login switched off and signed out everywhere");
    if (fx.loginRestored) more.push("login switched back on");
    if (fx.shiftsReleased) more.push(`${fx.shiftsReleased} upcoming shift${fx.shiftsReleased === 1 ? " is" : "s are"} now open shifts (managers told)`);
    if (fx.contractsEnded) more.push(`${fx.contractsEnded === 1 ? "contract" : `${fx.contractsEnded} contracts`} ended`);
    if (fx.leaveCancelled) more.push(`${fx.leaveCancelled} leave request${fx.leaveCancelled === 1 ? "" : "s"} cancelled`);
    if (more.length) parts.push(more.join(" · ").replace(/^./, (c) => c.toUpperCase()) + ".");
  }
  return parts.join(" ");
}

function StatusModal({ employee, canArchive, onClose, onDone }: {
  employee: EmployeeDetail; canArchive: boolean; onClose: () => void; onDone: (e: EmployeeDetail) => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const current = employee.summary.employmentStatus;
  const currentIsExit = EXIT_STATUSES.includes(current);
  const options = EMPLOYMENT_STATUSES.filter((st) => st !== current && (canArchive || (!EXIT_STATUSES.includes(st) && !currentIsExit)));
  const [status, setStatus] = useState<EmploymentStatus | "">(options[0] ?? "");
  const [date, setDate] = useState(todayIso());
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const exiting = !!status && EXIT_STATUSES.includes(status);
  const blocksLogin = exiting || status === "SUSPENDED";
  const [keepLogin, setKeepLogin] = useState(false);

  async function submit() {
    if (!status) return;
    if (exiting && !reason.trim()) return setError("A reason is required when employment ends.");
    setBusy(true);
    setError("");
    try {
      onDone(await authenticatedFetch((s) => changeEmployeeStatus(s, employee.summary.id, {
        status,
        effectiveDate: exiting ? date : undefined,
        reason: reason.trim() || undefined,
        keepLoginActive: blocksLogin && keepLogin ? true : undefined,
      })));
    } catch (err) {
      setError(errorMessage(err, "Could not change the status"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title="Change employment status"
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="button" className={`btn btn-md ${exiting ? "btn-danger-solid" : "btn-primary"}`} onClick={submit} disabled={busy || !status}>{busy ? "Saving…" : "Confirm"}</button>
        </>
      )}
    >
      {options.length === 0 ? (
        <p className="text-sm text-ink-soft">You do not have permission to change this employee&apos;s status.</p>
      ) : (
        <div className="grid gap-3">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          <Field label="New status">
            <select className="select" value={status} onChange={(e) => setStatus(e.target.value as EmploymentStatus)}>
              {options.map((st) => <option key={st} value={st}>{humanize(st)}</option>)}
            </select>
          </Field>
          {exiting ? (
            <>
              <Field label="Last working day"><input type="date" className="input" value={date} max={todayIso()} min={employee.summary.hireDate} onChange={(e) => setDate(e.target.value)} /></Field>
              <ul className="list-disc pl-5 text-sm text-ink-soft">
                <li>Shifts after the last working day become open shifts and the branch&apos;s managers are told.</li>
                <li>The active contract ends on the last working day.</li>
                <li>Pending leave, and approved leave after the last working day, is cancelled.</li>
              </ul>
            </>
          ) : null}
          {blocksLogin && employee.linkedUser?.active ? (
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" className="mt-1" checked={keepLogin} onChange={(e) => setKeepLogin(e.target.checked)} />
              <span>
                Keep their login switched on
                <span className="block text-ink-muted">
                  {keepLogin
                    ? "They can still sign in to the POS and the staff app."
                    : `By default the login is switched off and signed out everywhere${status === "SUSPENDED" ? "; it is switched back on when they return to active" : ""}.`}
                </span>
              </span>
            </label>
          ) : null}
          <Field label={exiting ? "Reason *" : "Reason"}><textarea className="textarea" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} /></Field>
        </div>
      )}
    </Modal>
  );
}

const LOGIN_ROLES: { value: string; label: string; adminOnly: boolean }[] = [
  { value: "CASHIER", label: "Cashier / floor staff", adminOnly: false },
  { value: "RIDER", label: "Delivery rider", adminOnly: false },
  { value: "MANAGER", label: "Branch manager", adminOnly: true },
  { value: "HR", label: "HR officer", adminOnly: true },
];

function CreateLoginModal({ employee, isAdmin, onClose, onDone }: {
  employee: EmployeeDetail; isAdmin: boolean; onClose: () => void; onDone: (e: EmployeeDetail) => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const roles = LOGIN_ROLES.filter((r) => isAdmin || !r.adminOnly);
  const [role, setRole] = useState(roles[0].value);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const phone = employee.summary.phone;

  async function submit() {
    if (password.length < 8) return setError("Use at least 8 characters for the temporary password.");
    setBusy(true);
    setError("");
    try {
      onDone(await authenticatedFetch((s) => createEmployeeLogin(s, employee.summary.id, { role, temporaryPassword: password })));
    } catch (err) {
      setError(errorMessage(err, "Could not create the login"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title="Create login"
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary btn-md" onClick={submit} disabled={busy || !phone}>{busy ? "Creating…" : "Create login"}</button>
        </>
      )}
    >
      <div className="grid gap-3 text-sm">
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {!phone ? (
          <p className="alert alert-info">Add a phone number to this employee first: it is what they sign in with.</p>
        ) : (
          <>
            <p className="text-ink-soft">
              {employee.summary.fullName} will sign in with <span className="font-semibold tabular">{phone}</span>
              {employee.summary.primaryBranchName ? <> at {employee.summary.primaryBranchName}</> : null}. Give them the temporary password in person.
            </p>
            <Field label="Role">
              <select className="select" value={role} onChange={(e) => setRole(e.target.value)}>
                {roles.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </Field>
            {!isAdmin ? <p className="text-xs text-ink-muted">Manager and HR logins are created by an admin.</p> : null}
            <Field label="Temporary password">
              <input type="text" className="input" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72} autoComplete="new-password" />
            </Field>
          </>
        )}
      </div>
    </Modal>
  );
}

function LinkUserModal({ employeeId, onClose, onDone }: { employeeId: string; onClose: () => void; onDone: (e: EmployeeDetail) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [users, setUsers] = useState<LinkableUser[] | null>(null);
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authenticatedFetch((s) => getLinkableUsers(s)).then(setUsers).catch((err) => { setUsers([]); setError(errorMessage(err, "Could not load accounts")); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function submit() {
    if (!selected) return;
    setBusy(true);
    setError("");
    try {
      onDone(await authenticatedFetch((s) => linkEmployeeUser(s, employeeId, selected)));
    } catch (err) {
      setError(errorMessage(err, "Could not link the account"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title="Link login account"
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="button" className="btn btn-primary btn-md" onClick={submit} disabled={busy || !selected}>{busy ? "Linking…" : "Link"}</button>
        </>
      )}
    >
      <div className="grid gap-3 text-sm">
        <p className="text-ink-soft">Linking lets this person use self-service with their existing login. It does not change their role or POS permissions.</p>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {users === null ? <Skeleton className="h-10 w-full" /> : users.length === 0 ? (
          <p>No unlinked staff accounts in your branches. Use Create login instead.</p>
        ) : (
          <Field label="Staff account">
            <select className="select" value={selected} onChange={(e) => setSelected(e.target.value)}>
              <option value="">Choose an account</option>
              {users.map((u) => <option key={u.id} value={u.id}>{u.name} · {humanize(u.role)} · {u.phone}</option>)}
            </select>
          </Field>
        )}
      </div>
    </Modal>
  );
}
