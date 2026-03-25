"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  closeShift,
  getActiveShifts,
  getBranches,
  openShift,
  type BranchRecord,
  type ShiftRecord,
} from "@/lib/apiClient";

export default function ShiftsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [shifts, setShifts] = useState<ShiftRecord[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [openingCash, setOpeningCash] = useState("0");
  const [branchId, setBranchId] = useState("");
  const [openNotes, setOpenNotes] = useState("");
  const [closingCashByShift, setClosingCashByShift] = useState<Record<string, string>>({});
  const [closingNotesByShift, setClosingNotesByShift] = useState<Record<string, string>>({});
  const [busyOpen, setBusyOpen] = useState(false);
  const [busyCloseId, setBusyCloseId] = useState<string | null>(null);

  async function loadData() {
    if (!session) return;
    await authenticatedFetch(async (activeSession) => {
      const [nextShifts, nextBranches] = await Promise.all([
        getActiveShifts(activeSession),
        getBranches(activeSession),
      ]);
      setShifts(nextShifts);
      setBranches(nextBranches);
    });
  }

  useEffect(() => {
    if (!session) return;
    loadData().catch((err) => setError(err instanceof Error ? err.message : "Could not load shifts"));
  }, [authenticatedFetch, session]);

  async function handleOpen() {
    if (!session) return;
    const amount = Number.parseFloat(openingCash);
    if (!Number.isFinite(amount) || amount < 0) {
      setError("Opening cash must be a valid non-negative amount");
      return;
    }

    setBusyOpen(true);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((activeSession) =>
        openShift(activeSession, {
          openingCash: amount,
          branchId: branchId || undefined,
          notes: openNotes.trim() || undefined,
        }),
      );
      setMessage("Shift opened successfully.");
      setOpenNotes("");
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not open shift");
    } finally {
      setBusyOpen(false);
    }
  }

  async function handleClose(shiftId: string) {
    if (!session) return;
    const raw = closingCashByShift[shiftId] ?? "0";
    const amount = Number.parseFloat(raw);
    if (!Number.isFinite(amount) || amount < 0) {
      setError("Closing cash must be a valid non-negative amount");
      return;
    }

    setBusyCloseId(shiftId);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((activeSession) =>
        closeShift(activeSession, shiftId, {
          closingCash: amount,
          notes: (closingNotesByShift[shiftId] ?? "").trim() || undefined,
        }),
      );
      setMessage("Shift closed successfully.");
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not close shift");
    } finally {
      setBusyCloseId(null);
    }
  }

  if (loading) {
    return <main className="shell"><section className="panel">Loading shifts...</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Shifts</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 md:grid-cols-[1fr,1.4fr]">
      <section className="panel">
        <p className="kicker">Shift Management</p>
        <h1 className="text-2xl font-semibold">Open Shift</h1>
        <p className="mt-2 text-sm text-[#35523d]">Start cashier shift and capture opening drawer amount.</p>

        <div className="mt-4 grid gap-3">
          <label className="grid gap-2 text-sm text-[#35523d]">
            <span>Opening Cash</span>
            <input
              className="rounded-xl border border-[#cfe0c8] p-3"
              type="number"
              min="0"
              step="0.01"
              value={openingCash}
              onChange={(event) => setOpeningCash(event.target.value)}
            />
          </label>

          <label className="grid gap-2 text-sm text-[#35523d]">
            <span>Branch</span>
            <select
              className="rounded-xl border border-[#cfe0c8] p-3"
              value={branchId}
              onChange={(event) => setBranchId(event.target.value)}
            >
              <option value="">Default / Assigned branch</option>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>{branch.name}</option>
              ))}
            </select>
          </label>

          <label className="grid gap-2 text-sm text-[#35523d]">
            <span>Notes</span>
            <textarea
              className="min-h-24 rounded-xl border border-[#cfe0c8] p-3"
              value={openNotes}
              onChange={(event) => setOpenNotes(event.target.value)}
            />
          </label>

          {error ? <p className="rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
          {message ? <p className="rounded-xl bg-[#e7f7eb] px-4 py-3 text-sm text-[#166534]">{message}</p> : null}

          <button
            type="button"
            className="rounded-full bg-[#132018] px-4 py-3 text-white disabled:opacity-60"
            disabled={busyOpen}
            onClick={() => void handleOpen()}
          >
            {busyOpen ? "Opening..." : "Open Shift"}
          </button>
        </div>
      </section>

      <section className="panel">
        <p className="kicker">Active Shifts</p>
        <h2 className="text-xl font-semibold">Open Drawers</h2>
        <div className="mt-4 grid gap-3">
          {shifts.length === 0 ? (
            <p className="rounded-xl border border-dashed border-[#cfe0c8] p-4 text-sm text-[#35523d]">No active shifts.</p>
          ) : (
            shifts.map((shift) => (
              <article key={shift.id} className="rounded-xl border border-[#cfe0c8] p-4">
                <p className="font-semibold">{shift.cashierName}</p>
                <p className="text-sm text-[#35523d]">{shift.branchName ?? "No branch"}</p>
                <p className="mt-2 text-sm text-[#35523d]">Opened {new Date(shift.openedAt).toLocaleString()}</p>
                <p className="mt-1 text-sm text-[#35523d]">Opening Cash: GHS {Number.parseFloat(shift.openingCash).toFixed(2)}</p>

                <div className="mt-3 grid gap-2 md:grid-cols-2">
                  <input
                    className="rounded-xl border border-[#cfe0c8] p-3 text-sm"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="Closing cash"
                    value={closingCashByShift[shift.id] ?? ""}
                    onChange={(event) =>
                      setClosingCashByShift((current) => ({ ...current, [shift.id]: event.target.value }))
                    }
                  />
                  <input
                    className="rounded-xl border border-[#cfe0c8] p-3 text-sm"
                    type="text"
                    placeholder="Close notes"
                    value={closingNotesByShift[shift.id] ?? ""}
                    onChange={(event) =>
                      setClosingNotesByShift((current) => ({ ...current, [shift.id]: event.target.value }))
                    }
                  />
                </div>

                <button
                  type="button"
                  className="mt-3 rounded-full border border-[#132018] px-4 py-2 text-sm font-semibold text-[#132018] disabled:opacity-60"
                  disabled={busyCloseId === shift.id}
                  onClick={() => void handleClose(shift.id)}
                >
                  {busyCloseId === shift.id ? "Closing..." : "Close Shift"}
                </button>
              </article>
            ))
          )}
        </div>
      </section>
    </main>
  );
}
