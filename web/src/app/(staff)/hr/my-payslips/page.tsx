"use client";

import { useEffect, useState } from "react";
import { EmptyState, PageHeader, SectionHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { HrGuard, errorMessage, formatDate } from "@/features/hr/ui";
import { PayslipDrawer } from "@/features/hr/payroll/PayslipDrawer";
import { money } from "@/features/hr/payroll/payrollUtils";
import { sourceLabel } from "@/features/hr/tips/tipUtils";
import { formatMinutes } from "@/features/workforce/time";
import { getMyPayslips, getMyTips, type MyPayslip, type MyTipShare } from "@/lib/hrApi";

export default function MyPayslipsPage() {
  return (
    <HrGuard anyOf={["self"]} title="No employee record linked">
      <MyPayslips />
    </HrGuard>
  );
}

function MyPayslips() {
  const { authenticatedFetch } = useStaffSession();
  const [rows, setRows] = useState<MyPayslip[] | null>(null);
  const [tips, setTips] = useState<MyTipShare[]>([]);
  const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    authenticatedFetch((s) => getMyPayslips(s)).then(setRows).catch((err) => setError(errorMessage(err, "Could not load your payslips")));
    authenticatedFetch((s) => getMyTips(s)).then(setTips).catch(() => setTips([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="grid gap-5">
      <PageHeader eyebrow="My HR" title="My payslips" icon="receipt" description="Payslips appear here once payroll has been finalized, with your tips and service charge shares." />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <section className="panel">
        {rows && rows.length === 0 ? <EmptyState compact icon="receipt" title="No payslips yet" /> : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Period</th><th className="num">Gross</th><th className="num">Deductions</th><th className="num">Net pay</th><th>Status</th></tr></thead>
              <tbody>
                {(rows ?? []).map((p) => (
                  <tr key={p.id} className="cursor-pointer" onClick={() => setOpen(p.id)}>
                    <td>{formatDate(p.periodStart)} – {formatDate(p.periodEnd)}<p className="text-xs text-ink-muted">{p.runNumber}</p></td>
                    <td className="num tabular">{money(p.gross, p.currency)}</td>
                    <td className="num tabular">{money(p.deductions, p.currency)}</td>
                    <td className="num tabular font-semibold">{money(p.net, p.currency)}</td>
                    <td>{p.paidAt ? <span className="badge badge-success">Paid</span> : <span className="badge badge-gold">Payment pending</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {tips.length ? (
        <section className="panel grid gap-3">
          <SectionHeader title="My tips & service charge" description="Approved shares. They are added to your next payslip." />
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Pool</th><th>Period</th><th className="num">Time worked</th><th className="num">Share</th><th>Status</th></tr></thead>
              <tbody>
                {tips.map((t) => (
                  <tr key={t.id}>
                    <td>{sourceLabel[t.source]}<p className="text-xs text-ink-muted">{t.poolNumber} · {t.branchName}</p></td>
                    <td>{formatDate(t.periodStart)} – {formatDate(t.periodEnd)}</td>
                    <td className="num tabular">{formatMinutes(t.minutes)}</td>
                    <td className="num tabular font-semibold">{money(t.amount)}</td>
                    <td>{t.status === "PAID" ? <span className="badge badge-success">Paid</span> : t.inPayroll ? <span className="badge badge-gold">In payroll</span> : <span className="badge badge-pending">Approved</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}
      {open ? <PayslipDrawer id={open} onClose={() => setOpen(null)} /> : null}
    </div>
  );
}
