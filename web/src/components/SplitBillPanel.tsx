"use client";

import { useCallback, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createOrderSplits,
  getOrderSplits,
  initiatePayment,
  type OrderRecord,
  type PaymentMethod,
  type PaymentSplitRecord,
  type SplitMode,
} from "@/lib/apiClient";

type SplitBillPanelProps = {
  order: OrderRecord;
  onPaid?: () => void;
};

const STATUS_BADGE: Record<PaymentSplitRecord["status"], string> = {
  PENDING: "badge-warning",
  PAID: "badge-success",
  CANCELLED: "badge-neutral",
};

/**
 * Splits an order's bill into shares (equally, or by each group participant's items)
 * and takes a separate payment for each share.
 */
export function SplitBillPanel({ order, onPaid }: SplitBillPanelProps) {
  const { authenticatedFetch } = useStaffSession();
  const [splits, setSplits] = useState<PaymentSplitRecord[]>([]);
  const [mode, setMode] = useState<SplitMode>(order.items.some((item) => item.participantId) ? "BY_ITEMS" : "EQUAL");
  const [parts, setParts] = useState("2");
  const [names, setNames] = useState("");
  const [payingId, setPayingId] = useState<string | null>(null);
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [momoPhone, setMomoPhone] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setSplits(await authenticatedFetch((s) => getOrderSplits(s, order.id)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load bill splits");
    }
  }, [authenticatedFetch, order.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const active = splits.filter((split) => split.status !== "CANCELLED");
  const anyPaid = active.some((split) => split.status === "PAID");
  const paidTotal = active.filter((s) => s.status === "PAID").reduce((sum, s) => sum + Number(s.amount), 0);
  const closed = order.status === "CANCELLED" || order.status === "VOIDED";

  async function createSplits(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const nameList = names.split(",").map((n) => n.trim()).filter(Boolean);
      const next = await authenticatedFetch((s) =>
        createOrderSplits(s, order.id, {
          mode,
          parts: mode === "EQUAL" ? Number(parts) : undefined,
          names: mode === "EQUAL" && nameList.length > 0 ? nameList : undefined,
        }),
      );
      setSplits(next);
      setMessage(`Bill split into ${next.length} shares`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not split the bill");
    } finally {
      setBusy(false);
    }
  }

  async function pay(split: PaymentSplitRecord) {
    if (method === "MOBILE_MONEY" && !momoPhone.trim()) {
      setError("Enter the Mobile Money number for this share");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await authenticatedFetch((s) =>
        initiatePayment(s, {
          orderId: order.id,
          splitId: split.id,
          method,
          // The backend requires a phone; cash and card payments taken at the counter use a placeholder.
          momoPhone: momoPhone.trim() || "0000000000",
          idempotencyKey: `${split.id}-${Date.now()}`,
        }),
      );
      setMessage(
        result.status === "SUCCESS"
          ? `${split.displayName}'s share is paid`
          : `Payment started for ${split.displayName} (${result.status})${result.message ? `: ${result.message}` : ""}`,
      );
      setPayingId(null);
      await load();
      onPaid?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not take payment for this share");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel grid gap-3" aria-labelledby="split-bill-heading">
      <div>
        <h2 id="split-bill-heading" className="text-lg font-semibold">Split bill</h2>
        <p className="text-sm text-ink-soft">
          Each share is paid separately. Shares can be changed until the first one is paid.
        </p>
      </div>

      {!anyPaid && !closed ? (
        <form className="grid gap-2 sm:grid-cols-[auto_auto_1fr_auto] sm:items-end" onSubmit={(e) => void createSplits(e)}>
          <label className="grid gap-1 text-sm">
            <span>Split</span>
            <select className="select" value={mode} onChange={(e) => setMode(e.target.value as SplitMode)}>
              <option value="EQUAL">Equally</option>
              <option value="BY_ITEMS">By guest items (group order)</option>
            </select>
          </label>
          {mode === "EQUAL" ? (
            <>
              <label className="grid gap-1 text-sm">
                <span>Shares</span>
                <input className="input w-24" type="number" min={2} max={20} required value={parts} onChange={(e) => setParts(e.target.value)} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Names (optional, comma separated)</span>
                <input className="input" placeholder="Ama, Kofi" value={names} onChange={(e) => setNames(e.target.value)} />
              </label>
            </>
          ) : (
            <p className="text-sm text-ink-soft sm:col-span-2">Each group participant pays for their own items plus a fair part of fees and taxes.</p>
          )}
          <button type="submit" className="btn btn-secondary" disabled={busy}>
            {active.length > 0 ? "Re-split" : "Split bill"}
          </button>
        </form>
      ) : null}

      {active.length > 0 ? (
        <>
          <ul className="grid gap-2">
            {active.map((split) => (
              <li key={split.id} className="card grid gap-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-semibold">{split.displayName}</p>
                    <p className="text-sm text-ink-soft">GHS {Number(split.amount).toFixed(2)}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`badge ${STATUS_BADGE[split.status]}`}>{split.status}</span>
                    {split.status === "PENDING" && !closed ? (
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        onClick={() => setPayingId(payingId === split.id ? null : split.id)}
                        aria-expanded={payingId === split.id}
                      >
                        Take payment
                      </button>
                    ) : null}
                  </div>
                </div>
                {payingId === split.id ? (
                  <div className="grid gap-2 sm:grid-cols-[auto_1fr_auto] sm:items-end">
                    <label className="grid gap-1 text-sm">
                      <span>Method</span>
                      <select className="select" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
                        <option value="CASH">Cash</option>
                        <option value="MOBILE_MONEY">Mobile Money</option>
                        <option value="CARD">Card</option>
                      </select>
                    </label>
                    <label className="grid gap-1 text-sm">
                      <span>MoMo phone {method === "MOBILE_MONEY" ? "" : "(not needed)"}</span>
                      <input
                        className="input"
                        inputMode="tel"
                        placeholder="024..."
                        value={momoPhone}
                        disabled={method !== "MOBILE_MONEY"}
                        onChange={(e) => setMomoPhone(e.target.value)}
                      />
                    </label>
                    <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void pay(split)}>
                      Pay GHS {Number(split.amount).toFixed(2)}
                    </button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
          <p className="text-sm text-ink-soft">
            Paid GHS {paidTotal.toFixed(2)} of GHS {Number(order.total).toFixed(2)} ·{" "}
            {active.filter((s) => s.status === "PAID").length}/{active.length} shares settled
          </p>
        </>
      ) : null}

      <div aria-live="polite">
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {message ? <p className="alert alert-success">{message}</p> : null}
      </div>
    </section>
  );
}
