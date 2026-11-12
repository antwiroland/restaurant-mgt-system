"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Detail, Field, errorMessage, formatDate, formatDateTime } from "@/features/hr/ui";
import { money } from "@/features/hr/payroll/payrollUtils";
import {
  CANDIDATE_SOURCES,
  PIPELINE,
  addApplication,
  getVacancy,
  searchCandidates,
  setVacancyPublished,
  setVacancyStatus,
  type ApplicationStage,
  type CandidateSource,
  type CandidateSummary,
  type VacancyDetail,
  type VacancyStatus,
} from "@/lib/hrApi";
import { ApplicationDrawer } from "./ApplicationDrawer";
import { stageLabel, stageTone, vacancyTone } from "./recruitUtils";

const transitions: Record<VacancyStatus, { to: VacancyStatus; label: string; needsReason?: boolean }[]> = {
  DRAFT: [{ to: "OPEN", label: "Open for applications" }, { to: "CANCELLED", label: "Cancel vacancy", needsReason: true }],
  OPEN: [{ to: "ON_HOLD", label: "Put on hold" }, { to: "CLOSED", label: "Close vacancy", needsReason: true }, { to: "CANCELLED", label: "Cancel vacancy", needsReason: true }],
  ON_HOLD: [{ to: "OPEN", label: "Reopen" }, { to: "CLOSED", label: "Close vacancy", needsReason: true }, { to: "CANCELLED", label: "Cancel vacancy", needsReason: true }],
  FILLED: [{ to: "CLOSED", label: "Close vacancy", needsReason: true }],
  CLOSED: [{ to: "OPEN", label: "Reopen" }],
  CANCELLED: [],
};

