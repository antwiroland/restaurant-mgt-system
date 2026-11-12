"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Field, HrGuard, errorMessage, formatDate, todayIso } from "@/features/hr/ui";
import { getBranches, saveDownloadedFile, type BranchRecord } from "@/lib/apiClient";
import { downloadHrReport, getHrReports, runHrReport, type HrReport, type HrReportInfo, type HrReportQuery, type HrReportType } from "@/lib/hrApi";

export default function HrReportsPage() {
  return (
    <HrGuard anyOf={["hr.reports.view"]} title="HR reports access required">
      <Reports />
    </HrGuard>
  );
}

/** Columns holding codes (status, types) are shown in words; dates as dates. */
const CODE_COLUMNS = new Set(["status", "employmentType", "event"]);
const DATE_COLUMNS = new Set(["hireDate", "exitDate", "date", "startDate", "endDate", "periodStart", "periodEnd", "payDate"]);
const NUMBER_COLUMNS = new Set(["days", "scheduledHours", "paidHours", "overtimeHours", "payableOvertimeHours", "lateCount", "absences",
  "employees", "gross", "deductions", "net", "employerCost"]);

function display(key: string, value: string | number | null): string {
  if (value == null || value === "") return "—";
  if (DATE_COLUMNS.has(key)) return formatDate(String(value));
  if (CODE_COLUMNS.has(key)) return humanize(String(value));
  return String(value);
}

const firstOfMonth = () => `${todayIso().slice(0, 8)}01`;

function Reports() {
  const { authenticatedFetch } = useStaffSession();
  const [available, setAvailable] = useState<HrReportInfo[] | null>(null);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [type, setType] = useState<HrReportType | "">("");
  const [from, setFrom] = useState(firstOfMonth());
  const [to, setTo] = useState(todayIso());
  const [branchId, setBranchId] = useState("");
  const [includeExited, setIncludeExited] = useState(false);
  const [report, setReport] = useState<HrReport | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      const list = await authenticatedFetch((s) => getHrReports(s));
      setAvailable(list);
      setType((current) => current || list[0]?.type || "");
    } catch (err) {
      setError(errorMessage(err, "Could not load the reports"));
    }
    authenticatedFetch((s) => getBranches(s)).then(setBranches).catch(() => setBranches([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  const info = available?.find((r) => r.type === type);
  const query = (): HrReportQuery => ({
    from: info?.periodic ? from : undefined,
    to: info?.periodic ? to : undefined,
    branchId: branchId || undefined,
    includeExited: type === "EMPLOYEES" ? includeExited : undefined,
  });

  async function run(e?: FormEvent) {
    e?.preventDefault();
    if (!type) return;
    setBusy(true);
    setError("");
    try {
      setReport(await authenticatedFetch((s) => runHrReport(s, type, query())));
    } catch (err) {
      setReport(null);
      setError(errorMessage(err, "Could not run the report"));
    } finally {
      setBusy(false);
    }
  }

  async function download() {
    if (!type) return;
    setBusy(true);
    setError("");
    try {
      saveDownloadedFile(await authenticatedFetch((s) => downloadHrReport(s, type, query())));
    } catch (err) {
      setError(errorMessage(err, "Could not download the report"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-6">
      <PageHeader eyebrow="Human Resources" title="HR Reports" icon="download"
        description="Registers and summaries for your branches. View them here or download a CSV for a spreadsheet. Downloads are recorded in the audit log." />
      {error ? <ErrorState message={error} onRetry={report ? () => run() : load} /> : null}
      {!available && !error ? <Skeleton className="h-40 w-full" /> : null}
      {available && available.length === 0 ? (
        <EmptyState icon="shield" title="No reports available" description="Your account can see the dashboard but none of the data behind these reports." />
      ) : null}

      {available && available.length > 0 ? (
        <form className="panel grid gap-4" onSubmit={run} aria-label="Report options">
          <fieldset className="grid gap-2">
            <legend className="text-sm font-semibold">Report</legend>
            <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              {available.map((r) => (
                <label key={r.type} className={`flex cursor-pointer gap-3 rounded-xl border p-3 ${type === r.type ? "border-brand bg-brand-subtle" : "border-line"}`}>
                  <input type="radio" name="report" value={r.type} checked={type === r.type} className="mt-1"
                    onChange={() => { setType(r.type); setReport(null); }} />
                  <span>
                    <span className="block font-semibold text-ink">{r.title}</span>
                    <span className="block text-xs text-ink-muted">{r.description}</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
          <div className="flex flex-wrap items-end gap-3">
            {info?.periodic ? (
              <>
                <Field label="From"><input type="date" className="input" value={from} max={to} required onChange={(e) => setFrom(e.target.value)} /></Field>
                <Field label="To"><input type="date" className="input" value={to} min={from} required onChange={(e) => setTo(e.target.value)} /></Field>
              </>
            ) : null}
            {branches.length > 1 ? (
              <Field label="Branch">
                <select className="select" value={branchId} onChange={(e) => setBranchId(e.target.value)}>
                  <option value="">All my branches</option>
                  {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select>
              </Field>
            ) : null}
            {type === "EMPLOYEES" ? (
              <label className="flex items-center gap-2 pb-2 text-sm">
                <input type="checkbox" checked={includeExited} onChange={(e) => setIncludeExited(e.target.checked)} /> Include people who have left
              </label>
            ) : null}
            <div className="ml-auto flex gap-2">
              <button type="submit" className="btn btn-primary btn-md" disabled={busy || !type}>View report</button>
              <button type="button" className="btn btn-secondary btn-md" disabled={busy || !type} onClick={download}>
                <Icon name="download" size={16} /> Download CSV
              </button>
            </div>
          </div>
        </form>
      ) : null}

      {report ? (
        <section className="panel grid gap-3" aria-label={report.title}>
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-sm font-semibold">{report.title}{report.branchName ? ` · ${report.branchName}` : ""}</h2>
            <p className="text-xs text-ink-muted">
              {report.from && report.to ? `${formatDate(report.from)} – ${formatDate(report.to)} · ` : ""}{report.rows.length} row{report.rows.length === 1 ? "" : "s"}
            </p>
          </div>
          {report.rows.length === 0 ? <p className="text-sm text-ink-muted">Nothing for this selection.</p> : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead><tr>{report.columns.map((c) => <th key={c.key} className={NUMBER_COLUMNS.has(c.key) ? "num" : undefined}>{c.label}</th>)}</tr></thead>
                <tbody>
                  {report.rows.map((row, i) => (
                    <tr key={i}>
                      {report.columns.map((c) => (
                        <td key={c.key} className={NUMBER_COLUMNS.has(c.key) ? "num tabular" : "text-sm"}>{display(c.key, row[c.key])}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </div>
  );
}
