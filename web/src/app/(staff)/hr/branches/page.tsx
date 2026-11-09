"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader, StatCard } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { HrGuard, errorMessage } from "@/features/hr/ui";
import { getBranchWorkforce, type BranchWorkforce } from "@/lib/hrApi";

export default function BranchWorkforcePage() {
  return (
    <HrGuard anyOf={["hr.branch.view", "hr.employee.view"]}>
      <Workforce />
    </HrGuard>
  );
}

function Workforce() {
  const { authenticatedFetch } = useStaffSession();
  const [rows, setRows] = useState<BranchWorkforce[] | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      setRows(await authenticatedFetch((s) => getBranchWorkforce(s)));
    } catch (err) {
      setError(errorMessage(err, "Could not load branch workforce"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { void load(); }, [load]);

  const totals = (rows ?? []).reduce(
    (t, r) => ({ current: t.current + r.currentEmployees, onLeave: t.onLeave + r.onLeave, suspended: t.suspended + r.suspended, covering: t.covering + r.coveringFromOtherBranches }),
    { current: 0, onLeave: 0, suspended: 0, covering: 0 },
  );

  return (
    <div className="grid gap-6">
      <PageHeader eyebrow="Human Resources" title="Branch Workforce" icon="store" description="Headcount for each branch you can access. Employees count at their home branch; staff covering from other branches are shown separately." />
      {error ? <ErrorState message={error} onRetry={load} /> : null}
      {!rows && !error ? <Skeleton className="h-48 w-full" /> : null}
      {rows ? (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard label="Current employees" value={totals.current} icon="users" tone="brand" />
            <StatCard label="On leave" value={totals.onLeave} icon="calendar" tone="info" />
            <StatCard label="Suspended" value={totals.suspended} icon="alert" tone="gold" />
            <StatCard label="Covering other branches" value={totals.covering} icon="route" tone="sage" />
          </div>
          <section className="panel">
            {rows.length === 0 ? <EmptyState compact icon="store" title="No branches in your scope" /> : (
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead><tr><th>Branch</th><th className="num">Current</th><th className="num">Active</th><th className="num">On leave</th><th className="num">Suspended</th><th className="num">Covering here</th></tr></thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.branchId ?? "none"}>
                        <td>
                          {r.branchId ? <Link className="font-semibold hover:text-brand" href="/hr/employees">{r.name}</Link> : <span className="font-semibold">{r.name}</span>}
                          {r.code ? <span className="ml-2 text-xs text-ink-muted">{r.code}</span> : null}
                          {!r.active ? <span className="ml-2 badge badge-neutral">Inactive</span> : null}
                        </td>
                        <td className="num font-semibold">{r.currentEmployees}</td>
                        <td className="num">{r.activeCount}</td>
                        <td className="num">{r.onLeave}</td>
                        <td className="num">{r.suspended}</td>
                        <td className="num">{r.coveringFromOtherBranches}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
