"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { EmployeeForm } from "@/features/hr/EmployeeForm";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { EmploymentBadge, Field, HrGuard, Pager, errorMessage, formatDate, todayIso } from "@/features/hr/ui";
import { getBranches, saveDownloadedFile, type BranchRecord } from "@/lib/apiClient";
import {
  ATTENTION_LABELS,
  EMPLOYMENT_STATUSES,
  EMPLOYMENT_TYPES,
  createEmployee,
  exportEmployees,
  getDepartments,
  getPositions,
  importStaffUsers,
  listEmployees,
  type Department,
  type EmployeeFilters,
  type EmployeeInput,
  type EmployeeSummary,
  type EmploymentType,
  type ImportResult,
  type Page,
  type Position,
} from "@/lib/hrApi";

const PAGE_SIZE = 25;

export default function EmployeesPage() {
  return (
    <HrGuard anyOf={["hr.employee.view"]}>
      <EmployeeDirectory />
    </HrGuard>
  );
}

function EmployeeDirectory() {
  const router = useRouter();
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [filters, setFilters] = useState<EmployeeFilters>({ page: 0, size: PAGE_SIZE, sort: "name,asc" });
  const [search, setSearch] = useState("");
  const [data, setData] = useState<Page<EmployeeSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [departments, setDepartments] = useState<Department[]>([]);
  const [positions, setPositions] = useState<Position[]>([]);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [importOpen, setImportOpen] = useState(false);

  // debounce free-text search into the filters
  useEffect(() => {
    const handle = setTimeout(() => setFilters((f) => (f.q === search.trim() ? f : { ...f, q: search.trim(), page: 0 })), 300);
    return () => clearTimeout(handle);
  }, [search]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setData(await authenticatedFetch((s) => listEmployees(s, filters)));
    } catch (err) {
      setError(errorMessage(err, "Could not load employees"));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    authenticatedFetch(async (s) => {
      const [d, p, b] = await Promise.all([
        getDepartments(s, true).catch(() => []),
        getPositions(s, true).catch(() => []),
        getBranches(s).catch(() => [] as BranchRecord[]),
      ]);
      setDepartments(d);
      setPositions(p);
      setBranches(b.filter((branch) => access?.allBranches || access?.branchIds.includes(branch.id)));
    }).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access?.allBranches]);

  async function submitCreate(input: EmployeeInput) {
    setSaving(true);
    setFormError("");
    try {
      const created = await authenticatedFetch((s) => createEmployee(s, input));
      setCreating(false);
      router.push(`/hr/employees/${created.summary.id}`);
    } catch (err) {
      setFormError(errorMessage(err, "Could not create the employee"));
    } finally {
      setSaving(false);
    }
  }

  const update = (patch: Partial<EmployeeFilters>) => setFilters((f) => ({ ...f, ...patch, page: patch.page ?? 0 }));
  const canCreate = can("hr.employee.create");
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState("");

  async function exportCsv() {
    setExporting(true);
    setExportError("");
    try {
      saveDownloadedFile(await authenticatedFetch((s) => exportEmployees(s, filters)));
    } catch (err) {
      setExportError(errorMessage(err, "Could not export the list"));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="grid gap-6">
      <PageHeader
        eyebrow="Human Resources"
        title="Employees"
        icon="users"
        description="Everyone who works for the business, across the branches you can access."
        actions={(
          <>
            <button type="button" className="btn btn-secondary btn-md" onClick={() => void exportCsv()} disabled={exporting}>
              <Icon name="download" size={16} /> {exporting ? "Exporting…" : "Export CSV"}
            </button>
            {canCreate ? (
            <>
            <button type="button" className="btn btn-secondary btn-md" onClick={() => setImportOpen(true)}>
              <Icon name="download" size={16} /> Import staff accounts
            </button>
            <button type="button" className="btn btn-primary btn-md" onClick={() => { setFormError(""); setCreating(true); }}>
              <Icon name="plus" size={16} /> New employee
            </button>
            </>
            ) : null}
          </>
        )}
      />
      {exportError ? <p className="alert alert-danger" role="alert">{exportError}</p> : null}

      <section className="panel grid gap-4">
        <div className="grid gap-3 md:grid-cols-[minmax(0,2fr)_repeat(4,minmax(0,1fr))]">
          <label className="relative">
            <span className="sr-only">Search employees</span>
            <Icon name="search" size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input className="input pl-9" placeholder="Name, number, phone or email" value={search} onChange={(e) => setSearch(e.target.value)} />
          </label>
          <select className="select" aria-label="Branch" value={filters.branchId ?? ""} onChange={(e) => update({ branchId: e.target.value || undefined })}>
            <option value="">All my branches</option>
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
          <select className="select" aria-label="Department" value={filters.departmentId ?? ""} onChange={(e) => update({ departmentId: e.target.value || undefined })}>
            <option value="">All departments</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
          <select className="select" aria-label="Status" value={filters.status ?? ""} onChange={(e) => update({ status: e.target.value as EmployeeFilters["status"] })}>
            <option value="">Current staff</option>
            {EMPLOYMENT_STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
          </select>
          <select className="select" aria-label="Employment type" value={filters.type ?? ""} onChange={(e) => update({ type: e.target.value as EmployeeFilters["type"] })}>
            <option value="">All types</option>
            {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
          <label className="flex items-center gap-2 text-ink-soft">
            <input type="checkbox" checked={!!filters.includeExited} onChange={(e) => update({ includeExited: e.target.checked })} disabled={!!filters.status} />
            Include former employees
          </label>
          <label className="flex items-center gap-2 text-ink-soft">
            Sort
            <select className="select w-auto text-sm" value={filters.sort} onChange={(e) => update({ sort: e.target.value })}>
              <option value="name,asc">Name A–Z</option>
              <option value="name,desc">Name Z–A</option>
              <option value="number,asc">Employee number</option>
              <option value="hireDate,desc">Newest hires</option>
              <option value="hireDate,asc">Longest serving</option>
              <option value="status,asc">Status</option>
            </select>
          </label>
        </div>

        {error ? (
          <ErrorState message={error} onRetry={load} />
        ) : loading && !data ? (
          <div className="grid gap-2" aria-busy="true">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}</div>
        ) : data && data.items.length === 0 ? (
          <EmptyState
            icon="users"
            title={filters.q || filters.departmentId || filters.status || filters.type || filters.branchId ? "No employees match these filters" : "No employees yet"}
            description={canCreate ? "Add employees one by one, or import the staff accounts that already sign in to the system." : undefined}
          />
        ) : data ? (
          <>
            <div className={`overflow-x-auto ${loading ? "opacity-60" : ""}`}>
              <table className="data-table">
                <thead>
                  <tr><th>Employee</th><th>Position</th><th>Branch</th><th>Type</th><th>Hired</th><th>Status</th></tr>
                </thead>
                <tbody>
                  {data.items.map((e) => (
                    <tr key={e.id}>
                      <td>
                        <Link href={`/hr/employees/${e.id}`} className="font-semibold text-ink hover:text-brand">{e.fullName}</Link>
                        <p className="text-xs text-ink-muted tabular">{e.employeeNumber}{e.hasUserAccount ? " · has login" : ""}</p>
                        {e.attention?.length ? (
                          <p className="text-xs text-warning-on" title={e.attention.map((a) => ATTENTION_LABELS[a]).join(", ")}>
                            <Icon name="alert" size={12} /> {e.attention.length === 1 ? ATTENTION_LABELS[e.attention[0]] : `${e.attention.length} things missing`}
                          </p>
                        ) : null}
                      </td>
                      <td>{e.positionTitle ?? <span className="text-ink-muted">—</span>}<p className="text-xs text-ink-muted">{e.departmentName ?? ""}</p></td>
                      <td>{e.primaryBranchName ?? <span className="text-ink-muted">Head office</span>}</td>
                      <td>{humanize(e.employmentType)}</td>
                      <td className="tabular">{formatDate(e.hireDate)}</td>
                      <td><EmploymentBadge status={e.employmentStatus} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={data.page} size={data.size} total={data.total} onPage={(page) => update({ page })} />
          </>
        ) : null}
      </section>

      <Drawer open={creating} title="New employee" onClose={() => setCreating(false)}>
        {formError ? <div className="alert alert-danger mb-4" role="alert">{formError}</div> : null}
        {creating ? (
          <EmployeeForm
            formId="create-employee"
            departments={departments}
            positions={positions}
            branches={branches}
            canChangeBranch
            showIds={can("hr.compensation.view", "hr.payroll.view", "hr.payroll.process")}
            submitting={saving}
            onSubmit={submitCreate}
            onCancel={() => setCreating(false)}
          />
        ) : null}
      </Drawer>

      <ImportModal open={importOpen} onClose={() => setImportOpen(false)} onDone={() => void load()} />
    </div>
  );
}

function ImportModal({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [type, setType] = useState<EmploymentType>("FULL_TIME");
  const [hireDate, setHireDate] = useState(todayIso());
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState("");

  async function run() {
    setBusy(true);
    setError("");
    try {
      setResult(await authenticatedFetch((s) => importStaffUsers(s, { employmentType: type, hireDate })));
      onDone();
    } catch (err) {
      setError(errorMessage(err, "Import failed"));
    } finally {
      setBusy(false);
    }
  }

  function close() {
    setResult(null);
    setError("");
    onClose();
  }

  return (
    <Modal
      open={open}
      title="Import staff accounts"
      onClose={close}
      footer={result ? (
        <button type="button" className="btn btn-primary btn-md" onClick={close}>Done</button>
      ) : (
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={close}>Cancel</button>
          <button type="button" className="btn btn-primary btn-md" onClick={run} disabled={busy || !hireDate}>{busy ? "Importing…" : "Import"}</button>
        </>
      )}
    >
      {result ? (
        <div className="grid gap-3 text-sm">
          <p className="alert alert-success">{result.created} employee record{result.created === 1 ? "" : "s"} created and linked to their login.</p>
          {result.skipped.length ? (
            <div>
              <p className="font-semibold">Skipped — resolve these manually:</p>
              <ul className="mt-1 list-disc pl-5 text-ink-soft">{result.skipped.map((s) => <li key={s.userId}>{s.name}: {s.reason}</li>)}</ul>
            </div>
          ) : null}
          {result.created ? <p className="text-ink-soft">Review each imported record and correct hire dates, positions and personal details.</p> : null}
        </div>
      ) : (
        <div className="grid gap-4 text-sm">
          <p className="text-ink-soft">
            Creates an employee record for every active staff login (admins, managers, cashiers, riders) in your branches that does not have one yet.
            Names, phone, email and branch are copied from the account. Accounts whose phone or email already belongs to an employee are skipped, never merged.
          </p>
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Employment type for all imported records">
              <select className="select" value={type} onChange={(e) => setType(e.target.value as EmploymentType)}>
                {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
              </select>
            </Field>
            <Field label="Default hire date" hint="Correct individual dates afterwards">
              <input type="date" className="input" value={hireDate} max={todayIso()} onChange={(e) => setHireDate(e.target.value)} />
            </Field>
          </div>
        </div>
      )}
    </Modal>
  );
}
