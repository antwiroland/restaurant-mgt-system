"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import { formatMinutes } from "@/features/workforce/time";
import { hhmm } from "@/features/hr/schedule/scheduleUtils";
import {
  getPosRoster,
  newRequestKey,
  posClock,
  posSummary,
  type BreakType,
  type ClockAction,
  type PosSummary,
  type RosterEntry,
} from "@/lib/hrApi";

const TERMINAL_KEY = "rm.pos.terminalId";

/** Stable, non-secret label for this device so attendance records show which till was used. */
function terminalId(): string {
  try {
    const existing = window.localStorage.getItem(TERMINAL_KEY);
    if (existing) return existing;
    const created = `till-${newRequestKey().slice(0, 8)}`;
    window.localStorage.setItem(TERMINAL_KEY, created);
    return created;
  } catch {
    return "till";
  }
}

/** A network failure (not an answer from the server) is retried with the same key, so it can never double-clock. */
async function withRetry<T>(run: () => Promise<T>, attempts = 3): Promise<T> {
  let lastError: unknown;
  for (let i = 0; i < attempts; i++) {
    try {
      return await run();
    } catch (err) {
      lastError = err;
      if (!(err instanceof TypeError)) throw err;
      await new Promise((resolve) => setTimeout(resolve, 600 * (i + 1)));
    }
  }
  throw lastError;
}

type Step = { kind: "pick" } | { kind: "pin"; person: RosterEntry; then: "act" | "info" } | { kind: "done"; message: string; tone: "ok" | "error" };

/**
 * Staff clock for the POS. Anyone on the branch roster picks their name and enters their own clock PIN. Using it never
 * signs anyone in to the POS, changes the signed-in account or affects orders.
 */
