"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Detail, Field, errorMessage, formatDate, formatDateTime } from "@/features/hr/ui";
import { money } from "@/features/hr/payroll/payrollUtils";
import { getSchedulableStaff, saveDownloadedFile } from "@/lib/apiClient";
import {
  CONTRACT_TYPES,
  SALARY_TYPES,
  downloadCandidateDocument,
  getApplication,
  hireApplicant,
  moveApplication,
  recordInterview,
  respondToOffer,
  scheduleInterview,
  sendOffer,
  uploadCandidateDocument,
  type ApplicationDetail,
  type ApplicationStage,
  type ContractType,
  type InterviewKind,
  type InterviewOutcome,
  type SalaryType,
} from "@/lib/hrApi";
import { kindLabel, nextStages, outcomeTone, stageLabel, stageTone } from "./recruitUtils";

type StaffOption = { id: string; name: string };

/** One application: candidate, documents, stage moves, interviews, offer and hire. */
export function ApplicationDrawer({ id, onClose, onChanged }: { id: string; onClose: () => void; onChanged: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [app, setApp] = useState<ApplicationDetail | null>(null);
  const [staff, setStaff] = useState<StaffOption[]>([]);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setApp(await authenticatedFetch((s) => getApplication(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the application"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    authenticatedFetch((s) => getSchedulableStaff(s))
      .then((list) => setStaff(list.map((u) => ({ id: u.id, name: u.name }))))
      .catch(() => setStaff([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function run(action: () => Promise<ApplicationDetail>, done: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      setApp(await action());
      setNote("");
      setNotice(done);
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not update the application"));
    } finally {
      setBusy(false);
    }
  }

  const move = (stage: ApplicationStage) =>
    run(() => authenticatedFetch((s) => moveApplication(s, id, { stage, note: note || undefined })), `Moved to ${stageLabel[stage].toLowerCase()}.`);

  async function hire() {
    setBusy(true);
    setError("");
    try {
      const result = await authenticatedFetch((s) => hireApplicant(s, id));
      setNotice(`Hired as ${result.employeeNumber}.${result.documentsCopied ? ` ${result.documentsCopied} document(s) copied.` : ""}${result.contractNote ? ` ${result.contractNote}.` : " Draft contract created."}`);
      setApp(await authenticatedFetch((s) => getApplication(s, id)));
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Could not complete the hire"));
    } finally {
      setBusy(false);
    }
  }

  const a = app;
  const open = a && !["HIRED", "REJECTED", "WITHDRAWN"].includes(a.stage);

  return (
    <Drawer open title={a ? `${a.candidate.fullName} · ${a.vacancy.title}` : "Application"} onClose={onClose}>
      {!a ? (error ? <p className="alert alert-danger">{error}</p> : <Skeleton className="h-64 w-full" />) : (
        <div className="grid gap-5">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {notice ? <p className="alert alert-info" role="status">{notice}</p> : null}
          <div className="flex flex-wrap items-center gap-2">
            <span className={`badge badge-dot ${stageTone[a.stage]}`}>{stageLabel[a.stage]}</span>
            <span className="text-sm text-ink-muted">{a.vacancy.vacancyNumber} · {a.vacancy.branchName} · applied {formatDate(a.appliedAt)}</span>
          </div>
          {a.rejectReason ? <p className="alert alert-warning text-sm">Rejected: {a.rejectReason}</p> : null}
          {a.employeeId ? <p className="alert alert-success text-sm">Hired {formatDateTime(a.hiredAt)}. <Link className="underline" href={`/hr/employees/${a.employeeId}`}>Open the employee record</Link></p> : null}

          <dl className="grid grid-cols-2 gap-4">
            <Detail label="Phone" value={a.candidate.phone ?? "—"} />
            <Detail label="Email" value={a.candidate.email ?? "—"} />
            <Detail label="Source" value={humanize(a.candidate.source)} />
            <Detail label="Other applications" value={Math.max(0, a.candidate.applications - 1)} />
          </dl>
          {a.candidateNotes ? <p className="whitespace-pre-line rounded-xl bg-sunken p-3 text-sm text-ink-soft">{a.candidateNotes}</p> : null}

          <Documents app={a} onChanged={(d) => setApp({ ...a, documents: d })} />

          {open ? (
            <section className="grid gap-2">
              <h3 className="text-sm font-semibold">Move</h3>
              <Field label="Note (required to reject)"><input className="input" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} /></Field>
              <div className="flex flex-wrap gap-2">
                {nextStages(a.stage).map((s) => (
                  <button key={s} type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => move(s)}>{stageLabel[s]}</button>
                ))}
                <button type="button" className="btn btn-ghost btn-sm" disabled={busy || !note.trim()} onClick={() => move("REJECTED")}>Reject</button>
                <button type="button" className="btn btn-ghost btn-sm" disabled={busy} onClick={() => move("WITHDRAWN")}>Candidate withdrew</button>
              </div>
            </section>
          ) : a.stage === "REJECTED" || a.stage === "WITHDRAWN" ? (
            <button type="button" className="btn btn-ghost btn-sm w-fit" disabled={busy} onClick={() => move("SCREENING")}>Reconsider (back to screening)</button>
          ) : null}

          <Interviews app={a} staff={staff} busy={busy}
            onSchedule={(body) => run(() => authenticatedFetch((s) => scheduleInterview(s, id, body)), "Scheduled; the interviewer has been notified.")}
            onRecorded={() => void load()} />

          {a.stage === "OFFER" || a.offer ? (
            <OfferSection app={a} busy={busy}
              onSend={(body) => run(() => authenticatedFetch((s) => sendOffer(s, id, body)), "Offer recorded as sent.")}
              onRespond={(accepted) => run(() => authenticatedFetch((s) => respondToOffer(s, id, { accepted })), accepted ? "Offer accepted." : "Offer declined.")}
              onHire={hire} />
          ) : null}

          <section className="grid gap-2">
            <h3 className="text-sm font-semibold">History</h3>
            <ol className="grid gap-1 text-sm">
              {a.events.map((e, i) => (
                <li key={i} className="flex flex-wrap justify-between gap-2 border-b border-line-subtle py-1">
                  <span>{e.fromStage && e.fromStage !== e.toStage ? `${stageLabel[e.fromStage]} → ` : ""}{stageLabel[e.toStage]}{e.note ? ` — ${e.note}` : ""}</span>
                  <span className="text-xs text-ink-muted">{e.actorName ?? ""} · {formatDateTime(e.at)}</span>
                </li>
              ))}
            </ol>
          </section>
        </div>
      )}
    </Drawer>
  );
}

function Documents({ app, onChanged }: { app: ApplicationDetail; onChanged: (docs: ApplicationDetail["documents"]) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function upload() {
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      const detail = await authenticatedFetch((s) => uploadCandidateDocument(s, app.candidate.id, file, file.name.replace(/\.[^.]+$/, "") || "CV"));
      onChanged(detail.documents);
      setFile(null);
    } catch (err) {
      setError(errorMessage(err, "Upload failed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="grid gap-2">
      <h3 className="text-sm font-semibold">Documents</h3>
      {error ? <p className="alert alert-danger text-sm">{error}</p> : null}
      {app.documents.length === 0 ? <p className="text-sm text-ink-soft">No CV or certificates yet.</p> : (
        <ul className="grid gap-1 text-sm">
          {app.documents.map((d) => (
            <li key={d.id} className="flex items-center justify-between gap-2">
              <span>{d.title} <span className="text-xs text-ink-muted">({Math.round(d.sizeBytes / 1024)} KB)</span></span>
              <button type="button" className="btn btn-ghost btn-sm" onClick={async () => {
                try {
                  saveDownloadedFile(await authenticatedFetch((s) => downloadCandidateDocument(s, d.id, d.fileName)));
                } catch (err) {
                  setError(errorMessage(err, "Download failed"));
                }
              }}>Download</button>
            </li>
          ))}
        </ul>
      )}
      {!app.candidate.anonymised ? (
        <div className="flex flex-wrap items-center gap-2">
          <input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx" aria-label="Candidate document" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <button type="button" className="btn btn-ghost btn-sm" disabled={!file || busy} onClick={upload}>Upload</button>
        </div>
      ) : null}
    </section>
  );
}

function Interviews({ app, staff, busy, onSchedule, onRecorded }: {
  app: ApplicationDetail; staff: StaffOption[]; busy: boolean;
  onSchedule: (body: { kind: InterviewKind; scheduledAt: string; interviewerId?: string | null; location?: string }) => void;
  onRecorded: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const [kind, setKind] = useState<InterviewKind>("IN_PERSON");
  const [when, setWhen] = useState("");
  const [interviewerId, setInterviewerId] = useState("");
  const [location, setLocation] = useState("");
  const [recording, setRecording] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<InterviewOutcome>("PASSED");
  const [score, setScore] = useState("3");
  const [feedback, setFeedback] = useState("");
  const [error, setError] = useState("");
  const open = !["HIRED", "REJECTED", "WITHDRAWN"].includes(app.stage);

  function schedule(e: FormEvent) {
    e.preventDefault();
    onSchedule({ kind, scheduledAt: new Date(when).toISOString(), interviewerId: interviewerId || null, location: location || undefined });
  }

  async function record(interviewId: string) {
    setError("");
    try {
      await authenticatedFetch((s) => recordInterview(s, interviewId, {
        outcome, score: outcome === "PASSED" || outcome === "FAILED" ? Number(score) : null, feedback: feedback || undefined,
      }));
      setRecording(null);
      setFeedback("");
      onRecorded();
    } catch (err) {
      setError(errorMessage(err, "Could not record the result"));
    }
  }

  return (
    <section className="grid gap-2">
      <h3 className="text-sm font-semibold">Interviews &amp; trial shifts</h3>
      {error ? <p className="alert alert-danger text-sm">{error}</p> : null}
      {app.interviews.length === 0 ? <p className="text-sm text-ink-soft">None yet.</p> : (
        <ul className="grid gap-2">
          {app.interviews.map((i) => (
            <li key={i.id} className="grid gap-1 rounded-xl border border-line p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-semibold">{kindLabel[i.kind]} · {formatDateTime(i.scheduledAt)}</span>
                <span className={`badge ${outcomeTone[i.outcome]}`}>{humanize(i.outcome)}{i.score ? ` · ${i.score}/5` : ""}</span>
              </div>
              <span className="text-ink-muted">{i.interviewerName ?? "No interviewer"}{i.location ? ` · ${i.location}` : ""}</span>
              {i.feedback ? <p className="text-ink-soft">{i.feedback}</p> : null}
              {i.outcome === "SCHEDULED" ? (recording === i.id ? (
                <div className="grid gap-2 sm:grid-cols-3">
                  <select className="select" aria-label="Outcome" value={outcome} onChange={(e) => setOutcome(e.target.value as InterviewOutcome)}>
                    {(["PASSED", "FAILED", "NO_SHOW", "CANCELLED"] as InterviewOutcome[]).map((o) => <option key={o} value={o}>{humanize(o)}</option>)}
                  </select>
                  {outcome === "PASSED" || outcome === "FAILED" ? (
                    <select className="select" aria-label="Score" value={score} onChange={(e) => setScore(e.target.value)}>
                      {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n} / 5</option>)}
                    </select>
                  ) : <span />}
                  <input className="input" placeholder="Feedback" value={feedback} maxLength={2000} onChange={(e) => setFeedback(e.target.value)} />
                  <button type="button" className="btn btn-primary btn-sm w-fit" onClick={() => record(i.id)}>Save result</button>
                </div>
              ) : <button type="button" className="btn btn-ghost btn-sm w-fit" onClick={() => setRecording(i.id)}>Record result</button>) : null}
            </li>
          ))}
        </ul>
      )}
      {open ? (
        <form className="grid gap-2 rounded-xl border border-line p-3 sm:grid-cols-2" onSubmit={schedule}>
          <Field label="Type">
            <select className="select" value={kind} onChange={(e) => setKind(e.target.value as InterviewKind)}>
              {(Object.keys(kindLabel) as InterviewKind[]).map((k) => <option key={k} value={k}>{kindLabel[k]}</option>)}
            </select>
          </Field>
          <Field label="When"><input type="datetime-local" className="input" value={when} required onChange={(e) => setWhen(e.target.value)} /></Field>
          <Field label="Interviewer">
            <select className="select" value={interviewerId} onChange={(e) => setInterviewerId(e.target.value)}>
              <option value="">—</option>
              {staff.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
          </Field>
          <Field label="Where (optional)"><input className="input" value={location} maxLength={200} onChange={(e) => setLocation(e.target.value)} /></Field>
          <button type="submit" className="btn btn-secondary btn-sm w-fit" disabled={busy || !when}>Schedule</button>
        </form>
      ) : null}
    </section>
  );
}

function OfferSection({ app, busy, onSend, onRespond, onHire }: {
  app: ApplicationDetail; busy: boolean;
  onSend: (body: { contractType?: ContractType | null; salaryType?: SalaryType | null; salaryAmount?: number | null; weeklyHours?: number | null; startDate: string }) => void;
  onRespond: (accepted: boolean) => void;
  onHire: () => void;
}) {
  const o = app.offer;
  const [contractType, setContractType] = useState<ContractType | "">(o?.contractType ?? "PERMANENT");
  const [salaryType, setSalaryType] = useState<SalaryType | "">(o?.salaryType ?? "");
  const [salaryAmount, setSalaryAmount] = useState(o?.salaryAmount != null ? String(o.salaryAmount) : "");
  const [weeklyHours, setWeeklyHours] = useState(o?.weeklyHours != null ? String(o.weeklyHours) : "");
  const [startDate, setStartDate] = useState(o?.startDate ?? "");
  const editable = app.stage === "OFFER" && o?.status !== "ACCEPTED";

  return (
    <section className="grid gap-2 rounded-xl border border-line p-3">
      <h3 className="text-sm font-semibold">Offer</h3>
      {o ? (
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <Detail label="Status" value={o.status ? humanize(o.status) : "Not sent"} />
          <Detail label="Start date" value={formatDate(o.startDate)} />
          <Detail label="Contract" value={o.contractType ? humanize(o.contractType) : "—"} />
          <Detail label="Pay" value={o.payHidden ? "Hidden (no pay permission)" : o.salaryAmount != null ? `${money(o.salaryAmount)} ${humanize(o.salaryType ?? "")}` : "Not offered"} />
        </dl>
      ) : null}
      {editable ? (
        <form className="grid gap-2 sm:grid-cols-2" onSubmit={(e) => {
          e.preventDefault();
          onSend({
            contractType: contractType || null, salaryType: app.payVisible && salaryType ? salaryType : null,
            salaryAmount: app.payVisible && salaryAmount ? Number(salaryAmount) : null, weeklyHours: weeklyHours ? Number(weeklyHours) : null, startDate,
          });
        }}>
          <Field label="Contract">
            <select className="select" value={contractType} onChange={(e) => setContractType(e.target.value as ContractType | "")}>
              <option value="">—</option>
              {CONTRACT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
            </select>
          </Field>
          <Field label="Start date"><input type="date" className="input" value={startDate} required onChange={(e) => setStartDate(e.target.value)} /></Field>
          {app.payVisible ? (
            <>
              <Field label="Pay basis">
                <select className="select" value={salaryType} onChange={(e) => setSalaryType(e.target.value as SalaryType | "")}>
                  <option value="">—</option>
                  {SALARY_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
                </select>
              </Field>
              <Field label="Amount"><input className="input tabular" inputMode="decimal" value={salaryAmount} onChange={(e) => setSalaryAmount(e.target.value.replace(/[^0-9.]/g, ""))} /></Field>
            </>
          ) : null}
          <Field label="Weekly hours (optional)"><input className="input tabular" inputMode="decimal" value={weeklyHours} onChange={(e) => setWeeklyHours(e.target.value.replace(/[^0-9.]/g, ""))} /></Field>
          <button type="submit" className="btn btn-secondary btn-sm w-fit self-end" disabled={busy || !startDate}>{o?.status ? "Update offer" : "Record offer sent"}</button>
        </form>
      ) : null}
      {app.stage === "OFFER" && o?.status === "SENT" ? (
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn btn-success btn-sm" disabled={busy} onClick={() => onRespond(true)}>Candidate accepted</button>
          <button type="button" className="btn btn-ghost btn-sm" disabled={busy} onClick={() => onRespond(false)}>Candidate declined</button>
        </div>
      ) : null}
      {app.stage === "OFFER" && o?.status === "ACCEPTED" ? (
        <div className="grid gap-1">
          <button type="button" className="btn btn-primary btn-md w-fit" disabled={busy || !app.canHire} onClick={onHire}>Hire — create employee record</button>
          {!app.canHire ? <p className="text-xs text-ink-muted">You need permission to register employees.</p> : null}
          {!app.canCreateContract ? <p className="text-xs text-ink-muted">The contract will need to be created by someone who manages contracts.</p> : null}
        </div>
      ) : null}
    </section>
  );
}
