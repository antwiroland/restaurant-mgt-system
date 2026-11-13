"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createGuestSplits,
  getGuestSplits,
  payGuestOrder,
  payGuestSplit,
  submitGuestFeedback,
  verifyGuestPayment,
  type PaymentSplitRecord,
  type PublicOrderTrackingRecord,
} from "@/lib/apiClient";

type Props = {
  tableToken: string;
  order: PublicOrderTrackingRecord;
  /** Bumped by the page when realtime events arrive, so shares refresh without polling. */
  refreshKey?: number;
  /** The phone the guest gave on arrival, offered as the Mobile Money number. */
  guestPhone?: string;
};

const OPEN_STATUSES: PublicOrderTrackingRecord["status"][] = ["PENDING", "CONFIRMED", "PREPARING", "READY"];

function newKey(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
}

/** Pay (whole or split, while the order is open) and rating (once completed) for one guest table order. */
export function GuestOrderActions({ tableToken, order, refreshKey = 0, guestPhone }: Props) {
  const open = OPEN_STATUSES.includes(order.status);
  if (order.status === "COMPLETED") return <GuestRating tableToken={tableToken} orderId={order.orderId} />;
  if (open) return <GuestSplitPay tableToken={tableToken} order={order} refreshKey={refreshKey} guestPhone={guestPhone} />;
  return null;
}