export function StaffClockPanel({ onClose }: { onClose: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [roster, setRoster] = useState<RosterEntry[] | null>(null);
  const [error, setError] = useState("");
  const [step, setStep] = useState<Step>({ kind: "pick" });
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [info, setInfo] = useState<PosSummary | null>(null);
  const [query, setQuery] = useState("");
  const resetTimer = useRef<number | null>(null);

  const loadRoster = useCallback(async () => {
    setError("");
    try {
      setRoster(await authenticatedFetch((s) => getPosRoster(s)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load staff");
      setRoster([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void loadRoster();
    return () => { if (resetTimer.current) window.clearTimeout(resetTimer.current); };
  }, [loadRoster]);

  function finish(message: string, tone: "ok" | "error") {
    setPin("");
    setStep({ kind: "done", message, tone });
    if (resetTimer.current) window.clearTimeout(resetTimer.current);
    resetTimer.current = window.setTimeout(() => { setStep({ kind: "pick" }); setInfo(null); setQuery(""); }, tone === "ok" ? 3500 : 5000);
    void loadRoster();
  }

  async function act(person: RosterEntry, action: ClockAction, breakType?: BreakType) {
    if (pin.length < 4) return;
    setBusy(true);
    const key = newRequestKey();
    const enteredPin = pin;
    setPin("");
    try {
      const result = await withRetry(() => authenticatedFetch((s) => posClock(s, {
        employeeId: person.employeeId, pin: enteredPin, action, breakType, idempotencyKey: key, terminalId: terminalId(),
      })));
      finish(result.message, "ok");
    } catch (err) {
      finish(err instanceof TypeError ? "No connection to the server. Nothing was recorded — please try again." : err instanceof Error ? err.message : "Something went wrong", "error");
    } finally {
      setBusy(false);
    }
  }

  async function showInfo(person: RosterEntry) {
    if (pin.length < 4) return;
    setBusy(true);
    const enteredPin = pin;
    setPin("");
    try {
      setInfo(await authenticatedFetch((s) => posSummary(s, { employeeId: person.employeeId, pin: enteredPin })));
      setStep({ kind: "done", message: "", tone: "ok" });
      if (resetTimer.current) window.clearTimeout(resetTimer.current);
      resetTimer.current = window.setTimeout(() => { setStep({ kind: "pick" }); setInfo(null); }, 15000);
    } catch (err) {
      finish(err instanceof Error ? err.message : "Could not check", "error");
    } finally {
      setBusy(false);
    }
  }

  const visible = (roster ?? []).filter((r) => !query || r.displayName.toLowerCase().includes(query.toLowerCase()));

  return (
    <Modal open title="Staff clock" onClose={onClose}>
      <div className="grid gap-4" aria-live="polite">
        {step.kind === "pick" ? (
          <>
            <p className="text-sm text-ink-soft">Tap your name, then enter your clock PIN. This does not sign you in to the till.</p>
            {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
            {(roster?.length ?? 0) > 8 ? (
              <input className="input" placeholder="Find your name" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Find your name" />
            ) : null}
            {roster === null ? <Skeleton className="h-40 w-full" /> : visible.length === 0 ? (
              <p className="text-sm text-ink-soft">Nobody is assigned to this branch today.</p>
            ) : (
              <div className="grid max-h-[50vh] grid-cols-2 gap-2 overflow-y-auto sm:grid-cols-3">
                {visible.map((r) => (
                  <button
                    key={r.employeeId}
                    type="button"
                    disabled={!r.hasPin}
                    onClick={() => { setPin(""); setStep({ kind: "pin", person: r, then: "act" }); }}
                    className="card card-interactive min-h-[72px] p-3 text-left disabled:opacity-50"
                  >
                    <span className="block font-semibold">{r.displayName}</span>
                    <span className="block text-xs text-ink-muted">
                      {r.state === "ON_SHIFT" ? "Clocked in" : r.state === "ON_BREAK" ? "On break" : r.nextShiftAt ? `Shift ${hhmm(r.nextShiftAt)}` : r.positionTitle ?? ""}
                      {!r.hasPin ? " · no PIN set" : ""}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </>
        ) : null}

        {step.kind === "pin" ? (
          <PinStep
            person={step.person}
            pin={pin}
            setPin={setPin}
            busy={busy}
            onBack={() => { setPin(""); setStep({ kind: "pick" }); }}
            onAction={(action, breakType) => act(step.person, action, breakType)}
            onInfo={() => showInfo(step.person)}
          />
        ) : null}

        {step.kind === "done" && !info ? (
          <div className={`rounded-xl p-6 text-center ${step.tone === "ok" ? "bg-success-subtle text-success-on" : "bg-danger-subtle text-danger-on"}`} role="status">
            <Icon name={step.tone === "ok" ? "checkCircle" : "alert"} size={36} className="mx-auto" />
            <p className="mt-2 text-lg font-semibold">{step.message}</p>
          </div>
        ) : null}

        {step.kind === "done" && info ? (
          <div className="grid gap-3 text-sm">
            <p className="text-lg font-semibold">{info.displayName}</p>
            {info.current ? (
              <p>Clocked in since {hhmm(info.current.clockInAt)} · {formatMinutes(info.current.metrics.paidMinutes)} paid so far{info.current.onBreak ? " · on break" : ""}</p>
            ) : <p>Not clocked in.</p>}
            <div>
              <p className="font-semibold">Upcoming shifts</p>
              {info.upcomingShifts.length === 0 ? <p className="text-ink-soft">None in the next 7 days.</p> : (
                <ul className="mt-1 grid gap-1">
                  {info.upcomingShifts.map((s) => (
                    <li key={s.id} className="tabular">{new Date(s.startsAt).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })} {hhmm(s.startsAt)}–{hhmm(s.endsAt)}</li>
                  ))}
                </ul>
              )}
            </div>
            <button type="button" className="btn btn-secondary btn-md" onClick={() => { setInfo(null); setStep({ kind: "pick" }); }}>Done</button>
          </div>
        ) : null}
      </div>
    </Modal>
  );
}

function PinStep({ person, pin, setPin, busy, onBack, onAction, onInfo }: {
  person: RosterEntry; pin: string; setPin: (pin: string) => void; busy: boolean; onBack: () => void;
  onAction: (action: ClockAction, breakType?: BreakType) => void; onInfo: () => void;
}) {
  const ready = pin.length >= 4 && !busy;
  const press = (digit: string) => { if (pin.length < 6) setPin(pin + digit); };
  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between">
        <button type="button" className="btn btn-ghost btn-sm" onClick={onBack}><Icon name="arrowLeft" size={16} /> Back</button>
        <p className="font-semibold">{person.displayName}</p>
      </div>
      <div className="flex justify-center gap-2" aria-label={`${pin.length} digits entered`}>
        {Array.from({ length: 6 }, (_, i) => (
          <span key={i} className={`h-3.5 w-3.5 rounded-full ${i < pin.length ? "bg-ink" : "bg-line"}`} />
        ))}
      </div>
      <div className="mx-auto grid w-full max-w-xs grid-cols-3 gap-2">
        {["1", "2", "3", "4", "5", "6", "7", "8", "9"].map((d) => (
          <button key={d} type="button" className="btn btn-secondary btn-lg tabular" onClick={() => press(d)} disabled={busy}>{d}</button>
        ))}
        <button type="button" className="btn btn-ghost btn-lg" onClick={() => setPin("")} disabled={busy} aria-label="Clear">C</button>
        <button type="button" className="btn btn-secondary btn-lg tabular" onClick={() => press("0")} disabled={busy}>0</button>
        <button type="button" className="btn btn-ghost btn-lg" onClick={() => setPin(pin.slice(0, -1))} disabled={busy} aria-label="Delete digit"><Icon name="arrowLeft" size={18} /></button>
      </div>
      <div className="grid gap-2">
        {person.state === "OFF" ? (
          <button type="button" className="btn btn-primary btn-lg" disabled={!ready} onClick={() => onAction("CLOCK_IN")}>Clock in</button>
        ) : null}
        {person.state === "ON_SHIFT" ? (
          <div className="grid grid-cols-2 gap-2">
            <button type="button" className="btn btn-secondary btn-lg" disabled={!ready} onClick={() => onAction("BREAK_START", "MEAL")}>Meal break</button>
            <button type="button" className="btn btn-secondary btn-lg" disabled={!ready} onClick={() => onAction("BREAK_START", "REST")}>Short break</button>
          </div>
        ) : null}
        {person.state === "ON_BREAK" ? (
          <button type="button" className="btn btn-primary btn-lg" disabled={!ready} onClick={() => onAction("BREAK_END")}>End break</button>
        ) : null}
        {person.state !== "OFF" ? (
          <button type="button" className="btn btn-danger btn-lg" disabled={!ready} onClick={() => onAction("CLOCK_OUT")}>Clock out</button>
        ) : null}
        <button type="button" className="btn btn-ghost btn-md" disabled={!ready} onClick={onInfo}>My shifts &amp; hours</button>
      </div>
    </div>
  );
}
