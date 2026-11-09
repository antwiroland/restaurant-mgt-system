"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Field, HrGuard, errorMessage } from "@/features/hr/ui";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import {
  STAFF_CATEGORIES,
  getDepartments,
  getPositions,
  saveDepartment,
  savePosition,
  setDepartmentActive,
  setPositionActive,
  type Department,
  type Position,
  type StaffCategory,
} from "@/lib/hrApi";

export default function OrganisationPage() {
  return (
    <HrGuard anyOf={["hr.employee.view", "hr.department.manage", "hr.position.manage"]}>
      <Organisation />
    </HrGuard>
  );
}

function Organisation() {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [departments, setDepartments] = useState<Department[] | null>(null);
  const [positions, setPositions] = useState<Position[] | null>(null);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [editDept, setEditDept] = useState<Department | "new" | null>(null);
  const [editPos, setEditPos] = useState<Position | "new" | null>(null);
  const manageDepts = can("hr.department.manage");
  const managePositions = can("hr.position.manage");

  const load = useCallback(async () => {
    setError("");
    try {
      await authenticatedFetch(async (s) => {
        const [d, p, b] = await Promise.all([getDepartments(s, true), getPositions(s, true), getBranches(s).catch(() => [] as BranchRecord[])]);
        setDepartments(d);
        setPositions(p);
        setBranches(b);
      });
    } catch (err) {
      setError(errorMessage(err, "Could not load the organisation"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function toggleDept(d: Department) {
    setActionError("");
    try {
      await authenticatedFetch((s) => setDepartmentActive(s, d.id, !d.active));
      await load();
    } catch (err) {
      setActionError(errorMessage(err, "Could not update the department"));
    }
  }

  async function togglePos(p: Position) {
    setActionError("");
    try {
      await authenticatedFetch((s) => setPositionActive(s, p.id, !p.active));
      await load();
    } catch (err) {
      setActionError(errorMessage(err, "Could not update the position"));
    }
  }

  const branchName = (id?: string | null) => (id ? branches.find((b) => b.id === id)?.name ?? "Branch" : "All branches");
  const visibleDepts = (departments ?? []).filter((d) => showArchived || d.active);
  const visiblePositions = (positions ?? []).filter((p) => showArchived || p.active);

  return (
    <div className="grid gap-6">
      <PageHeader
        eyebrow="Human Resources"
        title="Departments & Positions"
        icon="clipboard"
        description="How the business is organised. Records in use are archived rather than deleted, so history stays intact."
        actions={(
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Show archived
          </label>
        )}
      />
      {actionError ? <p className="alert alert-danger" role="alert">{actionError}</p> : null}
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      <section className="panel grid gap-4">
        <SectionHeader
          title="Departments"
          actions={manageDepts ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditDept("new")}><Icon name="plus" size={16} /> Department</button> : null}
        />
        {!departments ? <Skeleton className="h-40 w-full" /> : visibleDepts.length === 0 ? <EmptyState compact icon="clipboard" title="No departments" /> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Department</th><th>Category</th><th>Scope</th><th>Head</th><th className="num">Actions</th></tr></thead>
              <tbody>
                {visibleDepts.map((d) => (
                  <tr key={d.id} className={d.active ? "" : "opacity-60"}>
                    <td><span className="font-semibold">{d.name}</span><p className="text-xs text-ink-muted">{d.code}{d.active ? "" : " · archived"}</p></td>
                    <td>{humanize(d.category)}</td>
                    <td>{branchName(d.branchId)}</td>
                    <td>{d.headEmployeeName ?? <span className="text-ink-muted">—</span>}</td>
                    <td className="num">
                      {manageDepts && (d.branchId || access?.allBranches) ? (
                        <div className="flex justify-end gap-1">
                          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditDept(d)}>Edit</button>
                          <button type="button" className="btn btn-ghost btn-sm" onClick={() => toggleDept(d)}>{d.active ? "Archive" : "Restore"}</button>
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

      <section className="panel grid gap-4">
        <SectionHeader
          title="Positions"
          actions={managePositions ? <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditPos("new")}><Icon name="plus" size={16} /> Position</button> : null}
        />
        {!positions ? <Skeleton className="h-40 w-full" /> : visiblePositions.length === 0 ? <EmptyState compact icon="clipboard" title="No positions" /> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Position</th><th>Department</th><th>Category</th><th className="num">Actions</th></tr></thead>
              <tbody>
                {visiblePositions.map((p) => (
                  <tr key={p.id} className={p.active ? "" : "opacity-60"}>
                    <td>
                      <span className="font-semibold">{p.title}</span>
                      <p className="text-xs text-ink-muted">{p.code}{p.active ? "" : " · archived"}</p>
                      {p.description ? <p className="mt-1 max-w-md text-xs text-ink-soft">{p.description}</p> : null}
                    </td>
                    <td>{p.departmentName ?? <span className="text-ink-muted">—</span>}</td>
                    <td>{humanize(p.category)}</td>
                    <td className="num">
                      {managePositions ? (
                        <div className="flex justify-end gap-1">
                          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditPos(p)}>Edit</button>
                          <button type="button" className="btn btn-ghost btn-sm" onClick={() => togglePos(p)}>{p.active ? "Archive" : "Restore"}</button>
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

      {editDept ? (
        <DepartmentModal
          department={editDept === "new" ? null : editDept}
          branches={branches.filter((b) => access?.allBranches || access?.branchIds.includes(b.id))}
          central={!!access?.allBranches}
          onClose={() => setEditDept(null)}
          onSaved={() => { setEditDept(null); void load(); }}
        />
      ) : null}
      {editPos ? (
        <PositionModal
          position={editPos === "new" ? null : editPos}
          departments={(departments ?? []).filter((d) => d.active || (editPos !== "new" && d.id === editPos.departmentId))}
          onClose={() => setEditPos(null)}
          onSaved={() => { setEditPos(null); void load(); }}
        />
      ) : null}
    </div>
  );
}

function DepartmentModal({ department, branches, central, onClose, onSaved }: {
  department: Department | null; branches: BranchRecord[]; central: boolean; onClose: () => void; onSaved: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState({
    code: department?.code ?? "",
    name: department?.name ?? "",
    category: (department?.category ?? "FRONT_OF_HOUSE") as StaffCategory,
    description: department?.description ?? "",
    branchId: department?.branchId ?? (central ? "" : branches[0]?.id ?? ""),
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.code.trim() || !form.name.trim()) return setError("Code and name are required.");
    if (!/^[A-Za-z0-9 _-]+$/.test(form.code.trim())) return setError("Code may only contain letters, numbers, spaces, - and _.");
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => saveDepartment(s, {
        code: form.code.trim(), name: form.name.trim(), category: form.category, description: form.description.trim() || undefined,
        branchId: form.branchId || null, headEmployeeId: department?.headEmployeeId ?? null,
      }, department?.id));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the department"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={department ? `Edit ${department.name}` : "New department"}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="dept-form" className="btn btn-primary btn-md" disabled={busy}>{busy ? "Saving…" : "Save"}</button>
        </>
      )}
    >
      <form id="dept-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="Code *"><input className="input" value={form.code} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))} maxLength={30} /></Field>
        <Field label="Name *"><input className="input" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} maxLength={120} /></Field>
        <Field label="Category">
          <select className="select" value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as StaffCategory }))}>
            {STAFF_CATEGORIES.map((c) => <option key={c} value={c}>{humanize(c)}</option>)}
          </select>
        </Field>
        <Field label="Scope">
          <select className="select" value={form.branchId} onChange={(e) => setForm((f) => ({ ...f, branchId: e.target.value }))}>
            {central ? <option value="">All branches</option> : null}
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name} only</option>)}
          </select>
        </Field>
        <Field label="Description" className="sm:col-span-2">
          <textarea className="textarea" rows={3} value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} maxLength={1000} />
        </Field>
      </form>
    </Modal>
  );
}

function PositionModal({ position, departments, onClose, onSaved }: {
  position: Position | null; departments: Department[]; onClose: () => void; onSaved: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [form, setForm] = useState({
    code: position?.code ?? "",
    title: position?.title ?? "",
    departmentId: position?.departmentId ?? "",
    category: (position?.category ?? "") as StaffCategory | "",
    description: position?.description ?? "",
    requirements: position?.requirements ?? "",
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.code.trim() || !form.title.trim()) return setError("Code and title are required.");
    if (!/^[A-Za-z0-9 _-]+$/.test(form.code.trim())) return setError("Code may only contain letters, numbers, spaces, - and _.");
    setBusy(true);
    setError("");
    try {
      await authenticatedFetch((s) => savePosition(s, {
        code: form.code.trim(), title: form.title.trim(), departmentId: form.departmentId || null, category: form.category || null,
        description: form.description.trim() || undefined, requirements: form.requirements.trim() || undefined,
      }, position?.id));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the position"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={position ? `Edit ${position.title}` : "New position"}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="pos-form" className="btn btn-primary btn-md" disabled={busy}>{busy ? "Saving…" : "Save"}</button>
        </>
      )}
    >
      <form id="pos-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="Code *"><input className="input" value={form.code} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))} maxLength={30} /></Field>
        <Field label="Title *"><input className="input" value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} maxLength={120} /></Field>
        <Field label="Department">
          <select className="select" value={form.departmentId} onChange={(e) => setForm((f) => ({ ...f, departmentId: e.target.value }))}>
            <option value="">None</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </Field>
        <Field label="Category" hint="Defaults to the department's category">
          <select className="select" value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as StaffCategory | "" }))}>
            <option value="">From department</option>
            {STAFF_CATEGORIES.map((c) => <option key={c} value={c}>{humanize(c)}</option>)}
          </select>
        </Field>
        <Field label="Job description" className="sm:col-span-2">
          <textarea className="textarea" rows={3} value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} maxLength={2000} />
        </Field>
        <Field label="Requirements" className="sm:col-span-2">
          <textarea className="textarea" rows={2} value={form.requirements} onChange={(e) => setForm((f) => ({ ...f, requirements: e.target.value }))} maxLength={2000} />
        </Field>
      </form>
    </Modal>
  );
}