function GuestSplitPay({ tableToken, order, refreshKey, guestPhone }: { tableToken: string; order: PublicOrderTrackingRecord; refreshKey: number; guestPhone?: string }) {
  const orderId = order.orderId;
  const [mode, setMode] = useState<"none" | "whole" | "split">("none");
  const [splits, setSplits] = useState<PaymentSplitRecord[]>([]);
  const [parts, setParts] = useState("2");
  const [names, setNames] = useState("");
  const [payingId, setPayingId] = useState<string | null>(null);
  const [phone, setPhone] = useState(guestPhone ?? "");
  const [pending, setPending] = useState<{ splitId: string | null; paymentId: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    try {
      const next = await getGuestSplits(tableToken, orderId);
      setSplits(next.filter((split) => split.status !== "CANCELLED"));
    } catch {
      setSplits([]);
    }
  }, [orderId, tableToken]);

  // Shares decide which payment buttons to offer, so load them up front and on every live update.
  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  useEffect(() => {
    if (guestPhone) setPhone((current) => current || guestPhone);
  }, [guestPhone]);

  const total = Number(order.total ?? 0);
  const paidSoFar = Number(order.amountPaid ?? 0);
  const fullyPaid = total > 0 && paidSoFar >= total;

  async function payWhole() {
    if (!phone.trim()) {
      setError("Enter the Mobile Money number to charge");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await payGuestOrder(tableToken, orderId, phone.trim(), newKey());
      setPending({ splitId: null, paymentId: result.paymentId });
      setMessage(result.message || `Approve the MoMo prompt on ${phone.trim()} to pay GHS ${total.toFixed(2)}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start the payment");
    } finally {
      setBusy(false);
    }
  }

  const anyPaid = splits.some((split) => split.status === "PAID");
  const paidCount = splits.filter((split) => split.status === "PAID").length;

  async function split(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const nameList = names.split(",").map((n) => n.trim()).filter(Boolean);
      setSplits(await createGuestSplits(tableToken, orderId, Number(parts), nameList));
      setMessage("Bill split. Each person can now pay their share.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not split the bill");
    } finally {
      setBusy(false);
    }
  }

  async function pay(share: PaymentSplitRecord) {
    if (!phone.trim()) {
      setError("Enter the Mobile Money number to charge");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await payGuestSplit(tableToken, orderId, share.id, phone.trim(), newKey());
      setPending({ splitId: share.id, paymentId: result.paymentId });
      setPayingId(null);
      setMessage(result.message || `Approve the MoMo prompt on ${phone.trim()} to pay ${share.displayName}'s share.`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start the payment");
    } finally {
      setBusy(false);
    }
  }

  async function checkPayment() {
    if (!pending) return;
    setBusy(true);
    setError("");
    try {
      const payment = await verifyGuestPayment(tableToken, orderId, pending.paymentId);
      if (payment.status === "SUCCESS") {
        setMessage("Payment received. Thank you!");
        setPending(null);
      } else if (payment.status === "FAILED") {
        setError(payment.providerMessage || "The payment failed. You can try again.");
        setPending(null);
      } else {
        setMessage("Still waiting for the MoMo approval…");
      }
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not check the payment");
    } finally {
      setBusy(false);
    }
  }

  if (fullyPaid) {
    return <p className="mt-2 text-sm font-semibold text-success-on">Paid. Thank you!</p>;
  }

  const feedback = (
    <div aria-live="polite">
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {message ? <p className="alert alert-success">{message}</p> : null}
    </div>
  );

  if (mode === "none") {
    return (
      <div className="mt-2 flex flex-wrap gap-2">
        {splits.length === 0 && total > 0 ? (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setMode("whole")}>
            Pay GHS {total.toFixed(2)} by MoMo
          </button>
        ) : null}
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => setMode("split")}>
          {splits.length > 0 ? "Pay my share" : "Split the bill"}
        </button>
      </div>
    );
  }

  if (mode === "whole") {
    return (
      <div className="mt-2 grid gap-2 rounded-lg bg-sunken p-2">
        {!pending ? (
          <div className="flex flex-wrap items-end gap-2">
            <label className="grid flex-1 gap-1">
              <span>MoMo number</span>
              <input className="input" inputMode="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="024..." />
            </label>
            <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={() => void payWhole()}>
              Pay GHS {total.toFixed(2)}
            </button>
            <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => { setMode("none"); setError(""); setMessage(""); }}>
              Cancel
            </button>
          </div>
        ) : (
          <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => void checkPayment()}>
            I&apos;ve approved it — check payment
          </button>
        )}
        {feedback}
      </div>
    );
  }

  return (
    <div className="mt-2 grid gap-2 rounded-lg bg-sunken p-2">
      {!anyPaid ? (
        <form className="grid gap-2 sm:grid-cols-[6rem_1fr_auto] sm:items-end" onSubmit={(e) => void split(e)}>
          <label className="grid gap-1">
            <span>People</span>
            <input className="input" type="number" min={2} max={20} required value={parts} onChange={(e) => setParts(e.target.value)} />
          </label>
          <label className="grid gap-1">
            <span>Names (optional, comma separated)</span>
            <input className="input" value={names} onChange={(e) => setNames(e.target.value)} placeholder="Ama, Kofi" />
          </label>
          <button type="submit" className="btn btn-secondary btn-sm" disabled={busy}>
            {splits.length > 0 ? "Re-split" : "Split equally"}
          </button>
        </form>
      ) : null}

      {splits.length > 0 ? (
        <ul className="grid gap-1">
          {splits.map((share) => (
            <li key={share.id} className="grid gap-2 rounded-md bg-surface p-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-semibold">{share.displayName}</span>
                <span>GHS {Number(share.amount).toFixed(2)}</span>
                <span className={`badge ${share.status === "PAID" ? "badge-success" : "badge-warning"}`}>
                  {share.status === "PAID" ? "Paid" : pending?.splitId === share.id ? "Awaiting MoMo" : "Unpaid"}
                </span>
                {share.status === "PENDING" && pending?.splitId !== share.id ? (
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    aria-expanded={payingId === share.id}
                    onClick={() => setPayingId(payingId === share.id ? null : share.id)}
                  >
                    Pay this share
                  </button>
                ) : null}
              </div>
              {payingId === share.id ? (
                <div className="flex flex-wrap items-end gap-2">
                  <label className="grid flex-1 gap-1">
                    <span>MoMo number</span>
                    <input className="input" inputMode="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="024..." />
                  </label>
                  <button type="button" className="btn btn-primary btn-sm" disabled={busy} onClick={() => void pay(share)}>
                    Pay GHS {Number(share.amount).toFixed(2)}
                  </button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}
      {splits.length > 0 ? <p className="text-ink-soft">{paidCount} of {splits.length} shares paid</p> : null}
      {pending ? (
        <button type="button" className="btn btn-secondary btn-sm" disabled={busy} onClick={() => void checkPayment()}>
          I&apos;ve approved it — check payment
        </button>
      ) : null}
      {!pending && !anyPaid ? (
        <button type="button" className="btn btn-secondary btn-sm justify-self-start" onClick={() => setMode("none")}>Back</button>
      ) : null}
      {feedback}
    </div>
  );
}

export function GuestRating({ tableToken, orderId, onRated }: { tableToken: string; orderId: string; onRated?: () => void }) {
  const storageKey = `rated-${orderId}`;
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [name, setName] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    try {
      if (window.localStorage.getItem(storageKey)) setDone(true);
    } catch {
      // Storage may be unavailable (private mode); the server still rejects a second rating.
    }
  }, [storageKey]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (rating === 0) {
      setError("Choose a star rating");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await submitGuestFeedback(tableToken, orderId, {
        rating,
        comment: comment.trim() || undefined,
        guestName: name.trim() || undefined,
      });
      setDone(true);
      onRated?.();
      try {
        window.localStorage.setItem(storageKey, "1");
      } catch {
        // Ignore storage failures.
      }
    } catch (err) {
      const text = err instanceof Error ? err.message : "Could not send your rating";
      if (/already/i.test(text)) {
        setDone(true);
        onRated?.();
      } else {
        setError(text);
      }
    } finally {
      setBusy(false);
    }
  }

  if (done) return <p className="mt-2 text-ink-soft">Thanks for rating this order!</p>;

  return (
    <form className="mt-2 grid gap-2 rounded-lg bg-sunken p-2" onSubmit={(e) => void submit(e)}>
      <fieldset>
        <legend className="font-semibold">How was it?</legend>
        <div className="flex gap-1" role="radiogroup" aria-label="Rating">
          {[1, 2, 3, 4, 5].map((star) => (
            <button
              key={star}
              type="button"
              role="radio"
              aria-checked={rating === star}
              aria-label={`${star} star${star === 1 ? "" : "s"}`}
              className={`min-h-[var(--touch-md)] min-w-[var(--touch-md)] text-2xl ${star <= rating ? "text-accent" : "text-ink-muted"}`}
              onClick={() => setRating(star)}
            >
              {star <= rating ? "★" : "☆"}
            </button>
          ))}
        </div>
      </fieldset>
      <label className="grid gap-1">
        <span>Comment (optional)</span>
        <textarea className="textarea" rows={2} maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      <label className="grid gap-1">
        <span>Your name (optional)</span>
        <input className="input" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <button type="submit" className="btn btn-primary btn-sm justify-self-start" disabled={busy}>Send rating</button>
    </form>
  );
}
