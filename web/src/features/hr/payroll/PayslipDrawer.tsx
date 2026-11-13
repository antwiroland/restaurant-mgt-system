"use client";

import { useEffect, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Detail, errorMessage, formatDate, formatDateTime } from "@/features/hr/ui";
import { formatMinutes } from "@/features/workforce/time";
import { saveDownloadedFile } from "@/lib/apiClient";
import { downloadPayslipPdf, getPayslip, type PayslipDetail, type PayslipLine } from "@/lib/hrApi";
import { BLOCKING_ISSUES, issueLabel, money } from "./payrollUtils";

/** A payslip with its frozen lines. Used by payroll staff and, once finalized, by the employee. */
export function PayslipDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [slip, setSlip] = useState<PayslipDetail | null>(null);
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState(false);

  async function downloadPdf() {
    setDownloading(true);
    setError("");
    try {
      saveDownloadedFile(await authenticatedFetch((s) => downloadPayslipPdf(s, id)));
    } catch (err) {
      setError(errorMessage(err, "Could not download the payslip"));
    } finally {
      setDownloading(false);
    }
  }

  useEffect(() => {
    authenticatedFetch((s) => getPayslip(s, id)).then(setSlip).catch((err) => setError(errorMessage(err, "Could not load the payslip")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const s = slip?.summary;
  const section = (kind: PayslipLine["kind"]) => (slip?.lines ?? []).filter((l) => l.kind === kind);

  return (
    <Drawer open title={s ? `Payslip · ${s.employeeName ?? ""}` : "Payslip"} onClose={onClose}>
      {!slip || !s ? (error ? <p className="alert alert-danger">{error}</p> : <Skeleton className="h-64 w-full" />) : (
        <div className="grid gap-5">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-semibold">{slip.runNumber}</span>
            <span className="text-ink-muted">{formatDate(slip.periodStart)} – {formatDate(slip.periodEnd)}</span>
            {slip.payDate ? <span className="text-ink-muted">· paid on {formatDate(slip.payDate)}</span> : null}
            {s.paidAt ? <span className="badge badge-success">Paid {humanize(s.paymentMethod ?? "")}{s.paymentReference ? ` · ${s.paymentReference}` : ""}</span> : null}
            <button type="button" className="btn btn-secondary btn-sm ml-auto" onClick={() => void downloadPdf()} disabled={downloading}>
              Download PDF
            </button>
          </div>
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {s.issues.length ? (
            <ul className="grid gap-1">
              {s.issues.map((i) => (
                <li key={i} className={`alert ${BLOCKING_ISSUES.has(i) ? "alert-danger" : "alert-warning"} text-sm`}>{issueLabel[i] ?? humanize(i)}</li>
              ))}
            </ul>
          ) : null}
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Detail label="Employee no." value={s.employeeNumber ?? "—"} />
            <Detail label="Pay type" value={s.salaryType ? `${humanize(s.salaryType)} · ${money(s.rate, slip.currency)}` : "—"} />
            <Detail label="Hours (locked timesheets)" value={formatMinutes(s.paidMinutes)} />
            <Detail label="Approved overtime" value={formatMinutes(s.overtimeMinutes)} />
            <Detail label="SSNIT no." value={slip.ssnitNumber ?? "Not recorded"} />
            <Detail label="TIN / Ghana Card" value={slip.tin ?? "Not recorded"} />
          </dl>

          <LineTable title="Earnings" lines={section("EARNING")} currency={slip.currency} total={s.gross} totalLabel="Gross pay" />
          <LineTable title="Deductions" lines={section("DEDUCTION")} currency={slip.currency} total={s.employeeDeductions} totalLabel="Total deductions" />
          <div className="flex items-center justify-between rounded-xl bg-sunken p-4">
            <span className="font-semibold">Net pay</span>
            <span className="tabular text-xl font-bold">{money(s.net, slip.currency)}</span>
          </div>
          {section("EMPLOYER").length ? (
            <LineTable title="Paid by the employer (not deducted)" lines={section("EMPLOYER")} currency={slip.currency} total={s.employerContributions} totalLabel="Employer total" />
          ) : null}
          {slip.yearToDate ? (
            <section className="grid gap-2">
              <h3 className="text-sm font-semibold">Year to date ({slip.yearToDate.year})</h3>
              <dl className="grid grid-cols-2 gap-4 sm:grid-cols-5">
                <Detail label="Gross" value={money(slip.yearToDate.gross, slip.currency)} />
                <Detail label="Taxable" value={money(slip.yearToDate.taxable, slip.currency)} />
                <Detail label="PAYE" value={money(slip.yearToDate.paye, slip.currency)} />
                <Detail label="SSNIT (you)" value={money(slip.yearToDate.ssnitEmployee, slip.currency)} />
                <Detail label="Net" value={money(slip.yearToDate.net, slip.currency)} />
              </dl>
            </section>
          ) : null}
          <p className="text-xs text-ink-muted">Taxable pay {money(s.taxable, slip.currency)}. {s.paidAt ? `Payment recorded ${formatDateTime(s.paidAt)}.` : ""}</p>
        </div>
      )}
    </Drawer>
  );
}

function LineTable({ title, lines, currency, total, totalLabel }: { title: string; lines: PayslipLine[]; currency: string; total: number; totalLabel: string }) {
  return (
    <section className="grid gap-2">
      <h3 className="text-sm font-semibold">{title}</h3>
      {lines.length === 0 ? <p className="text-sm text-ink-soft">None</p> : (
        <table className="data-table">
          <tbody>
            {lines.map((l, i) => (
              <tr key={`${l.code}-${i}`}>
                <td>
                  {l.description}
                  {l.quantity != null && l.rate != null && l.kind === "EARNING" ? (
                    <p className="text-xs text-ink-muted">{Number(l.quantity).toLocaleString()} × {money(l.rate, currency)}</p>
                  ) : l.rate != null && l.kind !== "EARNING" ? <p className="text-xs text-ink-muted">{Number(l.rate).toLocaleString()}%</p> : null}
                </td>
                <td className="num tabular">{money(l.amount, currency)}</td>
              </tr>
            ))}
            <tr><td className="font-semibold">{totalLabel}</td><td className="num tabular font-semibold">{money(total, currency)}</td></tr>
          </tbody>
        </table>
      )}
    </section>
  );
}
