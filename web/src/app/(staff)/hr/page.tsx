"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { ErrorState, PageHeader, StatCard, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { HrGuard, errorMessage, formatDate } from "@/features/hr/ui";
import { money } from "@/features/hr/payroll/payrollUtils";
import { HBars, HeadcountColumns } from "@/features/hr/report/charts";
import { getHrDashboard, type HrDashboard } from "@/lib/hrApi";

export default function HrDashboardPage() {
  return (
    <HrGuard anyOf={["hr.reports.view"]} title="HR reports access required">
      <Dashboard />
    </HrGuard>
  );
}

const expiryLabel = { CONTRACT_END: "Contract", PROBATION_END: "Probation", DOCUMENT: "Document" } as const;

function Dashboard() {
  const { authenticatedFetch } = useStaffSession();
  const [branchId, setBranchId] = useState("");
  const [data, setData] = useState<HrDashboard | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      setData(await authenticatedFetch((s) => getHrDashboard(s, branchId || undefined)));
    } catch (err) {
      setError(errorMessage(err, "Could not load the HR dashboard"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId]);

  useEffect(() => { void load(); }, [load]);

  const today = data?.today;
  const count = (key: string) => today?.counts[key] ?? 0;
  const waiting = (data?.actions ?? []).filter((a) => a.count > 0);

  return (
    <div className="grid gap-6">
      <PageHeader
        eyebrow="Human Resources"
        title="HR Dashboard"
        icon="chart"
        description={data ? `Your workforce as of ${formatDate(data.asOf)}.` : "Your workforce at a glance."}
        actions={data && data.branches.length > 1 ? (
          <label className="field min-w-48">
            <span className="sr-only">Branch</span>
            <select className="select" value={branchId} onChange={(e) => setBranchId(e.target.value)}>
              <option value="">All my branches</option>
              {data.branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </label>
        ) : null}
      />
      {error ? <ErrorState message={error} onRetry={load} /> : null}
      {!data && !error ? <Skeleton className="h-96 w-full" /> : null}

      {data ? (
        <>
          <section aria-label="Headcount" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Employed" value={data.headcount.employed} icon="users" tone="brand"
              meta={`${data.headcount.byStatus.ON_LEAVE ?? 0} on leave · ${data.headcount.byStatus.SUSPENDED ?? 0} suspended`} />
            <StatCard label="Starting soon" value={data.headcount.startingSoon} icon="user" tone="sage" meta="Hired, start date ahead" />
            <StatCard label="Leavers (12 months)" value={data.movement.leaversLast12Months} icon="logout" tone="neutral"
              meta={data.movement.turnoverPercent != null ? `${data.movement.turnoverPercent}% turnover` : "Not enough history yet"} />
            <StatCard label="Needs action" value={waiting.reduce((n, a) => n + a.count, 0)} icon="bell" tone={waiting.length ? "gold" : "neutral"}
              meta={waiting.length ? `${waiting.length} kind${waiting.length === 1 ? "" : "s"} of request` : "All caught up"} />
          </section>

          {today ? (
            <section className="panel grid gap-3">
              <h2 className="text-sm font-semibold">Today</h2>
              <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                {[
                  ["On shift now", count("WORKING") + count("ON_BREAK")],
                  ["Not started yet", count("NOT_STARTED")],
                  ["Absent", count("ABSENT")],
                  ["On leave", count("ON_LEAVE")],
                  ["Late arrivals", today.lateArrivals],
                  ["Finished", count("COMPLETED") + count("LEFT_EARLY")],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-xl bg-sunken p-3">
                    <dt className="text-xs text-ink-muted">{label}</dt>
                    <dd className="font-display text-2xl font-extrabold tabular text-ink">{value}</dd>
                  </div>
                ))}
              </dl>
              <Link href="/hr/attendance" className="text-sm font-semibold text-brand hover:underline">Open the attendance board</Link>
            </section>
          ) : null}

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="panel grid content-start gap-3">
              <h2 className="text-sm font-semibold">Waiting for someone</h2>
              {data.actions.length === 0 ? <p className="text-sm text-ink-muted">Nothing you can act on here.</p> : (
                <ul className="grid gap-1">
                  {data.actions.map((a) => (
                    <li key={a.key}>
                      <Link href={a.href} className="flex items-center justify-between gap-3 rounded-lg px-2 py-2 text-sm hover:bg-sunken">
                        <span className={a.count ? "text-ink" : "text-ink-muted"}>{a.label}</span>
                        <span className={`badge ${a.count ? "badge-warning" : "badge-neutral"} tabular`}>{a.count}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="panel grid content-start gap-3">
              <h2 className="text-sm font-semibold">Expiring in the next 30 days</h2>
              {data.expiring.length === 0 ? <p className="text-sm text-ink-muted">No contracts, probations or documents due.</p> : (
                <ul className="grid gap-1">
                  {data.expiring.slice(0, 10).map((x) => (
                    <li key={`${x.kind}-${x.employeeId}-${x.detail}-${x.date}`}>
                      <Link href={`/hr/employees/${x.employeeId}`} className="flex items-center justify-between gap-3 rounded-lg px-2 py-2 text-sm hover:bg-sunken">
                        <span className="min-w-0">
                          <span className="font-semibold text-ink">{x.employeeName}</span>
                          <span className="block truncate text-xs text-ink-muted">{expiryLabel[x.kind]}: {x.detail}</span>
                        </span>
                        <span className={`badge ${x.overdue ? "badge-danger" : "badge-warning"} shrink-0`}>
                          {x.overdue ? "Overdue · " : ""}{formatDate(x.date)}
                        </span>
                      </Link>
                    </li>
                  ))}
                  {data.expiring.length > 10 ? <li className="px-2 text-xs text-ink-muted">and {data.expiring.length - 10} more</li> : null}
                </ul>
              )}
            </section>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            <section className="panel grid content-start gap-3 lg:col-span-1">
              <h2 className="text-sm font-semibold">Headcount, last 6 months</h2>
              <HeadcountColumns data={data.trend} />
            </section>
            <section className="panel grid content-start gap-3">
              <h2 className="text-sm font-semibold">By department</h2>
              <HBars data={data.headcount.byDepartment} label="Employees by department" />
            </section>
            <section className="panel grid content-start gap-3">
              <h2 className="text-sm font-semibold">{data.branches.length > 1 && !branchId ? "By branch" : "By employment type"}</h2>
              {data.branches.length > 1 && !branchId
                ? <HBars data={data.headcount.byBranch} label="Employees by branch" />
                : <HBars data={data.headcount.byEmploymentType.map((s) => ({ ...s, label: humanize(s.label) }))} label="Employees by employment type" />}
            </section>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="panel grid content-start gap-3">
              <h2 className="text-sm font-semibold">Joiners and leavers</h2>
              <PeopleList title="Joining this month" icon="plus" people={data.movement.joiners} empty="No one joining this month." />
              <PeopleList title="Left since last month" icon="minus" people={data.movement.leavers} empty="No one has left recently." humanizeDetail />
            </section>

            {data.recruitment ? (
              <section className="panel grid content-start gap-3">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-sm font-semibold">Recruitment</h2>
                  <Link href="/hr/recruitment" className="text-sm font-semibold text-brand hover:underline">Open recruitment</Link>
                </div>
                <dl className="grid grid-cols-3 gap-3">
                  <div className="rounded-xl bg-sunken p-3"><dt className="text-xs text-ink-muted">Open vacancies</dt><dd className="font-display text-2xl font-extrabold tabular">{data.recruitment.openVacancies}</dd></div>
                  <div className="rounded-xl bg-sunken p-3"><dt className="text-xs text-ink-muted">On careers page</dt><dd className="font-display text-2xl font-extrabold tabular">{data.recruitment.published}</dd></div>
                  <div className="rounded-xl bg-sunken p-3"><dt className="text-xs text-ink-muted">Interviews (7 days)</dt><dd className="font-display text-2xl font-extrabold tabular">{data.recruitment.interviewsNext7Days}</dd></div>
                </dl>
                <HBars data={Object.entries(data.recruitment.pipeline).map(([stage, n]) => ({ label: humanize(stage), count: n }))}
                  label="Applicants by stage" empty="No applicants in progress." />
              </section>
            ) : null}
          </div>

          {data.payVisible ? (
            <section className="panel grid gap-3">
              <div>
                <h2 className="text-sm font-semibold">Labour cost</h2>
                <p className="text-xs text-ink-muted">Latest finalized or paid payroll per branch, against money collected for that branch&apos;s orders in the same period.</p>
              </div>
              {data.labour.length === 0 ? <p className="text-sm text-ink-muted">No finalized payroll yet.</p> : (
                <div className="overflow-x-auto">
                  <table className="data-table">
                    <thead><tr><th>Branch</th><th>Period</th><th className="num">Staff</th><th className="num">Gross pay</th><th className="num">Employer cost</th><th className="num">Total cost</th><th className="num">Sales</th><th className="num">Labour %</th><th className="num">Overtime</th></tr></thead>
                    <tbody>
                      {data.labour.map((r) => (
                        <tr key={r.branchId}>
                          <td className="font-semibold">{r.branchName}<p className="text-xs font-normal text-ink-muted">{r.runNumber} · {humanize(r.status)}</p></td>
                          <td className="text-sm">{formatDate(r.periodStart)} – {formatDate(r.periodEnd)}</td>
                          <td className="num tabular">{r.employees}</td>
                          <td className="num tabular">{money(r.grossPay)}</td>
                          <td className="num tabular">{money(r.employerCost)}</td>
                          <td className="num tabular font-semibold">{money(r.totalCost)}</td>
                          <td className="num tabular">{money(r.sales)}</td>
                          <td className="num tabular">{r.labourPercent != null ? `${r.labourPercent}%` : "—"}</td>
                          <td className="num tabular">{(r.overtimeMinutes / 60).toFixed(1)} h</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}

function PeopleList({ title, icon, people, empty, humanizeDetail = false }: {
  title: string; icon: "plus" | "minus"; people: HrDashboard["movement"]["joiners"]; empty: string; humanizeDetail?: boolean;
}) {
  return (
    <div className="grid gap-1">
      <h3 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-ink-muted"><Icon name={icon} size={14} />{title}</h3>
      {people.length === 0 ? <p className="text-sm text-ink-muted">{empty}</p> : (
        <ul className="grid gap-0.5">
          {people.slice(0, 8).map((p) => (
            <li key={p.employeeId}>
              <Link href={`/hr/employees/${p.employeeId}`} className="flex items-center justify-between gap-3 rounded-lg px-2 py-1.5 text-sm hover:bg-sunken">
                <span><span className="font-semibold text-ink">{p.name}</span>{p.detail ? <span className="text-ink-muted"> · {humanizeDetail ? humanize(p.detail) : p.detail}</span> : null}</span>
                <span className="text-xs text-ink-muted">{formatDate(p.date)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
