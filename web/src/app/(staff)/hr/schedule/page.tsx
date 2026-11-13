"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, PageHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { HrGuard, errorMessage } from "@/features/hr/ui";
import { ShiftDialog, IssuesList } from "@/features/hr/schedule/ShiftDialog";
import { DayList, MonthView, PolicyPanel, SwapApprovals, TemplatesPanel, weekRangeLabel } from "@/features/hr/schedule/SchedulePanels";
import { WeekBoard } from "@/features/hr/schedule/WeekBoard";
import { addDays, isoDate, startOfWeek } from "@/features/workforce/time";
import { getBranches, type BranchRecord } from "@/lib/apiClient";
import {
  ScheduleIssuesError,
  copyScheduleWeek,
  getScheduleBoard,
  getShiftTemplates,
  publishSchedule,
  type ScheduleBoard,
  type ScheduleIssue,
  type Shift,
  type ShiftTemplate,
} from "@/lib/hrApi";

type View = "week" | "day" | "month";
type Tab = "board" | "swaps" | "templates" | "policy";

export default function SchedulePage() {
  return (
    <HrGuard anyOf={["hr.schedule.view"]}>
      <Schedule />
    </HrGuard>
  );
}

function Schedule() {
  const { authenticatedFetch } = useStaffSession();
  const { can, access } = useHrAccess();
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState("");
  const [tab, setTab] = useState<Tab>("board");
  const [view, setView] = useState<View>("week");
  const [anchor, setAnchor] = useState(() => startOfWeek(new Date()));
  const [day, setDay] = useState(() => isoDate(new Date()));
  const [board, setBoard] = useState<ScheduleBoard | null>(null);
  const [templates, setTemplates] = useState<ShiftTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showCancelled, setShowCancelled] = useState(false);
  const [dialog, setDialog] = useState<{ shift?: Shift; employeeId?: string | null; date?: string } | null>(null);
  const [publishIssues, setPublishIssues] = useState<ScheduleIssue[]>([]);
  const [publishing, setPublishing] = useState(false);
  const [copying, setCopying] = useState(false);
  const [copySkipped, setCopySkipped] = useState<{ employeeName: string; when: string; reason: string }[]>([]);
  const canManage = can("hr.schedule.manage");
  const canPublish = can("hr.schedule.publish");

  useEffect(() => {
    authenticatedFetch((s) => getBranches(s)).then((list) => {
      const visible = list.filter((b) => b.active && (access?.allBranches || access?.branchIds.includes(b.id)));
      setBranches(visible);
      setBranchId((current) => current || visible[0]?.id || "");
    }).catch((err) => setError(errorMessage(err, "Could not load branches")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access?.allBranches, access?.branchIds.join(",")]);

  const range = useMemo(() => {
    if (view === "month") {
      const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
      const gridStart = startOfWeek(first);
      return { from: gridStart, days: 42 };
    }
    if (view === "day") return { from: new Date(`${day}T00:00:00`), days: 1 };
    return { from: anchor, days: 7 };
  }, [view, anchor, day]);

  const load = useCallback(async () => {
    if (!branchId) return;
    setLoading(true);
    setError("");
    try {
      const from = isoDate(range.from);
      const to = isoDate(addDays(range.from, range.days - 1));
      const [b, t] = await authenticatedFetch(async (s) => Promise.all([getScheduleBoard(s, branchId, from, to), getShiftTemplates(s, branchId)]));
      setBoard(b);
      setTemplates(t);
    } catch (err) {
      setError(errorMessage(err, "Could not load the schedule"));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, range.from.getTime(), range.days]);

  useEffect(() => { void load(); }, [load]);

  function step(direction: number) {
    if (view === "month") setAnchor(new Date(anchor.getFullYear(), anchor.getMonth() + direction, 1));
    else if (view === "day") setDay(isoDate(addDays(new Date(`${day}T00:00:00`), direction)));
    else setAnchor(addDays(anchor, 7 * direction));
  }

  async function publish() {
    if (!board) return;
    setPublishing(true);
    setPublishIssues([]);
    setNotice("");
    try {
      const result = await authenticatedFetch((s) => publishSchedule(s, branchId, board.from, board.to));
      setNotice(result.published
        ? `Published ${result.published} shift${result.published === 1 ? "" : "s"}${result.openShifts ? ` (${result.openShifts} still open)` : ""}. ${result.employeesNotified} employee${result.employeesNotified === 1 ? "" : "s"} notified.`
        : "There were no drafts to publish in this period.");
      await load();
    } catch (err) {
      if (err instanceof ScheduleIssuesError) setPublishIssues(err.issues);
      else setError(errorMessage(err, "Could not publish"));
    } finally {
      setPublishing(false);
    }
  }

  async function copyPreviousWeek() {
    if (!branchId) return;
    if (!window.confirm(`Copy the shifts of the week before into ${weekRangeLabel(anchor, 7)} as drafts?`)) return;
    setCopying(true);
    setNotice("");
    setCopySkipped([]);
    try {
      const result = await authenticatedFetch((s) => copyScheduleWeek(s, branchId, isoDate(addDays(anchor, -7)), isoDate(anchor)));
      setNotice(result.copied
        ? `Copied ${result.copied} shift${result.copied === 1 ? "" : "s"} as drafts. Review them, then publish.`
        : "The week before had no shifts to copy.");
      setCopySkipped(result.skipped);
      await load();
    } catch (err) {
      setError(errorMessage(err, "Could not copy the week"));
    } finally {
      setCopying(false);
    }
  }

  const drafts = board?.shifts.filter((s) => s.status === "DRAFT").length ?? 0;
  const understaffed = board?.coverage.filter((c) => c.state === "UNDERSTAFFED").length ?? 0;
  const open = board?.shifts.filter((s) => s.status !== "CANCELLED" && !s.employeeId).length ?? 0;
  const branchName = branches.find((b) => b.id === branchId)?.name ?? "";
  const title = view === "month"
    ? anchor.toLocaleDateString(undefined, { month: "long", year: "numeric" })
    : view === "day" ? new Date(`${day}T00:00:00`).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" })
      : weekRangeLabel(anchor, 7);

  const tabs: { key: Tab; label: string; show: boolean }[] = [
    { key: "board", label: "Schedule", show: true },
    { key: "swaps", label: "Swap requests", show: can("hr.shift.swap.approve") },
    { key: "templates", label: "Shift templates", show: true },
    { key: "policy", label: "Limits", show: true },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Human Resources"
        title="Shift Schedule"
        icon="calendar"
        description="Draft shifts, check them against availability, rest and hours limits, then publish so staff are notified."
        actions={branches.length > 1 ? (
          <select className="select w-auto" value={branchId} onChange={(e) => setBranchId(e.target.value)} aria-label="Branch">
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        ) : null}
      />

      {branches.length === 0 && !error ? (
        <EmptyState icon="store" title="No branch to schedule" description="Your account is not assigned to a branch. Ask an admin for branch access." />
      ) : (
        <>
          <div className="segmented max-w-full overflow-x-auto" role="tablist">
            {tabs.filter((t) => t.show).map((t) => (
              <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>{t.label}</button>
            ))}
          </div>

          {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}
          {error ? <ErrorState message={error} onRetry={load} /> : null}

          {tab === "board" ? (
            <section className="panel grid gap-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <button type="button" className="btn btn-secondary btn-icon btn-sm" onClick={() => step(-1)} aria-label="Previous"><Icon name="chevronLeft" size={16} /></button>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => { setAnchor(view === "month" ? new Date() : startOfWeek(new Date())); setDay(isoDate(new Date())); }}>Today</button>
                  <button type="button" className="btn btn-secondary btn-icon btn-sm" onClick={() => step(1)} aria-label="Next"><Icon name="chevronRight" size={16} /></button>
                  <h2 className="heading-md ml-1">{title}</h2>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <div className="segmented">
                    {(["day", "week", "month"] as View[]).map((v) => (
                      <button key={v} type="button" aria-pressed={view === v} onClick={() => setView(v)}>{v[0].toUpperCase() + v.slice(1)}</button>
                    ))}
                  </div>
                  {canManage && view === "week" ? (
                    <button type="button" className="btn btn-secondary btn-sm" onClick={() => void copyPreviousWeek()} disabled={copying}>
                      {copying ? "Copying…" : "Copy previous week"}
                    </button>
                  ) : null}
                  {canPublish && view !== "month" ? (
                    <button type="button" className="btn btn-primary btn-sm" onClick={publish} disabled={publishing || drafts === 0}>
                      {publishing ? "Publishing…" : `Publish ${drafts || ""} draft${drafts === 1 ? "" : "s"}`}
                    </button>
                  ) : null}
                </div>
              </div>

              {board ? (
                <div className="flex flex-wrap gap-2 text-xs">
                  <span className="badge badge-gold">{drafts} draft</span>
                  <span className={`badge ${open ? "badge-warning" : "badge-neutral"}`}>{open} open</span>
                  <span className={`badge ${understaffed ? "badge-danger" : "badge-success"}`}>{understaffed} understaffed slot{understaffed === 1 ? "" : "s"}</span>
                  {board.labour ? (
                    <span
                      className="badge badge-neutral"
                      title={board.labour.byEmployee.length
                        ? board.labour.byEmployee.map((e) => `${e.name ?? "Employee"}: ${(e.paidMinutes / 60).toFixed(1)} h, ${Number(e.cost).toFixed(2)}`).join("\n")
                        : "Paid hours at each employee's contract rate"}
                    >
                      Est. labour {Number(board.labour.total).toFixed(2)}
                      {board.labour.unpricedShifts ? ` (+${board.labour.unpricedShifts} unpriced)` : ""}
                    </span>
                  ) : null}
                  <label className="ml-auto flex items-center gap-1.5 text-ink-soft">
                    <input type="checkbox" checked={showCancelled} onChange={(e) => setShowCancelled(e.target.checked)} /> Show cancelled
                  </label>
                </div>
              ) : null}
              {publishIssues.length ? <IssuesList issues={publishIssues} /> : null}
              {copySkipped.length ? (
                <div className="alert alert-warning text-sm">
                  <p className="font-semibold">Not copied ({copySkipped.length}):</p>
                  <ul className="mt-1 list-disc pl-5">
                    {copySkipped.map((s, i) => <li key={i}>{s.employeeName}, {s.when}: {s.reason}</li>)}
                  </ul>
                </div>
              ) : null}

              {!board && loading ? <Skeleton className="h-72 w-full" /> : null}
              {board ? (
                <div className={loading ? "opacity-60" : ""}>
                  {view === "week" ? (
                    <WeekBoard
                      board={board}
                      weekStart={anchor}
                      days={7}
                      showCancelled={showCancelled}
                      canManage={canManage}
                      onOpenShift={(shift) => setDialog({ shift })}
                      onAddShift={(employeeId, date) => setDialog({ employeeId, date })}
                      onChanged={(m) => { setNotice(m); void load(); }}
                    />
                  ) : view === "day" ? (
                    <div className="grid gap-3">
                      {canManage ? <div><button type="button" className="btn btn-secondary btn-sm" onClick={() => setDialog({ date: day })}><Icon name="plus" size={16} /> Add shift</button></div> : null}
                      <DayList board={board} date={day} onOpenShift={(id) => setDialog({ shift: board.shifts.find((s) => s.id === id) })} />
                    </div>
                  ) : (
                    <MonthView board={board} month={anchor} onPickWeek={(start) => { setAnchor(start); setView("week"); }} />
                  )}
                </div>
              ) : null}
            </section>
          ) : null}

          {tab === "swaps" ? <section className="panel"><SwapApprovals onChanged={() => void load()} /></section> : null}
          {tab === "templates" && branchId ? <section className="panel"><TemplatesPanel branchId={branchId} onChanged={() => void load()} /></section> : null}
          {tab === "policy" && branchId ? <section className="panel"><PolicyPanel branchId={branchId} branchName={branchName} /></section> : null}
        </>
      )}

      {dialog && board ? (
        <ShiftDialog
          branchId={branchId}
          employees={board.employees}
          templates={templates}
          shift={dialog.shift}
          defaults={{ employeeId: dialog.employeeId, date: dialog.date }}
          canManage={canManage}
          onClose={() => setDialog(null)}
          onSaved={(message) => { setDialog(null); setNotice(message); void load(); }}
        />
      ) : null}
    </div>
  );
}
