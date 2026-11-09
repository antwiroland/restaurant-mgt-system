"use client";

import { useState, type DragEvent } from "react";
import { Icon } from "@/components/Icon";
import { useStaffSession } from "@/components/SessionProvider";
import { errorMessage } from "@/features/hr/ui";
import { addDays, formatMinutes, isoDate } from "@/features/workforce/time";
import { ScheduleIssuesError, saveShift, type ScheduleBoard, type ScheduleIssue, type Shift } from "@/lib/hrApi";
import { IssuesList } from "./ShiftDialog";
import { paidMinutesByEmployee, shiftDay, shiftLabel } from "./scheduleUtils";

const OPEN = "__open__";

const chipTone: Record<Shift["status"], string> = {
  DRAFT: "border-dashed border-accent bg-accent-subtle text-ink",
  PUBLISHED: "border-brand/40 bg-brand-subtle text-ink",
  CANCELLED: "border-line bg-muted text-ink-muted line-through",
};

type Props = {
  board: ScheduleBoard;
  weekStart: Date;
  days: number;
  showCancelled: boolean;
  canManage: boolean;
  onOpenShift: (shift: Shift) => void;
  onAddShift: (employeeId: string | null, date: string) => void;
  onChanged: (message: string) => void;
};

/** Rows = people (plus open shifts), columns = days. Drag a shift to another person or day to move it. */
export function WeekBoard({ board, weekStart, days, showCancelled, canManage, onOpenShift, onAddShift, onChanged }: Props) {
  const { authenticatedFetch } = useStaffSession();
  const [dragging, setDragging] = useState<Shift | null>(null);
  const [pending, setPending] = useState<{ shift: Shift; employeeId: string | null; date: string; issues: ScheduleIssue[]; blocking: boolean } | null>(null);
  const [error, setError] = useState("");
  const dates = Array.from({ length: days }, (_, i) => addDays(weekStart, i));
  const visible = board.shifts.filter((s) => showCancelled || s.status !== "CANCELLED");
  const totals = paidMinutesByEmployee(board.shifts);
  const cell = (rowId: string, date: string) =>
    visible.filter((s) => (s.employeeId ?? OPEN) === rowId && shiftDay(s) === date).sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const rows = [{ id: OPEN, name: "Open shifts", positionTitle: "Unassigned", homeBranch: true, status: "ACTIVE" }, ...board.employees];
  const coverageByDay = (date: string) => board.coverage.filter((c) => c.date === date);

  async function move(shift: Shift, employeeId: string | null, date: string, acknowledge: boolean) {
    const deltaDays = Math.round((new Date(`${date}T00:00:00`).getTime() - new Date(`${shiftDay(shift)}T00:00:00`).getTime()) / 86400000);
    const shiftBy = (iso: string) => new Date(new Date(iso).getTime() + deltaDays * 86400000).toISOString();
    setError("");
    try {
      await authenticatedFetch((s) => saveShift(s, {
        employeeId, branchId: shift.branchId, templateId: shift.templateId ?? null,
        startsAt: shiftBy(shift.startsAt), endsAt: shiftBy(shift.endsAt), breakMinutes: shift.breakMinutes,
        notes: shift.notes ?? undefined, acknowledgeWarnings: acknowledge, version: shift.version,
      }, shift.id));
      setPending(null);
      onChanged(shift.status === "PUBLISHED" ? "Shift moved. The employees involved were notified." : "Shift moved.");
    } catch (err) {
      if (err instanceof ScheduleIssuesError) {
        setPending({ shift, employeeId, date, issues: err.issues, blocking: err.blocking });
      } else {
        setError(errorMessage(err, "Could not move the shift"));
      }
    }
  }

  function onDrop(event: DragEvent, rowId: string, date: string) {
    event.preventDefault();
    const shift = dragging;
    setDragging(null);
    if (!shift || !canManage) return;
    const employeeId = rowId === OPEN ? null : rowId;
    if ((shift.employeeId ?? null) === employeeId && shiftDay(shift) === date) return;
    void move(shift, employeeId, date, false);
  }

  return (
    <div className="grid gap-3">
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {pending ? (
        <div className="grid gap-2">
          <IssuesList issues={pending.issues} />
          <div className="flex gap-2">
            {!pending.blocking ? (
              <button type="button" className="btn btn-accent btn-sm" onClick={() => move(pending.shift, pending.employeeId, pending.date, true)}>Move anyway</button>
            ) : null}
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setPending(null)}>Dismiss</button>
          </div>
        </div>
      ) : null}
      <div className="overflow-x-auto rounded-xl border border-line">
        <table className="w-full min-w-[880px] border-collapse text-sm">
          <thead>
            <tr className="bg-sunken">
              <th className="sticky left-0 z-10 w-48 border-b border-line bg-sunken px-3 py-2 text-left text-[11px] font-bold uppercase tracking-[0.1em] text-ink-soft">Staff</th>
              {dates.map((d) => {
                const key = isoDate(d);
                const cover = coverageByDay(key);
                return (
                  <th key={key} className="border-b border-l border-line px-2 py-2 text-left align-top">
                    <p className="text-xs font-bold text-ink">{d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })}</p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      {cover.map((c) => (
                        <span
                          key={c.templateId}
                          title={`${c.templateName}: ${c.assigned} assigned of ${c.minStaff} needed${c.open ? `, ${c.open} open` : ""}`}
                          className={`badge text-[10px] ${c.state === "UNDERSTAFFED" ? "badge-danger" : c.state === "OVERSTAFFED" ? "badge-warning" : "badge-success"}`}
                        >
                          {c.templateName} {c.assigned}/{c.minStaff}
                        </span>
                      ))}
                    </div>
                  </th>
                );
              })}
              <th className="w-20 border-b border-l border-line px-2 py-2 text-right text-[11px] font-bold uppercase tracking-[0.1em] text-ink-soft">Hours</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const minutes = row.id === OPEN ? 0 : totals.get(row.id) ?? 0;
              const over = days >= 7 && minutes > board.policy.maxWeeklyMinutes;
              return (
                <tr key={row.id} className={row.id === OPEN ? "bg-sunken/40" : ""}>
                  <th scope="row" className="sticky left-0 z-10 border-b border-line bg-surface px-3 py-2 text-left align-top font-normal">
                    <p className="font-semibold text-ink">{row.name}</p>
                    <p className="text-xs text-ink-muted">{row.positionTitle ?? ""}{row.homeBranch ? "" : " · covering"}{row.status !== "ACTIVE" && row.id !== OPEN ? ` · ${row.status.toLowerCase().replace("_", " ")}` : ""}</p>
                  </th>
                  {dates.map((d) => {
                    const key = isoDate(d);
                    const items = cell(row.id, key);
                    return (
                      <td
                        key={key}
                        className="group border-b border-l border-line px-1.5 py-1.5 align-top"
                        onDragOver={(e) => { if (canManage && dragging) e.preventDefault(); }}
                        onDrop={(e) => onDrop(e, row.id, key)}
                      >
                        <div className="grid gap-1">
                          {items.map((s) => (
                            <button
                              key={s.id}
                              type="button"
                              draggable={canManage && s.status !== "CANCELLED"}
                              onDragStart={() => setDragging(s)}
                              onDragEnd={() => setDragging(null)}
                              onClick={() => onOpenShift(s)}
                              className={`w-full rounded-lg border px-2 py-1 text-left text-xs ${chipTone[s.status]} ${canManage && s.status !== "CANCELLED" ? "cursor-grab active:cursor-grabbing" : ""}`}
                              title={`${s.status.toLowerCase()}${s.notes ? ` · ${s.notes}` : ""}`}
                            >
                              <span className="font-semibold tabular">{shiftLabel(s)}</span>
                              {s.templateName ? <span className="block truncate text-ink-soft">{s.templateName}</span> : null}
                            </button>
                          ))}
                          {canManage ? (
                            <button
                              type="button"
                              className="rounded-lg border border-dashed border-transparent py-0.5 text-xs text-ink-muted opacity-60 hover:border-line hover:opacity-100 focus:opacity-100 sm:opacity-0 sm:group-hover:opacity-100"
                              onClick={() => onAddShift(row.id === OPEN ? null : row.id, key)}
                              aria-label={`Add shift for ${row.name} on ${key}`}
                            >
                              <Icon name="plus" size={14} className="mx-auto" />
                            </button>
                          ) : null}
                        </div>
                      </td>
                    );
                  })}
                  <td className={`border-b border-l border-line px-2 py-2 text-right align-top text-xs tabular ${over ? "font-bold text-danger" : "text-ink-soft"}`}>
                    {row.id === OPEN ? "" : formatMinutes(minutes)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-ink-muted">
        Dashed = draft (staff can&apos;t see it yet), solid = published. {canManage ? "Drag a shift to move it to another person or day." : ""}
        {" "}Hours are paid time after unpaid breaks; the weekly limit is {formatMinutes(board.policy.maxWeeklyMinutes)}.
      </p>
    </div>
  );
}
