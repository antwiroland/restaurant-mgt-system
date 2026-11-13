"use client";

import { useEffect, useState } from "react";
import { GuestRating } from "@/components/GuestOrderActions";
import { forgetOrder, rememberedOrders } from "@/features/orders/guestOrders";
import { getGuestRatingPrompt, type GuestRatingPromptRecord } from "@/lib/apiClient";

type Props = {
  tableToken: string;
  /** Orders the table page already shows (the current party's): those are rated from the tracker instead. */
  shownOrderIds: string[];
  /** Bumped when the page refreshes its orders, so a just-served order can be offered. */
  refreshKey?: number;
};

/**
 * "How was your meal?" for orders this phone placed that have been served and are no longer on the table page
 * (the table has moved on), within two hours of being served. Nothing shows on any other phone.
 */
export function GuestRatePrompt({ tableToken, shownOrderIds, refreshKey = 0 }: Props) {
  const [prompts, setPrompts] = useState<GuestRatingPromptRecord[]>([]);
  const shownKey = shownOrderIds.join(",");

  useEffect(() => {
    let cancelled = false;
    const shown = new Set(shownKey ? shownKey.split(",") : []);
    const candidates = rememberedOrders(tableToken).filter((o) => !shown.has(o.orderId));
    if (candidates.length === 0) {
      const clear = window.setTimeout(() => setPrompts([]), 0);
      return () => window.clearTimeout(clear);
    }
    Promise.all(
      candidates.map(async (o) => {
        try {
          const prompt = await getGuestRatingPrompt(tableToken, o.orderId);
          if (!prompt) forgetOrder(o.orderId); // expired or cancelled
          // Rated: keep it (until the window closes) to show the restaurant's reply once there is one.
          if (prompt && prompt.rated) return prompt.response ? prompt : null;
          return prompt && prompt.rateable ? prompt : null;
        } catch {
          return null; // offline: try again next time
        }
      }),
    ).then((results) => {
      if (!cancelled) setPrompts(results.filter((p): p is GuestRatingPromptRecord => p !== null));
    });
    return () => {
      cancelled = true;
    };
  }, [tableToken, shownKey, refreshKey]);

  function dismiss(orderId: string) {
    forgetOrder(orderId);
    setPrompts((current) => current.filter((p) => p.orderId !== orderId));
  }

  if (prompts.length === 0) return null;

  return (
    <section className="rounded-2xl border border-line bg-surface p-4 shadow-sm" aria-labelledby="rate-meal">
      <h2 id="rate-meal" className="font-display font-bold">{prompts.every((p) => p.rated) ? "Thanks for your rating" : "How was your meal?"}</h2>
      <p className="mt-1 text-sm text-ink-soft">Tell us about your last order at this table. Only you can see this.</p>
      <ul className="mt-3 grid gap-4">
        {prompts.map((prompt) => (
          <li key={prompt.orderId}>
            <div className="flex items-center justify-between text-sm">
              <span className="font-mono font-semibold">#{prompt.orderNumber}</span>
              <button type="button" className="text-xs font-semibold text-ink-soft underline-offset-2 hover:underline" onClick={() => dismiss(prompt.orderId)}>
                Not now
              </button>
            </div>
            {prompt.rated && prompt.response ? (
              <div className="mt-2 rounded-lg bg-sunken p-2 text-sm">
                <p className="font-semibold">Reply from the restaurant</p>
                <p className="mt-1 text-ink-soft">{prompt.response}</p>
              </div>
            ) : (
              <GuestRating tableToken={tableToken} orderId={prompt.orderId} />
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
