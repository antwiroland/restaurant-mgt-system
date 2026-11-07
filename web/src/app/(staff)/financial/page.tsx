"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  applyOrderDiscount,
  getFinancialAudit,
  getReconciliation,
  refundPayment,
  signOffReconciliation,
  verifyManagerPin,
  voidOrder,
  type DiscountMode,
  type FinancialAuditRecord,
  type OverrideActionType,
  type ReconciliationReport,
} from "@/lib/apiClient";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function money(value?: string | null): string {
  if (value === undefined || value === null) return "-";
  return `GHS ${Number(value).toFixed(2)}`;
}

export default function FinancialPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const isManager = session?.user.role === "ADMIN" || session?.user.role === "MANAGER";

  const [pin, setPin] = useState("");
  const [actionType, setActionType] = useState<OverrideActionType>("DISCOUNT");
  const [overrideToken, setOverrideToken] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const [discountOrderId, setDiscountOrderId] = useState("");
  const [discountMode, setDiscountMode] = useState<DiscountMode>("PERCENTAGE");
  const [discountValue, setDiscountValue] = useState("");
  const [discountReason, setDiscountReason] = useState("");

  const [voidOrderId, setVoidOrderId] = useState("");
  const [voidReason, setVoidReason] = useState("");

  const [refundPaymentId, setRefundPaymentId] = useState("");
  const [refundAmount, setRefundAmount] = useState("");
  const [refundReason, setRefundReason] = useState("");

  const [businessDate, setBusinessDate] = useState(todayIso());
  const [report, setReport] = useState<ReconciliationReport | null>(null);
  const [countedCash, setCountedCash] = useState("");
  const [signOffNotes, setSignOffNotes] = useState("");
  const [audit, setAudit] = useState<FinancialAuditRecord[]>([]);

  const loadManagerData = useCallback(async () => {
    if (!session || !isManager) return;
    try {
      const [nextReport, nextAudit] = await Promise.all([
        authenticatedFetch((s) => getReconciliation(s, businessDate)),
        authenticatedFetch((s) => getFinancialAudit(s)),
      ]);
      setReport(nextReport);
      setAudit(nextAudit);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load reconciliation");
    }
  }, [authenticatedFetch, businessDate, isManager, session]);

  useEffect(() => {
    void loadManagerData();
  }, [loadManagerData]);

  async function run<T>(label: string, action: () => Promise<T>, describe: (result: T) => string) {
    setError("");
    setMessage("");
    if (!overrideToken) {
      setError("Generate or paste a manager override token first");
      return;
    }
    setBusy(true);
    try {
      const result = await action();
      setMessage(describe(result));
      setOverrideToken("");
      await loadManagerData();
    } catch (err) {
      setError(err instanceof Error ? err.message : `Could not ${label}`);
    } finally {
      setBusy(false);
    }
  }

  async function generateToken() {
    setError("");
    setMessage("");
    try {
      const result = await authenticatedFetch((s) => verifyManagerPin(s, { pin, actionType }));
      setOverrideToken(result.overrideToken);
      setPin("");
      setMessage(`${result.actionType} override token generated (valid for 5 minutes)`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not verify manager PIN");
    }
  }

  if (loading) return <main className="shell"><section className="panel" aria-busy="true">Loading financial tools...</section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Financial</p>
          <h1 className="heading-lg mt-1">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 lg:grid-cols-2">
      <section className="panel">
        <p className="kicker">Financial</p>
        <h1 className="heading-lg mt-1">Manager approval</h1>
        <p className="mt-2 text-sm text-ink-soft">A manager enters their PIN to approve one action type. The token is used once below.</p>
        <div className="mt-3 grid gap-2">
          <label className="grid gap-1 text-sm">
            <span>Manager PIN</span>
            <input className="input" type="password" inputMode="numeric" autoComplete="off" value={pin} onChange={(e) => setPin(e.target.value)} maxLength={6} />
          </label>
          <label className="grid gap-1 text-sm">
            <span>Approves</span>
            <select className="select" value={actionType} onChange={(e) => setActionType(e.target.value as OverrideActionType)}>
              <option value="DISCOUNT">Discount</option>
              <option value="VOID">Void</option>
              <option value="REFUND">Refund</option>
            </select>
          </label>
          <button type="button" className="btn btn-secondary" onClick={() => void generateToken()} disabled={!pin}>Verify PIN</button>
        </div>
        <label className="mt-3 grid gap-1 text-sm">
          <span>Override token</span>
          <textarea className="input text-xs" rows={3} value={overrideToken} onChange={(e) => setOverrideToken(e.target.value)} />
        </label>
        <div aria-live="polite">
          {error ? <p className="mt-3 alert alert-danger" role="alert">{error}</p> : null}
          {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
        </div>
      </section>

      <section className="panel grid gap-4">
        <form
          className="grid gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void run(
              "apply discount",
              () => authenticatedFetch((s) => applyOrderDiscount(s, {
                overrideToken,
                orderId: discountOrderId.trim(),
                mode: discountMode,
                value: Number(discountValue),
                reason: discountReason || undefined,
              })),
              (r) => `Discount of ${money(r.discountAmount)} applied. New total ${money(r.newTotal)}`,
            );
          }}
        >
          <h2 className="font-display text-lg font-bold text-ink">Discount an order</h2>
          <input className="input" placeholder="Order ID" aria-label="Order ID to discount" value={discountOrderId} onChange={(e) => setDiscountOrderId(e.target.value)} required />
          <div className="grid grid-cols-2 gap-2">
            <select className="select" aria-label="Discount type" value={discountMode} onChange={(e) => setDiscountMode(e.target.value as DiscountMode)}>
              <option value="PERCENTAGE">Percent (%)</option>
              <option value="FLAT">Amount (GHS)</option>
            </select>
            <input className="input" type="number" min="0.01" step="0.01" aria-label="Discount value" placeholder="Value" value={discountValue} onChange={(e) => setDiscountValue(e.target.value)} required />
          </div>
          <input className="input" placeholder="Reason (optional)" aria-label="Discount reason" value={discountReason} onChange={(e) => setDiscountReason(e.target.value)} />
          <button type="submit" className="btn btn-primary" disabled={busy}>Apply discount</button>
        </form>

        <form
          className="grid gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void run(
              "void order",
              () => authenticatedFetch((s) => voidOrder(s, { overrideToken, orderId: voidOrderId.trim(), reason: voidReason || undefined })),
              (r) => `Order voided (${money(r.voidedAmount)}); ${r.paymentsVoided} open payment(s) cancelled`,
            );
          }}
        >
          <h2 className="font-display text-lg font-bold text-ink">Void an unpaid order</h2>
          <input className="input" placeholder="Order ID" aria-label="Order ID to void" value={voidOrderId} onChange={(e) => setVoidOrderId(e.target.value)} required />
          <input className="input" placeholder="Reason" aria-label="Void reason" value={voidReason} onChange={(e) => setVoidReason(e.target.value)} />
          <button type="submit" className="btn btn-danger" disabled={busy}>Void order</button>
        </form>

        <form
          className="grid gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void run(
              "refund payment",
              () => authenticatedFetch((s) => refundPayment(s, {
                overrideToken,
                paymentId: refundPaymentId.trim(),
                amount: refundAmount ? Number(refundAmount) : undefined,
                reason: refundReason || undefined,
              })),
              (r) => `Refunded ${money(r.amount)} (${r.providerStatus}). Remaining refundable ${money(r.remainingRefundable)}`,
            );
          }}
        >
          <h2 className="font-display text-lg font-bold text-ink">Refund a payment</h2>
          <input className="input" placeholder="Payment ID" aria-label="Payment ID to refund" value={refundPaymentId} onChange={(e) => setRefundPaymentId(e.target.value)} required />
          <input className="input" type="number" min="0.01" step="0.01" placeholder="Amount (blank = full)" aria-label="Refund amount" value={refundAmount} onChange={(e) => setRefundAmount(e.target.value)} />
          <input className="input" placeholder="Reason" aria-label="Refund reason" value={refundReason} onChange={(e) => setRefundReason(e.target.value)} />
          <button type="submit" className="btn btn-danger" disabled={busy}>Refund</button>
        </form>
      </section>

      {isManager ? (
        <section className="panel lg:col-span-2">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="kicker">Daily reconciliation</p>
              <h2 className="heading-md">Close of day</h2>
            </div>
            <label className="grid gap-1 text-sm">
              <span>Business date</span>
              <input className="input" type="date" value={businessDate} max={todayIso()} onChange={(e) => setBusinessDate(e.target.value)} />
            </label>
          </div>

          {report ? (
            <>
              <dl className="mt-4 grid grid-cols-2 gap-3 md:grid-cols-4">
                {[
                  ["Gross sales", money(report.grossSales)],
                  ["Refunds", money(report.totalRefunds)],
                  ["Voids", money(report.totalVoids)],
                  ["Discounts", money(report.totalDiscounts)],
                  ["Net sales", money(report.netSales)],
                  ["Expected cash", money(report.expectedCash)],
                  ["Paid orders", String(report.paidOrders)],
                  ["Closed shifts", String(report.shifts.closedShifts)],
                ].map(([label, value]) => (
                  <div key={label} className="card">
                    <dt className="text-xs text-ink-soft">{label}</dt>
                    <dd className="text-lg font-semibold">{value}</dd>
                  </div>
                ))}
              </dl>
              {report.byMethod.length ? (
                <p className="mt-3 text-sm text-ink-soft">
                  {report.byMethod.map((m) => `${m.method.replace("_", " ")}: ${money(m.amount)} (${m.count})`).join(" · ")}
                </p>
              ) : null}

              {report.signOff ? (
                <p className="mt-4 alert alert-success">
                  Signed off {new Date(report.signOff.signedAt).toLocaleString()}
                  {report.signOff.countedCash ? `, counted ${money(report.signOff.countedCash)} (variance ${money(report.signOff.cashVariance)})` : ""}
                </p>
              ) : (
                <form
                  className="mt-4 grid gap-2 md:grid-cols-[1fr_2fr_auto]"
                  onSubmit={(e) => {
                    e.preventDefault();
                    void run(
                      "sign off",
                      () => authenticatedFetch((s) => signOffReconciliation(s, businessDate, {
                        overrideToken,
                        countedCash: countedCash ? Number(countedCash) : undefined,
                        notes: signOffNotes || undefined,
                      })),
                      () => `Reconciliation for ${businessDate} signed off`,
                    );
                  }}
                >
                  <input className="input" type="number" step="0.01" min="0" placeholder="Counted cash (GHS)" aria-label="Counted cash" value={countedCash} onChange={(e) => setCountedCash(e.target.value)} />
                  <input className="input" placeholder="Notes" aria-label="Sign-off notes" value={signOffNotes} onChange={(e) => setSignOffNotes(e.target.value)} />
                  <button type="submit" className="btn btn-primary" disabled={busy}>Sign off day</button>
                </form>
              )}
            </>
          ) : (
            <p className="mt-4 text-sm text-ink-soft" aria-busy="true">Loading report...</p>
          )}

          <h3 className="mt-6 text-lg font-semibold">Financial audit trail</h3>
          {audit.length === 0 ? (
            <p className="mt-2 text-sm text-ink-soft">No financial actions recorded yet.</p>
          ) : (
            <div className="mt-2 overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr className="text-left text-ink-soft">
                    <th scope="col" className="py-2">When</th>
                    <th scope="col">Action</th>
                    <th scope="col">Approved by</th>
                    <th scope="col">Target</th>
                  </tr>
                </thead>
                <tbody>
                  {audit.slice(0, 50).map((entry) => (
                    <tr key={entry.id} className="border-t border-[var(--color-border-default)]">
                      <td className="py-2">{new Date(entry.createdAt).toLocaleString()}</td>
                      <td><span className="badge">{entry.action.replaceAll("_", " ")}</span></td>
                      <td>{entry.actorName ?? "-"}</td>
                      <td className="font-mono text-xs">{entry.entityType} {entry.entityId?.slice(0, 8)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </main>
  );
}
