"use client";

import { useCallback, useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, SectionHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { errorMessage } from "@/features/hr/ui";
import { IssuesList } from "@/features/hr/schedule/ShiftDialog";
import { shiftLabel } from "@/features/hr/schedule/scheduleUtils";
import { formatMinutes } from "@/features/workforce/time";
import { ScheduleIssuesError, claimOpenShift, getMyOpenShifts, type OpenShift, type ScheduleIssue } from "@/lib/hrApi";

/** Unassigned shifts at my branches in the coming weeks that I could take. Taking one tells the branch's managers. */
export function OpenShifts({ onTaken }: { onTaken: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [open, setOpen] = useState<OpenShift[] | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ id: string; issues: ScheduleIssue[] } | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    try {
      setOpen(await authenticatedFetch((s) => getMyOpenShifts(s)));
    } catch {
      setOpen([]); // not linked to an employee record, or offline: nothing to offer
    }
  }, [authenticatedFetch]);

  useEffect(() => {
    void load();
  }, [load]);

  async function take(id: string, acknowledge: boolean) {
    setBusyId(id);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((s) => claimOpenShift(s, id, acknowledge));
      setConfirm(null);
      setMessage("The shift is yours. Your manager has been told.");
      await load();
      onTaken();
    } catch (err) {
      if (err instanceof ScheduleIssuesError && !err.blocking) setConfirm({ id, issues: err.issues });
      else setError(errorMessage(err, "Could not take the shift"));
      await load();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="panel grid gap-3">
      <SectionHeader title="Open shifts" description="Shifts nobody has yet at your branches. Take one if you can work it." />
      {message ? <p className="alert alert-success" role="status">{message}</p> : null}
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {!open ? (
        <Skeleton className="h-20 w-full" />
      ) : open.length === 0 ? (
        <EmptyState compact icon="calendar" title="No open shifts right now" />
      ) : (
        <ul className="grid gap-2">
          {open.map(({ shift, warnings }) => (
            <li key={shift.id} className="card grid gap-2 p-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-semibold">{new Date(shift.startsAt).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "short" })}</p>
                  <p className="text-sm text-ink-soft tabular">
                    {shiftLabel(shift)} · {formatMinutes(shift.paidMinutes)} paid{shift.positionTitle ? ` · ${shift.positionTitle}` : ""}
                  </p>
                  {warnings.length ? <p className="text-xs text-warning-on">{warnings.map((w) => w.message).join(" · ")}</p> : null}
                </div>
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  disabled={busyId === shift.id}
                  onClick={() => void take(shift.id, false)}
                >
                  {busyId === shift.id ? "Taking…" : "Take this shift"}
                </button>
              </div>
              {confirm?.id === shift.id ? (
                <div className="grid gap-2">
                  <IssuesList issues={confirm.issues} />
                  <button type="button" className="btn btn-accent btn-sm justify-self-start" disabled={busyId === shift.id} onClick={() => void take(shift.id, true)}>
                    Take it anyway
                  </button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
