"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import {
  getFeedback,
  getFeedbackSummary,
  respondToFeedback,
  type FeedbackRecord,
  type FeedbackSummaryRecord,
} from "@/lib/apiClient";
import { addDays, isoDate } from "@/features/workforce/time";

function Stars({ value, label }: { value: number; label?: string }) {
  const rounded = Math.round(value);
  return (
    <span aria-label={`${label ? `${label}: ` : ""}${value} out of 5`} className="text-accent">
      <span aria-hidden="true">{"★".repeat(rounded)}{"☆".repeat(5 - rounded)}</span>
    </span>
  );
}

function avg(value?: string | null): string {
  return value ? Number(value).toFixed(1) : "-";
}

export default function FeedbackPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [from, setFrom] = useState(() => isoDate(addDays(new Date(), -30)));
  const [to, setTo] = useState(() => isoDate(new Date()));
  const [lowOnly, setLowOnly] = useState(false);
  const [items, setItems] = useState<FeedbackRecord[]>([]);
  const [summary, setSummary] = useState<FeedbackSummaryRecord | null>(null);
  const [replying, setReplying] = useState<string | null>(null);
  const [reply, setReply] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    setError("");
    try {
      const [list, stats] = await Promise.all([
        authenticatedFetch((s) => getFeedback(s, { from, to, maxRating: lowOnly ? 2 : undefined })),
        authenticatedFetch((s) => getFeedbackSummary(s, { from, to })),
      ]);
      setItems(list);
      setSummary(stats);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load feedback");
    } finally {
      setLoaded(true);
    }
  }, [authenticatedFetch, from, lowOnly, session, to]);

  useEffect(() => {
    void load();
  }, [load]);

  async function sendReply(event: React.FormEvent, item: FeedbackRecord) {
    event.preventDefault();
    if (!reply.trim()) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((s) => respondToFeedback(s, item.id, reply.trim()));
      setReplying(null);
      setReply("");
      setMessage("Reply saved");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save reply");
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-48 w-full" /></section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Feedback</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  const maxBucket = Math.max(1, ...Object.values(summary?.distribution ?? {}));

  return (
    <main className="shell grid gap-4">
      <section className="panel grid gap-3">
        <div>
          <p className="kicker">Customers</p>
          <h1 className="text-2xl font-semibold">Feedback and ratings</h1>
          <p className="text-sm text-ink-soft">Customers rate an order once it is completed or delivered. Ratings of 2 stars or lower alert managers in real time.</p>
        </div>
        <form className="flex flex-wrap items-end gap-2" onSubmit={(e) => { e.preventDefault(); void load(); }}>
          <label className="grid gap-1 text-sm">
            <span>From</span>
            <input className="input" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="grid gap-1 text-sm">
            <span>To</span>
            <input className="input" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </label>
          <label className="flex min-h-[var(--touch-md)] items-center gap-2 text-sm">
            <input type="checkbox" className="h-5 w-5" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} />
            Low ratings only
          </label>
        </form>
      </section>

      {summary ? (
        <section className="grid gap-4 md:grid-cols-[1fr_1.4fr]">
          <div className="panel grid content-start gap-2">
            <p className="text-sm text-ink-soft">Average rating</p>
            <p className="text-4xl font-semibold">{avg(summary.averageRating)}</p>
            {summary.averageRating ? <Stars value={Number(summary.averageRating)} /> : null}
            <p className="text-sm text-ink-soft">
              {summary.count} rating{summary.count === 1 ? "" : "s"} · {summary.lowRatings} low · {summary.responded} replied
            </p>
            <dl className="mt-2 grid grid-cols-3 gap-2 text-sm">
              <div><dt className="text-ink-soft">Food</dt><dd className="font-semibold">{avg(summary.averageFood)}</dd></div>
              <div><dt className="text-ink-soft">Service</dt><dd className="font-semibold">{avg(summary.averageService)}</dd></div>
              <div><dt className="text-ink-soft">Delivery</dt><dd className="font-semibold">{avg(summary.averageDelivery)}</dd></div>
            </dl>
          </div>
          <div className="panel grid content-start gap-2">
            <p className="text-sm text-ink-soft">Rating spread</p>
            {[5, 4, 3, 2, 1].map((stars) => {
              const count = summary.distribution?.[String(stars)] ?? 0;
              return (
                <div key={stars} className="grid grid-cols-[3rem_1fr_2.5rem] items-center gap-2 text-sm">
                  <span>{stars} ★</span>
                  <span className="h-3 rounded-full bg-sunken" aria-hidden="true">
                    <span className={`block h-3 rounded-full ${stars <= 2 ? "bg-danger" : "bg-brand"}`} style={{ width: `${(count / maxBucket) * 100}%` }} />
                  </span>
                  <span className="text-right">{count}</span>
                </div>
              );
            })}
          </div>
        </section>
      ) : null}

      <div aria-live="polite">
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {message ? <p className="alert alert-success">{message}</p> : null}
      </div>

      <section className="panel">
        <h2 className="text-xl font-semibold">Comments</h2>
        {!loaded ? (
          <Skeleton className="mt-3 h-32 w-full" />
        ) : items.length === 0 ? (
          <p className="mt-3 text-sm text-ink-soft">No feedback in this period.</p>
        ) : (
          <ul className="mt-3 grid gap-2">
            {items.map((item) => (
              <li key={item.id} className={`card grid gap-2 ${item.rating <= 2 ? "border-danger" : ""}`}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm">
                    <Stars value={item.rating} />{" "}
                    <span className="font-semibold">{item.customerName || "Guest"}</span>
                    <span className="text-ink-soft"> · {new Date(item.createdAt).toLocaleString()}</span>
                  </p>
                  <Link href={`/orders/${item.orderId}`} className="text-sm underline">Order #{item.orderId.slice(0, 8).toUpperCase()}</Link>
                </div>
                {item.foodRating || item.serviceRating || item.deliveryRating ? (
                  <p className="text-xs text-ink-soft">
                    {item.foodRating ? `Food ${item.foodRating}/5 ` : ""}
                    {item.serviceRating ? `· Service ${item.serviceRating}/5 ` : ""}
                    {item.deliveryRating ? `· Delivery ${item.deliveryRating}/5` : ""}
                  </p>
                ) : null}
                {item.comment ? <p className="text-sm">“{item.comment}”</p> : <p className="text-sm text-ink-soft">No comment.</p>}
                {item.response ? (
                  <p className="rounded-md bg-sunken p-2 text-sm">
                    <span className="font-semibold">Your reply:</span> {item.response}
                  </p>
                ) : replying === item.id ? (
                  <form className="grid gap-2" onSubmit={(e) => void sendReply(e, item)}>
                    <label className="grid gap-1 text-sm">
                      <span>Reply</span>
                      <textarea className="input" rows={2} maxLength={2000} required value={reply} onChange={(e) => setReply(e.target.value)} />
                    </label>
                    <div className="flex gap-2">
                      <button type="submit" className="btn btn-primary btn-sm" disabled={busy}>Save reply</button>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setReplying(null)}>Cancel</button>
                    </div>
                  </form>
                ) : (
                  <button type="button" className="btn btn-secondary btn-sm justify-self-start" onClick={() => { setReplying(item.id); setReply(""); }}>
                    Reply
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
