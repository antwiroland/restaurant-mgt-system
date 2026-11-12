"use client";

import { useCallback, useEffect, useState } from "react";
import { ConfirmModal } from "@/components/ConfirmModal";
import { Drawer } from "@/components/Drawer";
import { EmptyState, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Detail, errorMessage, formatDate } from "@/features/hr/ui";
import { anonymiseCandidate, getCandidate, searchCandidates, type CandidateDetail, type CandidateSummary } from "@/lib/hrApi";
import { stageLabel, stageTone } from "./recruitUtils";

/** Candidate search, each person's applications, and removal of personal data once nothing is open. */
export function CandidatesPanel() {
  const { authenticatedFetch } = useStaffSession();
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<CandidateSummary[]>([]);
  const [open, setOpen] = useState<CandidateDetail | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setRows(await authenticatedFetch((s) => searchCandidates(s, q || undefined)));
    } catch (err) {
      setError(errorMessage(err, "Could not load candidates"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  useEffect(() => {
    const t = setTimeout(() => void load(), 250);
    return () => clearTimeout(t);
  }, [load]);

  async function show(id: string) {
    try {
      setOpen(await authenticatedFetch((s) => getCandidate(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the candidate"));
    }
  }

  async function anonymise() {
    if (!open) return;
    try {
      setOpen(await authenticatedFetch((s) => anonymiseCandidate(s, open.summary.id)));
      void load();
    } catch (err) {
      setError(errorMessage(err, "Could not remove the personal data"));
    } finally {
      setConfirming(false);
    }
  }

  return (
    <section className="panel grid gap-3">
      <input className="input" placeholder="Search by name, phone or email" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search candidates" />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {rows.length === 0 ? <EmptyState compact icon="user" title="No candidates found" /> : (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead><tr><th>Candidate</th><th>Contact</th><th>Source</th><th className="num">Open</th><th>Registered</th></tr></thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id} className="cursor-pointer" onClick={() => show(c.id)}>
                  <td className="font-semibold">{c.fullName}{c.anonymised ? <span className="badge badge-neutral ml-2 text-[10px]">Data removed</span> : null}</td>
                  <td className="text-sm">{c.phone ?? c.email ?? "—"}</td>
                  <td className="text-sm">{humanize(c.source)}</td>
                  <td className="num tabular">{c.openApplications}</td>
                  <td className="text-sm">{formatDate(c.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {open ? (
        <Drawer open title={open.summary.fullName} onClose={() => setOpen(null)}
          footer={!open.summary.anonymised ? (
            <button type="button" className="btn btn-ghost btn-md" disabled={open.summary.openApplications > 0}
              title={open.summary.openApplications > 0 ? "Close every open application first" : undefined} onClick={() => setConfirming(true)}>
              Remove personal data
            </button>
          ) : null}>
          <div className="grid gap-4">
            <dl className="grid grid-cols-2 gap-4">
              <Detail label="Phone" value={open.summary.phone ?? "—"} />
              <Detail label="Email" value={open.summary.email ?? "—"} />
              <Detail label="Source" value={humanize(open.summary.source)} />
              <Detail label="Referred by" value={open.referredByName ?? "—"} />
            </dl>
            {open.notes ? <p className="whitespace-pre-line rounded-xl bg-sunken p-3 text-sm">{open.notes}</p> : null}
            <section className="grid gap-1">
              <h3 className="text-sm font-semibold">Applications</h3>
              {open.applications.map((a) => (
                <p key={a.id} className="flex items-center justify-between gap-2 text-sm">
                  <span>{a.vacancyTitle} <span className="text-xs text-ink-muted">{a.vacancyNumber} · {formatDate(a.appliedAt)}</span></span>
                  <span className={`badge ${stageTone[a.stage]}`}>{stageLabel[a.stage]}</span>
                </p>
              ))}
            </section>
            <p className="text-xs text-ink-muted">{open.documents.length} document(s) on file. Removing personal data deletes the name, contact details, notes and documents; the application history stays for statistics.</p>
          </div>
        </Drawer>
      ) : null}
      <ConfirmModal open={confirming} title="Remove personal data?" message="This permanently deletes the candidate's name, contact details, notes and documents."
        confirmLabel="Remove" danger onConfirm={anonymise} onClose={() => setConfirming(false)} />
    </section>
  );
}
