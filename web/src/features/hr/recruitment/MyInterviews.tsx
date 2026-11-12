"use client";

import { useCallback, useEffect, useState } from "react";
import { SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { errorMessage, formatDateTime } from "@/features/hr/ui";
import { getMyInterviews, recordInterview, type InterviewOutcome, type MyInterview } from "@/lib/hrApi";
import { kindLabel, outcomeTone } from "./recruitUtils";

/** Interviews and trial shifts the signed-in person runs. Shown only when there are any. */
export function MyInterviews() {
  const { authenticatedFetch } = useStaffSession();
  const [rows, setRows] = useState<MyInterview[]>([]);
  const [editing, setEditing] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<InterviewOutcome>("PASSED");
  const [score, setScore] = useState("3");
  const [feedback, setFeedback] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setRows(await authenticatedFetch((s) => getMyInterviews(s)));
    } catch {
      setRows([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function save(id: string) {
    setError("");
    try {
      await authenticatedFetch((s) => recordInterview(s, id, {
        outcome, score: outcome === "PASSED" || outcome === "FAILED" ? Number(score) : null, feedback: feedback || undefined,
      }));
      setEditing(null);
      setFeedback("");
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not save the result"));
    }
  }

  if (rows.length === 0) return null;
  return (
    <section className="panel grid gap-3">
      <SectionHeader title="Interviews I run" description="Record how each interview or trial shift went; HR sees your score and notes." />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <ul className="grid gap-2">
        {rows.map((i) => (
          <li key={i.id} className="grid gap-1 rounded-xl border border-line p-3 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-semibold">{i.candidateName} · {i.vacancyTitle}</span>
              <span className={`badge ${outcomeTone[i.outcome]}`}>{humanize(i.outcome)}{i.score ? ` · ${i.score}/5` : ""}</span>
            </div>
            <span className="text-ink-muted">{kindLabel[i.kind]} · {formatDateTime(i.scheduledAt)}{i.location ? ` · ${i.location}` : ""}</span>
            {i.outcome === "SCHEDULED" ? (editing === i.id ? (
              <div className="grid gap-2 sm:grid-cols-3">
                <select className="select" aria-label="Outcome" value={outcome} onChange={(e) => setOutcome(e.target.value as InterviewOutcome)}>
                  {(["PASSED", "FAILED", "NO_SHOW"] as InterviewOutcome[]).map((o) => <option key={o} value={o}>{humanize(o)}</option>)}
                </select>
                {outcome !== "NO_SHOW" ? (
                  <select className="select" aria-label="Score" value={score} onChange={(e) => setScore(e.target.value)}>
                    {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n} / 5</option>)}
                  </select>
                ) : <span />}
                <input className="input" placeholder="Notes" value={feedback} maxLength={2000} onChange={(e) => setFeedback(e.target.value)} />
                <button type="button" className="btn btn-primary btn-sm w-fit" onClick={() => save(i.id)}>Save</button>
              </div>
            ) : <button type="button" className="btn btn-ghost btn-sm w-fit" onClick={() => setEditing(i.id)}>Record result</button>) : null}
          </li>
        ))}
      </ul>
    </section>
  );
}
