"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import {
  addCashMovement,
  closeShift,
  getActiveShifts,
  getBranches,
  getShiftDetail,
  getShiftHistory,
  openShift,
  type BranchRecord,
  type CashMovementType,
  type ShiftDetailRecord,
  type ShiftRecord,
} from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";

function money(value?: string | number | null): string {
  return value === undefined || value === null || value === "" ? "—" : formatMoney(Number(value));
}

function varianceTone(variance?: string | null): string {
  const value = Number(variance ?? 0);
  if (!variance || value === 0) return "text-ink";
  return value < 0 ? "text-danger-on" : "text-warning-on";
}

function varianceLabel(variance?: string | null): string {
  if (variance === undefined || variance === null) return "—";
  const value = Number(variance);
  if (value === 0) return "Balanced";
  return `${value < 0 ? "Short" : "Over"} ${formatMoney(Math.abs(value))}`;
}

/** The drawer breakdown: how expected cash is made up. */
function Breakdown({ detail }: { detail: ShiftDetailRecord }) {
  const rows: [string, string, string?][] = [
    ["Opening float", money(detail.shift.openingCash)],
    [`Cash sales (${detail.cashSalesCount})`, `+ ${money(detail.cashSales)}`],
    [`Cash refunds (${detail.cashRefundsCount})`, `− ${money(detail.cashRefunds)}`],
    ["Cash in", `+ ${money(detail.payIns)}`],
    ["Cash out", `− ${money(detail.payOuts)}`],
  ];
  return (
    <div className="mt-3 rounded-lg bg-sunken p-3 text-sm">
      <dl className="grid gap-1">
        {rows.map(([label, value]) => (
          <div key={label} className="flex justify-between gap-3">
            <dt className="text-ink-soft">{label}</dt>
            <dd className="money">{value}</dd>
          </div>
        ))}
        <div className="mt-1 flex justify-between gap-3 border-t border-line pt-1 font-semibold">
          <dt>Expected in drawer</dt>
          <dd className="money">{money(detail.expectedCash)}</dd>
        </div>
        {detail.shift.status === "CLOSED" ? (
          <>
            <div className="flex justify-between gap-3">
              <dt className="text-ink-soft">Counted</dt>
              <dd className="money">{money(detail.shift.closingCash)}</dd>
            </div>
            <div className={`flex justify-between gap-3 font-semibold ${varianceTone(detail.shift.variance)}`}>
              <dt>Variance</dt>
              <dd>{varianceLabel(detail.shift.variance)}</dd>
            </div>
          </>
        ) : null}
      </dl>
      {detail.movements.length > 0 ? (
        <ul className="mt-3 grid gap-1 border-t border-line pt-2 text-xs text-ink-soft">
          {detail.movements.map((movement) => (
            <li key={movement.id} className="flex justify-between gap-3">
              <span>
                {new Date(movement.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} ·{" "}
                {movement.type === "PAY_IN" ? "In" : "Out"}: {movement.reason}
                {movement.createdByName ? ` (${movement.createdByName})` : ""}
              </span>
              <span className="money">{movement.type === "PAY_IN" ? "+" : "−"} {money(movement.amount)}</span>
            </li>
          ))}
        </ul>
      ) : null}
      {detail.shift.notes ? <p className="mt-2 whitespace-pre-line text-xs text-ink-soft">Notes: {detail.shift.notes}</p> : null}
    </div>
  );
}

export default function ShiftsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [shifts, setShifts] = useState<ShiftRecord[]>([]);
  const [history, setHistory] = useState<ShiftRecord[]>([]);
  const [details, setDetails] = useState<Record<string, ShiftDetailRecord>>({});
  const [query, setQuery] = useState("");
  const [branchFilter, setBranchFilter] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [openFieldError, setOpenFieldError] = useState<string>("");
  const [closeFieldErrors, setCloseFieldErrors] = useState<Record<string, string>>({});
  const [openingCash, setOpeningCash] = useState("0");
  const [branchId, setBranchId] = useState("");
  const [openNotes, setOpenNotes] = useState("");
  const [closingCashByShift, setClosingCashByShift] = useState<Record<string, string>>({});
  const [closingNotesByShift, setClosingNotesByShift] = useState<Record<string, string>>({});
  const [movementByShift, setMovementByShift] = useState<Record<string, { type: CashMovementType; amount: string; reason: string }>>({});
  const [busyOpen, setBusyOpen] = useState(false);
  const [busyShiftId, setBusyShiftId] = useState<string | null>(null);

  const matches = (shift: ShiftRecord) => {
    const normalizedQuery = query.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || shift.cashierName.toLowerCase().includes(normalizedQuery)
      || (shift.branchName?.toLowerCase().includes(normalizedQuery) ?? false);
    const matchesBranch = !branchFilter || (shift.branchId ?? "") === branchFilter;
    return matchesQuery && matchesBranch;
  };
  const filteredShifts = shifts.filter(matches);
  const closedShifts = history.filter((shift) => shift.status === "CLOSED").filter(matches);

  const loadData = useCallback(async () => {
    if (!session) return;
    await authenticatedFetch(async (activeSession) => {
      const [nextShifts, nextBranches, nextHistory] = await Promise.all([
        getActiveShifts(activeSession),
        getBranches(activeSession).catch(() => [] as BranchRecord[]),
        getShiftHistory(activeSession),
      ]);
      setShifts(nextShifts);
      setBranches(nextBranches);
      setHistory(nextHistory);
    });
  }, [authenticatedFetch, session]);

  useEffect(() => {
    if (!session) return;
    loadData().catch((err) => setError(err instanceof Error ? err.message : "Could not load shifts"));
  }, [loadData, session]);

  async function toggleDetail(shiftId: string) {
    if (details[shiftId]) {
      setDetails(({ [shiftId]: _removed, ...rest }) => {
        void _removed;
        return rest;
      });
      return;
    }
    try {
      const detail = await authenticatedFetch((activeSession) => getShiftDetail(activeSession, shiftId));
      setDetails((current) => ({ ...current, [shiftId]: detail }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the shift");
    }
  }

  async function handleOpen() {
    if (!session) return;
    const amount = Number.parseFloat(openingCash);
    if (!Number.isFinite(amount) || amount < 0) {
      setOpenFieldError("Opening cash must be a valid non-negative amount");
      return;
    }

    setBusyOpen(true);
    setOpenFieldError("");
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
      setMessage("Shift opened. Cash you take or refund now counts towards this drawer.");
      setOpenNotes("");
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not open shift");
    } finally {
      setBusyOpen(false);
    }
  }

  async function handleMovement(shiftId: string) {
    const draft = movementByShift[shiftId] ?? { type: "PAY_OUT", amount: "", reason: "" };
    const amount = Number.parseFloat(draft.amount);
    if (!Number.isFinite(amount) || amount <= 0 || !draft.reason.trim()) {
      setCloseFieldErrors((current) => ({ ...current, [`move-${shiftId}`]: "Enter an amount and a reason" }));
      return;
    }
    setBusyShiftId(shiftId);
    setCloseFieldErrors((current) => ({ ...current, [`move-${shiftId}`]: "" }));
    setError("");
    setMessage("");
    try {
      const detail = await authenticatedFetch((activeSession) =>
        addCashMovement(activeSession, shiftId, { type: draft.type, amount, reason: draft.reason.trim() }),
      );
      setDetails((current) => ({ ...current, [shiftId]: detail }));
      setMovementByShift((current) => ({ ...current, [shiftId]: { type: draft.type, amount: "", reason: "" } }));
      setMessage(`${draft.type === "PAY_IN" ? "Cash in" : "Cash out"} of ${formatMoney(amount)} recorded.`);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not record the cash movement");
    } finally {
      setBusyShiftId(null);
    }
  }

  async function handleClose(shiftId: string) {
    if (!session) return;
    const raw = closingCashByShift[shiftId] ?? "";
    const amount = Number.parseFloat(raw);
    if (!Number.isFinite(amount) || amount < 0) {
      setCloseFieldErrors((current) => ({ ...current, [shiftId]: "Count the drawer and enter the closing cash" }));
      return;
    }

    setBusyShiftId(shiftId);
    setCloseFieldErrors((current) => ({ ...current, [shiftId]: "" }));
    setError("");
    setMessage("");
    try {
      const closed = await authenticatedFetch((activeSession) =>
        closeShift(activeSession, shiftId, {
          closingCash: amount,
          notes: (closingNotesByShift[shiftId] ?? "").trim() || undefined,
        }),
      );
      setMessage(`Shift closed: expected ${money(closed.expectedCash)}, counted ${money(closed.closingCash)} (${varianceLabel(closed.variance).toLowerCase()}).`);
      setDetails(({ [shiftId]: _removed, ...rest }) => {
        void _removed;
        return rest;
      });
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not close shift");
    } finally {
      setBusyShiftId(null);
    }
  }

  if (loading) {
    return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" />{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-14 w-full" />)}</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Shifts</p>
          <h1 className="heading-lg mt-1">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4">
      <div className="grid gap-4 md:grid-cols-[1fr,1.4fr]">
        <section className="panel">
          <p className="kicker">Cash shifts</p>
          <h1 className="heading-lg mt-1">Open a shift</h1>
          <p className="mt-2 text-sm text-ink-soft">
            Count the float into the drawer. You need an open shift to take or refund cash; it all counts towards your drawer.
          </p>

          <div className="mt-4 grid gap-3">
            <label className="grid gap-2 text-sm text-ink-soft">
              <span>Opening cash (float)</span>
              <input className="input" type="number" min="0" step="0.01" value={openingCash} onChange={(event) => setOpeningCash(event.target.value)} />
              {openFieldError ? <span className="text-xs text-danger-on">{openFieldError}</span> : null}
            </label>

            <label className="grid gap-2 text-sm text-ink-soft">
              <span>Branch</span>
              <select className="input" value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                <option value="">Default / Assigned branch</option>
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>{branch.name}</option>
                ))}
              </select>
            </label>

            <label className="grid gap-2 text-sm text-ink-soft">
              <span>Notes</span>
              <textarea className="min-h-24 input" value={openNotes} onChange={(event) => setOpenNotes(event.target.value)} />
            </label>

            <div aria-live="polite">
              {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
              {message ? <p className="alert alert-success">{message}</p> : null}
            </div>

            <button type="button" className="btn btn-primary btn-md disabled:opacity-60" disabled={busyOpen} onClick={() => void handleOpen()}>
              {busyOpen ? "Opening..." : "Open shift"}
            </button>
          </div>
        </section>

        <section className="panel">
          <p className="kicker">Active shifts</p>
          <h2 className="heading-md">Open drawers</h2>
          <div className="mt-3 grid gap-2 md:grid-cols-2">
            <input className="input text-sm" placeholder="Search cashier or branch" value={query} onChange={(e) => setQuery(e.target.value)} />
            <select className="select text-sm" value={branchFilter} onChange={(e) => setBranchFilter(e.target.value)}>
              <option value="">All branches</option>
              {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
            </select>
          </div>
          <div className="mt-4 grid gap-3">
            {filteredShifts.length === 0 ? (
              <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">No open shifts.</p>
            ) : (
              filteredShifts.map((shift) => {
                const move = movementByShift[shift.id] ?? { type: "PAY_OUT" as CashMovementType, amount: "", reason: "" };
                const busy = busyShiftId === shift.id;
                return (
                  <article key={shift.id} className="rounded-xl border border-line p-4">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <p className="font-semibold">{shift.cashierName}</p>
                        <p className="text-sm text-ink-soft">
                          {shift.branchName ?? "No branch"} · opened {new Date(shift.openedAt).toLocaleString()}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-xs text-ink-soft">Expected in drawer now</p>
                        <p className="money font-display text-lg font-bold">{money(shift.expectedCash)}</p>
                      </div>
                    </div>
                    <button type="button" className="mt-2 text-sm font-semibold text-brand" onClick={() => void toggleDetail(shift.id)} aria-expanded={!!details[shift.id]}>
                      {details[shift.id] ? "Hide breakdown" : "Show breakdown"}
                    </button>
                    {details[shift.id] ? <Breakdown detail={details[shift.id]} /> : null}

                    <fieldset className="mt-3 grid gap-2 rounded-lg border border-line p-3">
                      <legend className="px-1 text-xs font-semibold text-ink">Cash in / out</legend>
                      <div className="grid gap-2 sm:grid-cols-[8rem_7rem_1fr_auto]">
                        <select
                          className="select text-sm"
                          aria-label="Cash in or out"
                          value={move.type}
                          onChange={(e) => setMovementByShift((c) => ({ ...c, [shift.id]: { ...move, type: e.target.value as CashMovementType } }))}
                        >
                          <option value="PAY_OUT">Cash out</option>
                          <option value="PAY_IN">Cash in</option>
                        </select>
                        <input
                          className="input text-sm"
                          type="number"
                          min="0.01"
                          step="0.01"
                          placeholder="Amount"
                          aria-label="Amount"
                          value={move.amount}
                          onChange={(e) => setMovementByShift((c) => ({ ...c, [shift.id]: { ...move, amount: e.target.value } }))}
                        />
                        <input
                          className="input text-sm"
                          placeholder="Reason, e.g. ice from the shop"
                          aria-label="Reason"
                          maxLength={255}
                          value={move.reason}
                          onChange={(e) => setMovementByShift((c) => ({ ...c, [shift.id]: { ...move, reason: e.target.value } }))}
                        />
                        <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => void handleMovement(shift.id)}>
                          Record
                        </button>
                      </div>
                      {closeFieldErrors[`move-${shift.id}`] ? <span className="text-xs text-danger-on">{closeFieldErrors[`move-${shift.id}`]}</span> : null}
                    </fieldset>

                    <div className="mt-3 grid gap-2 md:grid-cols-2">
                      <div className="grid gap-1">
                        <input
                          className="input text-sm"
                          type="number"
                          min="0"
                          step="0.01"
                          placeholder="Counted cash"
                          aria-label="Counted closing cash"
                          value={closingCashByShift[shift.id] ?? ""}
                          onChange={(event) => setClosingCashByShift((current) => ({ ...current, [shift.id]: event.target.value }))}
                        />
                        {closeFieldErrors[shift.id] ? <span className="text-xs text-danger-on">{closeFieldErrors[shift.id]}</span> : null}
                      </div>
                      <input
                        className="input text-sm"
                        type="text"
                        placeholder="Close notes"
                        aria-label="Close notes"
                        value={closingNotesByShift[shift.id] ?? ""}
                        onChange={(event) => setClosingNotesByShift((current) => ({ ...current, [shift.id]: event.target.value }))}
                      />
                    </div>

                    <button type="button" className="mt-3 btn btn-secondary disabled:opacity-60" disabled={busy} onClick={() => void handleClose(shift.id)}>
                      {busy ? "Working..." : "Close shift"}
                    </button>
                  </article>
                );
              })
            )}
          </div>
        </section>
      </div>

      <section className="panel">
        <p className="kicker">History</p>
        <h2 className="heading-md">Closed shifts (last 30 days)</h2>
        {closedShifts.length === 0 ? (
          <p className="mt-3 text-sm text-ink-soft">No closed shifts in this period.</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead>
                <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-soft">
                  <th className="py-2 pr-3">Cashier</th>
                  <th className="py-2 pr-3">Branch</th>
                  <th className="py-2 pr-3">Opened</th>
                  <th className="py-2 pr-3">Closed</th>
                  <th className="py-2 pr-3 text-right">Expected</th>
                  <th className="py-2 pr-3 text-right">Counted</th>
                  <th className="py-2 pr-3 text-right">Variance</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {closedShifts.map((shift) => (
                  <tr key={shift.id} className="border-b border-line-subtle align-top">
                    <td className="py-2 pr-3 font-semibold">{shift.cashierName}</td>
                    <td className="py-2 pr-3 text-ink-soft">{shift.branchName ?? "—"}</td>
                    <td className="py-2 pr-3 text-ink-soft">{new Date(shift.openedAt).toLocaleString()}</td>
                    <td className="py-2 pr-3 text-ink-soft">{shift.closedAt ? new Date(shift.closedAt).toLocaleString() : "—"}</td>
                    <td className="money py-2 pr-3 text-right">{money(shift.expectedCash)}</td>
                    <td className="money py-2 pr-3 text-right">{money(shift.closingCash)}</td>
                    <td className={`py-2 pr-3 text-right font-semibold ${varianceTone(shift.variance)}`}>{varianceLabel(shift.variance)}</td>
                    <td className="py-2">
                      <button type="button" className="text-sm font-semibold text-brand" onClick={() => void toggleDetail(shift.id)} aria-expanded={!!details[shift.id]}>
                        {details[shift.id] ? "Hide" : "Details"}
                      </button>
                      {details[shift.id] ? <div className="min-w-[260px]"><Breakdown detail={details[shift.id]} /></div> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
