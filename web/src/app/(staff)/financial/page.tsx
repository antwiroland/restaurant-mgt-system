"use client";

import Link from "next/link";
import { useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { applyFinancialAction, verifyManagerPin, type OverrideActionType } from "@/lib/apiClient";

export default function FinancialPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [pin, setPin] = useState("");
  const [actionType, setActionType] = useState<OverrideActionType>("DISCOUNT");
  const [overrideToken, setOverrideToken] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function generateToken() {
    if (!session) return;
    try {
      setError("");
      setMessage("");
      const result = await authenticatedFetch((activeSession) => verifyManagerPin(activeSession, { pin, actionType }));
      setOverrideToken(result.overrideToken);
      setMessage(`${result.actionType} override token generated`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not verify manager PIN");
    }
  }

  async function runAction(action: "discount" | "void" | "refund") {
    if (!session) return;
    if (!overrideToken) {
      setError("Override token is required");
      return;
    }
    try {
      setError("");
      const result = await authenticatedFetch((activeSession) => applyFinancialAction(activeSession, action, overrideToken));
      setMessage(`${action.toUpperCase()} accepted (${result.actionType || "OK"})`);
    } catch (err) {
      setError(err instanceof Error ? err.message : `Could not apply ${action}`);
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading financial tools...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Financial</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4 md:grid-cols-2">
      <section className="panel">
        <p className="kicker">Financial</p>
        <h1 className="text-2xl font-semibold">Override Token</h1>
        <p className="mt-2 text-sm text-ink-soft">Manager can generate token with PIN, or paste an existing token below.</p>

        <div className="mt-3 grid gap-2">
          <input className="input" inputMode="numeric" placeholder="Manager PIN (4 digits)" value={pin} onChange={(e) => setPin(e.target.value)} maxLength={4} />
          <select className="input" value={actionType} onChange={(e) => setActionType(e.target.value as OverrideActionType)}>
            <option value="DISCOUNT">DISCOUNT</option>
            <option value="VOID">VOID</option>
            <option value="REFUND">REFUND</option>
          </select>
          <button className="btn btn-secondary btn-sm" onClick={() => void generateToken()}>Verify PIN + Generate Token</button>
        </div>

        <textarea className="mt-3 w-full input text-xs" rows={5} placeholder="Override token" value={overrideToken} onChange={(e) => setOverrideToken(e.target.value)} />
      </section>

      <section className="panel">
        <h2 className="text-xl font-semibold">Apply Action</h2>
        <div className="mt-3 flex flex-wrap gap-2">
          <button className="btn btn-secondary btn-sm" onClick={() => void runAction("discount")}>Apply Discount</button>
          <button className="btn btn-secondary btn-sm" onClick={() => void runAction("void")}>Apply Void</button>
          <button className="btn btn-secondary btn-sm" onClick={() => void runAction("refund")}>Apply Refund</button>
        </div>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>
    </main>
  );
}


