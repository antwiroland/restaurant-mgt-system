"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { errorMessage } from "@/features/hr/ui";
import { saveDownloadedFile } from "@/lib/apiClient";
import { downloadPaymentFile, getPaymentFiles, type PaymentFileSummary } from "@/lib/hrApi";
import { money } from "./payrollUtils";

const LABEL: Record<string, string> = { BANK: "Bank transfer", MOBILE_MONEY: "Mobile Money", CASH: "Cash" };

/**
 * The files a finalized run is paid with: one CSV per method for the bank's or Mobile Money provider's bulk-payment
 * upload, plus who cannot be paid yet because their payout details are missing. Downloads are audited.
 */
export function PaymentFilesPanel({ runId, refreshKey }: { runId: string; refreshKey: number }) {
  const { authenticatedFetch } = useStaffSession();
  const [summary, setSummary] = useState<PaymentFileSummary | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    authenticatedFetch((s) => getPaymentFiles(s, runId)).then(setSummary).catch((err) => setError(errorMessage(err, "Could not load payment files")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId, refreshKey]);

  async function download(method: "BANK" | "MOBILE_MONEY") {
    setBusy(method);
    setError("");
    try {
      saveDownloadedFile(await authenticatedFetch((s) => downloadPaymentFile(s, runId, method)));
    } catch (err) {
      setError(errorMessage(err, "Could not create the payment file"));
    } finally {
      setBusy(null);
    }
  }

  if (!summary) return error ? <p className="alert alert-danger" role="alert">{error}</p> : null;
  const files = summary.groups.filter((g) => g.method === "BANK" || g.method === "MOBILE_MONEY");
  const cash = summary.groups.find((g) => g.method === "CASH");

  return (
    <section className="grid gap-3 rounded-xl border border-line p-3" aria-label="Payment files">
      <div>
        <h3 className="text-sm font-semibold">Payment files</h3>
        <p className="text-xs text-ink-muted">Upload these to your bank or Mobile Money bulk-payment service, then record the payment below.</p>
      </div>
      {files.length === 0 && !cash && summary.missing.length === 0 ? <p className="text-sm text-ink-soft">Every payslip in this run has been paid.</p> : null}
      <ul className="grid gap-2">
        {files.map((g) => (
          <li key={g.method} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-sunken px-3 py-2 text-sm">
            <span><span className="font-semibold">{LABEL[g.method]}</span> · {g.people} {g.people === 1 ? "person" : "people"} · <span className="tabular">{money(g.total, summary.currency)}</span></span>
            <button type="button" className="btn btn-secondary btn-sm" disabled={busy !== null} onClick={() => void download(g.method as "BANK" | "MOBILE_MONEY")}>
              Download CSV
            </button>
          </li>
        ))}
        {cash ? (
          <li className="rounded-lg bg-sunken px-3 py-2 text-sm">
            <span className="font-semibold">Cash</span> · {cash.people} {cash.people === 1 ? "person" : "people"} · <span className="tabular">{money(cash.total, summary.currency)}</span> (no file)
          </li>
        ) : null}
      </ul>
      {summary.missing.length ? (
        <div className="alert alert-warning grid gap-1 text-sm">
          <p className="font-semibold">Not in any file yet: add their payout details first</p>
          <ul className="grid gap-0.5">
            {summary.missing.map((m) => (
              <li key={m.employeeId}>
                <Link className="underline" href={`/hr/employees/${m.employeeId}`}>{m.employeeName}</Link> ({m.employeeNumber}): {m.problem} · {money(m.net, summary.currency)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
    </section>
  );
}