/** A vacancy with its pipeline: who is at which stage, adding applicants and changing the vacancy's status. */
export function VacancyDrawer({ id, onClose, onChanged }: { id: string; onClose: () => void; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [vacancy, setVacancy] = useState<VacancyDetail | null>(null);
  const [openApp, setOpenApp] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [showClosed, setShowClosed] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setVacancy(await authenticatedFetch((s) => getVacancy(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the vacancy"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => { void load(); }, [load]);

  async function changeStatus(to: VacancyStatus) {
    setBusy(true);
    setError("");
    try {
      setVacancy(await authenticatedFetch((s) => setVacancyStatus(s, id, { status: to, reason: reason || undefined })));
      setReason("");
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not change the vacancy"));
    } finally {
      setBusy(false);
    }
  }

  async function changePublished(published: boolean) {
    setBusy(true);
    setError("");
    try {
      setVacancy(await authenticatedFetch((s) => setVacancyPublished(s, id, published)));
      onChanged();
    } catch (err) {
      setError(errorMessage(err, published ? "Could not publish the vacancy" : "Could not unpublish the vacancy"));
    } finally {
      setBusy(false);
    }
  }

  const v = vacancy?.summary;
  const closedStages: ApplicationStage[] = ["HIRED", "REJECTED", "WITHDRAWN"];
  const actions = v ? transitions[v.status] : [];

  return (
    <Drawer open title={v ? `${v.vacancyNumber} · ${v.title}` : "Vacancy"} onClose={onClose}>
      {!vacancy || !v ? (error ? <p className="alert alert-danger">{error}</p> : <Skeleton className="h-64 w-full" />) : (
        <div className="grid gap-5">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          <div className="flex flex-wrap items-center gap-2">
            <span className={`badge badge-dot ${vacancyTone[v.status]}`}>{humanize(v.status)}</span>
            {v.published ? <span className="badge badge-info">On careers page</span> : null}
            <span className="text-sm text-ink-muted">{v.branchName}{v.positionTitle ? ` · ${v.positionTitle}` : ""} · {humanize(v.employmentType)}</span>
          </div>
          {v.closeReason ? <p className="alert alert-warning text-sm">{humanize(v.status)}: {v.closeReason}</p> : null}
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Detail label="Hired" value={`${v.hiredCount} of ${v.openings}`} />
            <Detail label="Pay range" value={!v.payVisible ? "Hidden" : v.salaryMin != null || v.salaryMax != null ? `${v.salaryMin != null ? money(v.salaryMin) : "…"} – ${v.salaryMax != null ? money(v.salaryMax) : "…"}` : "—"} />
            <Detail label="Closing date" value={formatDate(v.closingDate)} />
            <Detail label="Open for" value={v.openedAt ? `${v.daysOpen} days` : "Not opened"} />
          </dl>
          {vacancy.description ? <p className="whitespace-pre-line rounded-xl bg-sunken p-3 text-sm text-ink-soft"><strong>Job description:</strong> {vacancy.description}</p> : null}
          {vacancy.requirements ? <p className="whitespace-pre-line rounded-xl bg-sunken p-3 text-sm text-ink-soft"><strong>Requirements:</strong> {vacancy.requirements}</p> : null}

          {v.status === "OPEN" || v.published ? (
            <section className="grid gap-2 rounded-xl border border-line p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-semibold">Careers page</h3>
                  <p className="text-xs text-ink-muted">
                    {v.published
                      ? `Published${v.publishedAt ? ` ${formatDateTime(v.publishedAt)}` : ""}. Anyone can see the title, description and requirements; pay stays private.`
                      : "Not published. Only staff can see this vacancy."}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {v.published ? (
                    <>
                      <a href={`/careers/${v.id}`} target="_blank" rel="noreferrer" className="btn btn-ghost btn-sm">View posting</a>
                      <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => changePublished(false)}>Unpublish</button>
                    </>
                  ) : (
                    <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={() => changePublished(true)}>Publish to careers page</button>
                  )}
                </div>
              </div>
              {!v.published && !vacancy.description && !v.positionId ? (
                <p className="text-xs text-warning-on">Tip: this vacancy has no job description yet.</p>
              ) : null}
            </section>
          ) : null}

          <section className="grid gap-2">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">Pipeline</h3>
              <label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} /> Show hired, rejected and withdrawn</label>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {[...PIPELINE, ...(showClosed ? closedStages : [])].map((stage) => {
                const rows = vacancy.applications.filter((a) => a.stage === stage);
                return (
                  <div key={stage} className="rounded-xl border border-line p-2">
                    <p className="mb-1 flex items-center justify-between text-xs font-semibold">
                      <span className={`badge ${stageTone[stage]}`}>{stageLabel[stage]}</span><span className="text-ink-muted">{rows.length}</span>
                    </p>
                    {rows.length === 0 ? <p className="text-xs text-ink-muted">—</p> : (
                      <ul className="grid gap-1">
                        {rows.map((a) => (
                          <li key={a.id}>
                            <button type="button" className="w-full rounded-lg px-2 py-1 text-left text-sm hover:bg-sunken" onClick={() => setOpenApp(a.id)}>
                              <span className="font-semibold">{a.candidateName}</span>
                              <span className="block text-xs text-ink-muted">
                                {humanize(a.source)}{a.averageScore != null ? ` · score ${a.averageScore}` : ""}
                                {a.nextInterviewAt ? ` · next ${formatDateTime(a.nextInterviewAt)}` : ""}{a.offerStatus ? ` · offer ${a.offerStatus.toLowerCase()}` : ""}
                              </span>
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                );
              })}
            </div>
          </section>

          {v.status === "OPEN" ? <AddApplicant vacancyId={id} onAdded={(appId) => { void load(); onChanged(); setOpenApp(appId); }} /> : null}

          {actions.length ? (
            <section className="grid gap-2">
              <h3 className="text-sm font-semibold">Vacancy</h3>
              {v.published ? <p className="text-xs text-ink-muted">Putting it on hold, closing or cancelling also takes it off the careers page.</p> : null}
              {actions.some((a) => a.needsReason) ? (
                <Field label="Reason (required to close or cancel; open applications are rejected with it)">
                  <input className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
                </Field>
              ) : null}
              <div className="flex flex-wrap gap-2">
                {actions.map((a) => (
                  <button key={a.to} type="button" className={a.to === "OPEN" ? "btn btn-primary btn-sm" : "btn btn-ghost btn-sm"}
                    disabled={busy || (!!a.needsReason && !reason.trim())} onClick={() => changeStatus(a.to)}>{a.label}</button>
                ))}
              </div>
            </section>
          ) : null}
        </div>
      )}
      {openApp ? <ApplicationDrawer id={openApp} onClose={() => setOpenApp(null)} onChanged={() => { void load(); onChanged(); }} /> : null}
    </Drawer>
  );
}

function AddApplicant({ vacancyId, onAdded }: { vacancyId: string; onAdded: (applicationId: string) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [mode, setMode] = useState<"new" | "existing">("new");
  const [q, setQ] = useState("");
  const [matches, setMatches] = useState<CandidateSummary[]>([]);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [source, setSource] = useState<CandidateSource>("WALK_IN");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (mode !== "existing" || q.trim().length < 2) {
      setMatches([]);
      return;
    }
    const t = setTimeout(() => {
      authenticatedFetch((s) => searchCandidates(s, q)).then(setMatches).catch(() => setMatches([]));
    }, 250);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, mode]);

  async function add(body: Parameters<typeof addApplication>[2]) {
    setBusy(true);
    setError("");
    try {
      const app = await authenticatedFetch((s) => addApplication(s, vacancyId, body));
      setFirstName(""); setLastName(""); setPhone(""); setEmail(""); setQ("");
      onAdded(app.id);
    } catch (err) {
      setError(errorMessage(err, "Could not add the applicant"));
    } finally {
      setBusy(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void add({ candidate: { firstName, lastName: lastName || undefined, phone: phone || undefined, email: email || undefined, source } });
  }

  return (
    <section className="grid gap-2 rounded-xl border border-line p-3">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">Add applicant</h3>
        <div className="segmented">
          <button type="button" aria-pressed={mode === "new"} onClick={() => setMode("new")}>New person</button>
          <button type="button" aria-pressed={mode === "existing"} onClick={() => setMode("existing")}>Existing candidate</button>
        </div>
      </div>
      {error ? <p className="alert alert-danger text-sm" role="alert">{error}</p> : null}
      {mode === "new" ? (
        <form className="grid gap-2 sm:grid-cols-2" onSubmit={submit}>
          <Field label="First name"><input className="input" value={firstName} required maxLength={80} onChange={(e) => setFirstName(e.target.value)} /></Field>
          <Field label="Last name"><input className="input" value={lastName} maxLength={80} onChange={(e) => setLastName(e.target.value)} /></Field>
          <Field label="Phone"><input className="input" value={phone} maxLength={20} onChange={(e) => setPhone(e.target.value)} /></Field>
          <Field label="Email"><input type="email" className="input" value={email} maxLength={255} onChange={(e) => setEmail(e.target.value)} /></Field>
          <Field label="Source">
            <select className="select" value={source} onChange={(e) => setSource(e.target.value as CandidateSource)}>
              {CANDIDATE_SOURCES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </select>
          </Field>
          <button type="submit" className="btn btn-secondary btn-sm w-fit self-end" disabled={busy || !firstName || (!phone && !email)}>Add</button>
        </form>
      ) : (
        <div className="grid gap-2">
          <input className="input" placeholder="Search by name, phone or email" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search candidates" />
          <ul className="grid gap-1">
            {matches.map((c) => (
              <li key={c.id} className="flex items-center justify-between gap-2 text-sm">
                <span>{c.fullName} <span className="text-xs text-ink-muted">{c.phone ?? c.email}</span></span>
                <button type="button" className="btn btn-ghost btn-sm" disabled={busy} onClick={() => add({ candidateId: c.id })}>Add</button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
