"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import {
  downloadReport,
  getBranches,
  saveDownloadedFile,
  type BranchRecord,
  type ReportFormat,
  type ReportKind,
} from "@/lib/apiClient";
import { addDays, isoDate } from "@/features/workforce/time";

type ReportDef = {
  kind: ReportKind;
  title: string;
  description: string;
  range: "period" | "day";
  branch: boolean;
};

const REPORTS: ReportDef[] = [
  { kind: "sales", title: "Sales", description: "Every order in the period with discounts, service charge, tax, tip, payments and refunds.", range: "period", branch: true },
  { kind: "reconciliation", title: "Daily reconciliation", description: "Gross and net sales, refunds, voids, totals per payment method, expected cash and sign-off.", range: "day", branch: true },
  { kind: "audit", title: "Audit trail", description: "Logins, PIN events, role changes and all financial actions.", range: "period", branch: false },
  { kind: "timesheet", title: "Timesheets", description: "Hours worked against the rota, overtime, late arrivals and missed shifts per staff member.", range: "period", branch: false },
];

export default function ReportsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [from, setFrom] = useState(() => isoDate(addDays(new Date(), -7)));
  const [to, setTo] = useState(() => isoDate(new Date()));
  const [day, setDay] = useState(() => isoDate(new Date()));
  const [branchId, setBranchId] = useState("");
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!session) return;
    authenticatedFetch((s) => getBranches(s))
      .then(setBranches)
      .catch(() => setBranches([]));
  }, [authenticatedFetch, session]);

  async function download(report: ReportDef, format: ReportFormat) {
    setBusy(`${report.kind}-${format}`);
    setError("");
    setMessage("");
    try {
      const file = await authenticatedFetch((s) =>
        downloadReport(s, report.kind, {
          format,
          from: report.range === "period" ? from : undefined,
          to: report.range === "period" ? to : undefined,
          date: report.range === "day" ? day : undefined,
          branchId: report.branch && branchId ? branchId : undefined,
        }),
      );
      saveDownloadedFile(file);
      setMessage(`Downloaded ${file.filename}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not download the report");
    } finally {
      setBusy(null);
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-48 w-full" /></section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Reports</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4">
      <section className="panel grid gap-3">
        <div>
          <p className="kicker">Reports</p>
          <h1 className="text-2xl font-semibold">Export reports</h1>
          <p className="text-sm text-ink-soft">Download as CSV for spreadsheets and accounting software, or as PDF to print and file.</p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <label className="grid gap-1 text-sm">
            <span>From</span>
            <input className="input" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="grid gap-1 text-sm">
            <span>To</span>
            <input className="input" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </label>
          <label className="grid gap-1 text-sm">
            <span>Reconciliation day</span>
            <input className="input" type="date" value={day} onChange={(e) => setDay(e.target.value)} />
          </label>
          {branches.length > 0 ? (
            <label className="grid gap-1 text-sm">
              <span>Branch</span>
              <select className="select" value={branchId} onChange={(e) => setBranchId(e.target.value)}>
                <option value="">All branches</option>
                {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
              </select>
            </label>
          ) : null}
        </div>
        <div aria-live="polite">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {message ? <p className="alert alert-success">{message}</p> : null}
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        {REPORTS.map((report) => (
          <article key={report.kind} className="panel grid content-between gap-3">
            <div>
              <h2 className="text-lg font-semibold">{report.title}</h2>
              <p className="text-sm text-ink-soft">{report.description}</p>
              <p className="mt-1 text-xs text-ink-soft">
                {report.range === "day" ? `For ${day}` : `${from} to ${to}`}
                {report.branch && branchId ? ` · ${branches.find((b) => b.id === branchId)?.name ?? ""}` : ""}
              </p>
            </div>
            <div className="flex gap-2">
              {(["CSV", "PDF"] as const).map((format) => (
                <button
                  key={format}
                  type="button"
                  className="btn btn-secondary"
                  disabled={busy !== null}
                  onClick={() => void download(report, format)}
                  aria-label={`Download ${report.title} as ${format}`}
                >
                  {busy === `${report.kind}-${format}` ? "Preparing…" : format}
                </button>
              ))}
            </div>
          </article>
        ))}
      </section>
    </main>
  );
}
