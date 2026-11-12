"use client";

import { useEffect, useState } from "react";
import { EmptyState, PageHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { HrGuard, errorMessage, formatDate } from "@/features/hr/ui";
import { PayslipDrawer } from "@/features/hr/payroll/PayslipDrawer";
import { money } from "@/features/hr/payroll/payrollUtils";
import { getMyPayslips, type MyPayslip } from "@/lib/hrApi";

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
  const [open, setOpen] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    authenticatedFetch((s) => getMyPayslips(s)).then(setRows).catch((err) => setError(errorMessage(err, "Could not load your payslips")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="grid gap-5">
      <PageHeader eyebrow="My HR" title="My payslips" icon="receipt" description="Payslips appear here once payroll has been finalized." />
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
      {open ? <PayslipDrawer id={open} onClose={() => setOpen(null)} /> : null}
    </div>
  );
}
