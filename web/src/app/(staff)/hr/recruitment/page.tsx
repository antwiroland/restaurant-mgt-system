"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, Field, errorMessage, formatDate } from "@/features/hr/ui";
import { CandidatesPanel } from "@/features/hr/recruitment/CandidatesPanel";
import { VacancyDrawer } from "@/features/hr/recruitment/VacancyDrawer";
import { vacancyTone } from "@/features/hr/recruitment/recruitUtils";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import {
  EMPLOYMENT_TYPES,
  createVacancy,
  getPositions,
  getVacancies,
  type EmploymentType,
  type Position,
  type VacancyStatus,
  type VacancySummary,
} from "@/lib/hrApi";

type Tab = "vacancies" | "candidates";

export default function RecruitmentPage() {
  return (
    <HrGuard anyOf={["hr.recruitment.manage"]}>
      <Recruitment />
    </HrGuard>
  );
}

function Recruitment() {
  const { authenticatedFetch } = useStaffSession();
  const { access } = useHrAccess();
  const [tab, setTab] = useState<Tab>("vacancies");
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState("");
  const [status, setStatus] = useState<VacancyStatus | "">("");
  const [rows, setRows] = useState<VacancySummary[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    authenticatedFetch((s) => getBranches(s)).then((list) => {
      const visible = list.filter((b) => b.active && (access?.allBranches || access?.branchIds.includes(b.id)));
      setBranches(visible);
      setBranchId((c) => c || visible[0]?.id || "");
    }).catch((err) => setError(errorMessage(err, "Could not load branches")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access?.allBranches, access?.branchIds.join(",")]);

  const load = useCallback(async () => {
    if (!branchId) return;
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getVacancies(s, { branchId, status: status || undefined })));
    } catch (err) {
      setError(errorMessage(err, "Could not load vacancies"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, status]);

  useEffect(() => { void load(); }, [load]);

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Recruitment"
        icon="user"
        description="Open vacancies, follow applicants from first contact to hire, and turn an accepted offer into an employee record in one step."
        actions={(
          <div className="flex flex-wrap gap-2">
            {branches.length > 1 && tab === "vacancies" ? (
              <select className="select w-auto" value={branchId} onChange={(e) => setBranchId(e.target.value)} aria-label="Branch">
                {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            ) : null}
            {tab === "vacancies" ? <button type="button" className="btn btn-primary btn-md" disabled={!branchId} onClick={() => setCreating(true)}><Icon name="plus" size={16} /> New vacancy</button> : null}
          </div>
        )}
      />
      <div className="segmented max-w-full overflow-x-auto" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "vacancies"} onClick={() => setTab("vacancies")}>Vacancies</button>
        <button type="button" role="tab" aria-selected={tab === "candidates"} onClick={() => setTab("candidates")}>Candidates</button>
      </div>
      {error ? <ErrorState message={error} onRetry={load} /> : null}

      {tab === "vacancies" ? (
        <section className="panel grid gap-3">
          <Field label="Status" className="w-48">
            <select className="select" value={status} onChange={(e) => setStatus(e.target.value as VacancyStatus | "")}>
              <option value="">Any</option>
              {(["DRAFT", "OPEN", "ON_HOLD", "FILLED", "CLOSED", "CANCELLED"] as VacancyStatus[]).map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </select>
          </Field>
          {!rows ? <Skeleton className="h-40 w-full" /> : rows.length === 0 ? (
            <EmptyState compact icon="user" title="No vacancies" description="Create one to start collecting applicants." />
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead><tr><th>Vacancy</th><th className="num">Hired</th><th className="num">In progress</th><th>Closing</th><th>Status</th></tr></thead>
                <tbody>
                  {rows.map((v) => {
                    const active = (["APPLIED", "SCREENING", "INTERVIEW", "TRIAL_SHIFT", "OFFER"] as const).reduce((n, s) => n + (v.pipeline[s] ?? 0), 0);
                    return (
                      <tr key={v.id} className="cursor-pointer" onClick={() => setOpen(v.id)}>
                        <td className="font-semibold">{v.title}<p className="text-xs font-normal text-ink-muted">{v.vacancyNumber}{v.positionTitle ? ` · ${v.positionTitle}` : ""}</p></td>
                        <td className="num tabular">{v.hiredCount} / {v.openings}</td>
                        <td className="num tabular">{active}</td>
                        <td className="text-sm">{formatDate(v.closingDate)}</td>
                        <td><span className={`badge badge-dot ${vacancyTone[v.status]}`}>{humanize(v.status)}</span></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : <CandidatesPanel />}

      {creating ? <NewVacancyModal branchId={branchId} onClose={() => setCreating(false)} onCreated={(id) => { setCreating(false); void load(); setOpen(id); }} /> : null}
      {open ? <VacancyDrawer id={open} onClose={() => setOpen(null)} onChanged={() => void load()} /> : null}
    </div>
  );
}

function NewVacancyModal({ branchId, onClose, onCreated }: { branchId: string; onClose: () => void; onCreated: (id: string) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [positions, setPositions] = useState<Position[]>([]);
  const [title, setTitle] = useState("");
  const [positionId, setPositionId] = useState("");
  const [employmentType, setEmploymentType] = useState<EmploymentType>("FULL_TIME");
  const [openings, setOpenings] = useState("1");
  const [requirements, setRequirements] = useState("");
  const [salaryMin, setSalaryMin] = useState("");
  const [salaryMax, setSalaryMax] = useState("");
  const [closingDate, setClosingDate] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authenticatedFetch((s) => getPositions(s)).then((p) => setPositions(p.filter((x) => x.active))).catch(() => setPositions([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const v = await authenticatedFetch((s) => createVacancy(s, {
        branchId, title, positionId: positionId || null, employmentType, openings: Number(openings || 1), requirements: requirements || undefined,
        salaryMin: salaryMin ? Number(salaryMin) : null, salaryMax: salaryMax ? Number(salaryMax) : null, closingDate: closingDate || null,
      }));
      onCreated(v.summary.id);
    } catch (err) {
      setError(errorMessage(err, "Could not create the vacancy"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open title="New vacancy" onClose={onClose}
      footer={(
        <div className="flex w-full justify-end gap-2">
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="vacancy-form" className="btn btn-primary btn-md" disabled={busy || !title}>Save as draft</button>
        </div>
      )}>
      <form id="vacancy-form" className="grid gap-3" onSubmit={submit}>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <Field label="Title"><input className="input" value={title} required maxLength={160} onChange={(e) => setTitle(e.target.value)} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Position">
            <select className="select" value={positionId} onChange={(e) => { setPositionId(e.target.value); if (!title) setTitle(positions.find((p) => p.id === e.target.value)?.title ?? ""); }}>
              <option value="">—</option>
              {positions.map((p) => <option key={p.id} value={p.id}>{p.title}</option>)}
            </select>
          </Field>
          <Field label="Employment type">
            <select className="select" value={employmentType} onChange={(e) => setEmploymentType(e.target.value as EmploymentType)}>
              {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
            </select>
          </Field>
          <Field label="Openings"><input className="input tabular" inputMode="numeric" value={openings} onChange={(e) => setOpenings(e.target.value.replace(/\D/g, ""))} /></Field>
          <Field label="Closing date (optional)"><input type="date" className="input" value={closingDate} onChange={(e) => setClosingDate(e.target.value)} /></Field>
          {can("hr.compensation.view") ? (
            <>
              <Field label="Pay from (optional)"><input className="input tabular" inputMode="decimal" value={salaryMin} onChange={(e) => setSalaryMin(e.target.value.replace(/[^0-9.]/g, ""))} /></Field>
              <Field label="Pay to (optional)"><input className="input tabular" inputMode="decimal" value={salaryMax} onChange={(e) => setSalaryMax(e.target.value.replace(/[^0-9.]/g, ""))} /></Field>
            </>
          ) : null}
        </div>
        <Field label="Requirements (optional)"><textarea className="input min-h-20" value={requirements} maxLength={10000} onChange={(e) => setRequirements(e.target.value)} /></Field>
      </form>
    </Modal>
  );
}
