"use client";

import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { errorMessage } from "@/features/hr/ui";
import { saveDownloadedFile } from "@/lib/apiClient";
import { downloadStatutoryReturn, getCostAllocation, type CostAllocation } from "@/lib/hrApi";
import { money } from "./payrollUtils";

/**
 * For a calculated run: the SSNIT and PAYE returns to file, and what the run costs each branch (split by the minutes
 * people worked there, or the days they were based there after a transfer).
 */
export function RunReportsPanel({ runId, currency, refreshKey }: { runId: string; currency: string; refreshKey: string }) {
  const { authenticatedFetch } = useStaffSession();
  const [allocation, setAllocation] = useState<CostAllocation | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    authenticatedFetch((s) => getCostAllocation(s, runId)).then(setAllocation).catch(() => setAllocation(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId, refreshKey]);

  async function download(type: "ssnit" | "paye") {
    setBusy(type);
    setError("");
    try {
      saveDownloadedFile(await authenticatedFetch((s) => downloadStatutoryReturn(s, runId, type)));
    } catch (err) {
      setError(errorMessage(err, "Could not download the return"));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="grid gap-3 rounded-xl border border-line p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">Returns</h3>
        <span className="flex flex-wrap gap-2">
          <button type="button" className="btn btn-secondary btn-sm" disabled={busy !== null} onClick={() => void download("ssnit")}>
            {busy === "ssnit" ? "Preparing…" : "SSNIT return (CSV)"}
          </button>
          <button type="button" className="btn btn-secondary btn-sm" disabled={busy !== null} onClick={() => void download("paye")}>
            {busy === "paye" ? "Preparing…" : "PAYE return (CSV)"}
          </button>
        </span>
      </div>
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}

      {allocation && allocation.branches.length ? (
        <div className="grid gap-2">
          <h3 className="text-sm font-semibold">Cost by branch</h3>
          <table className="data-table">
            <thead><tr><th>Branch</th><th className="num">People</th><th className="num">Gross</th><th className="num">Employer</th><th className="num">Total cost</th></tr></thead>
            <tbody>
              {allocation.branches.map((b) => (
                <tr key={b.branchId}>
                  <td>{b.branchName ?? "Branch"}</td>
                  <td className="num tabular">{b.people}</td>
                  <td className="num tabular">{money(b.gross, currency)}</td>
                  <td className="num tabular">{money(b.employer, currency)}</td>
                  <td className="num tabular font-semibold">{money(b.total, currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {allocation.splitEmployees.length ? (
            <p className="text-xs text-ink-muted">
              Split across branches: {allocation.splitEmployees
                .map((e) => `${e.employeeName} (${Object.entries(e.shareByBranch).map(([b, pct]) => `${b} ${pct}%`).join(", ")})`)
                .join("; ")}
            </p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
