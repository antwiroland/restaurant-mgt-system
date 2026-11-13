"use client";

import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { getOrderFeedback, submitOrderFeedback, type FeedbackRecord } from "@/lib/apiClient";

type Props = { orderId: string; orderType: "DINE_IN" | "PICKUP" | "DELIVERY"; status: string };

const RATEABLE = new Set(["COMPLETED", "DELIVERED"]);

function Stars({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <fieldset>
      <legend className="text-sm font-semibold">{label}</legend>
      <div className="flex gap-1" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((star) => (
          <button
            key={star}
            type="button"
            role="radio"
            aria-checked={value === star}
            aria-label={`${star} star${star === 1 ? "" : "s"}`}
            className={`min-h-[var(--touch-md)] min-w-[var(--touch-md)] text-2xl ${star <= value ? "text-accent" : "text-ink-muted"}`}
            onClick={() => onChange(star === value ? 0 : star)}
          >
            {star <= value ? "★" : "☆"}
          </button>
        ))}
      </div>
    </fieldset>
  );
}

/** Rate a finished order from the website (signed-in customers), and see the restaurant's reply. */
export function OrderRating({ orderId, orderType, status }: Props) {
  const { authenticatedFetch } = useStaffSession();
  const [existing, setExisting] = useState<FeedbackRecord | null>(null);
  const [checked, setChecked] = useState(false);
  const [rating, setRating] = useState(0);
  const [food, setFood] = useState(0);
  const [service, setService] = useState(0);
  const [delivery, setDelivery] = useState(0);
  const [comment, setComment] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const rateable = RATEABLE.has(status);

  useEffect(() => {
    if (!rateable) return;
    let cancelled = false;
    authenticatedFetch((s) => getOrderFeedback(s, orderId))
      .then((feedback) => {
        if (!cancelled) setExisting(feedback ?? null);
      })
      .catch(() => {
        // 404: not rated yet
      })
      .finally(() => {
        if (!cancelled) setChecked(true);
      });
    return () => {
      cancelled = true;
    };
  }, [authenticatedFetch, orderId, rateable]);

  if (!rateable || !checked) return null;

  if (existing) {
    return (
      <section className="rounded-card border border-line bg-surface p-5" aria-labelledby="your-rating">
        <h2 id="your-rating" className="font-display text-lg font-bold">Your rating</h2>
        <p className="mt-1 text-2xl text-accent" aria-label={`${existing.rating} out of 5 stars`}>
          {"★".repeat(existing.rating)}<span className="text-ink-muted">{"☆".repeat(5 - existing.rating)}</span>
        </p>
        {existing.comment ? <p className="mt-1 text-sm text-ink-soft">“{existing.comment}”</p> : null}
        {existing.response ? (
          <div className="mt-3 rounded-lg bg-sunken p-3 text-sm">
            <p className="font-semibold">Reply from the restaurant</p>
            <p className="mt-1 text-ink-soft">{existing.response}</p>
          </div>
        ) : null}
      </section>
    );
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (rating === 0) {
      setError("Choose an overall star rating");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const saved = await authenticatedFetch((s) =>
        submitOrderFeedback(s, orderId, {
          rating,
          foodRating: food || undefined,
          serviceRating: service || undefined,
          deliveryRating: orderType === "DELIVERY" && delivery ? delivery : undefined,
          comment: comment.trim() || undefined,
        }),
      );
      setExisting(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send your rating");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="rounded-card border border-line bg-surface p-5" aria-labelledby="rate-order">
      <h2 id="rate-order" className="font-display text-lg font-bold">How was your order?</h2>
      <form className="mt-3 grid gap-3" onSubmit={(e) => void submit(e)}>
        <Stars label="Overall" value={rating} onChange={setRating} />
        <div className="grid gap-3 sm:grid-cols-3">
          <Stars label="Food (optional)" value={food} onChange={setFood} />
          <Stars label="Service (optional)" value={service} onChange={setService} />
          {orderType === "DELIVERY" ? <Stars label="Delivery (optional)" value={delivery} onChange={setDelivery} /> : null}
        </div>
        <label className="grid gap-1 text-sm">
          <span>Comment (optional)</span>
          <textarea className="textarea" rows={2} maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />
        </label>
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <button type="submit" className="btn btn-primary justify-self-start" disabled={busy}>Send rating</button>
      </form>
    </section>
  );
}
