"use client";

import { createRealtimeClient, useAccessTokenGetter } from "@/lib/realtime";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { watchAlert } from "@/features/tables/paymentWatch";
import { getTablePaymentWatch, type StaffRole } from "@/lib/apiClient";
import { playAlertSound } from "@/lib/alertSound";

// Alerts already announced with a sound (kept across page changes). Table alerts present when the page first loads
// show without a sound; anything new after that plays one.
const sounded = new Set<string>();
let tableWatchPrimed = false;

type Alert = { id: string; text: string; href: string; tone: "warning" | "danger" };

type LowStockPayload = { ingredientId?: string; name?: string; unit?: string; stockQuantity?: number | string; event?: "ITEM_86" | "ITEM_BACK"; menuItemId?: string };
type LowRatingPayload = { id: string; orderId: string; rating: number; customerName?: string | null; comment?: string | null };

const MAX_ALERTS = 4;
const TABLE_WATCH_MS = 60_000;

/**
 * Live pop-up alerts. Floor staff and managers: a table that was served and has not paid for a while, or asked for
 * the bill and is still waiting (go over with the bill; the guest is never prompted). Managers also get ingredients
 * running low, menu items auto-86'd, and ratings of 2 stars or lower. Hidden for roles that cannot act on them.
 */
export function StaffAlerts({ role }: { role?: StaffRole }) {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const { session, authenticatedFetch } = useStaffSession();
  const getToken = useAccessTokenGetter();
  const managerAlerts = role === "ADMIN" || role === "MANAGER";
  const enabled = managerAlerts || role === "CASHIER";
  // Alerts a person dismissed stay hidden until the table's situation changes (a new id).
  const dismissed = useRef(new Set<string>());

  const userId = session?.user.id;

  useEffect(() => {
    if (!enabled || !userId) return;
    let cancelled = false;
    async function check() {
      try {
        const watch = await authenticatedFetch((s) => getTablePaymentWatch(s));
        if (cancelled) return;
        const risky = watch.filter((entry) => entry.risk !== "NONE");
        const ids = new Set(risky.map((entry) => `table-${entry.tableId}-${entry.risk}`));
        const newOnes = [...ids].filter((id) => !sounded.has(id) && !dismissed.current.has(id));
        newOnes.forEach((id) => sounded.add(id));
        if (tableWatchPrimed && newOnes.length > 0) playAlertSound("warning");
        tableWatchPrimed = true;
        setAlerts((current) => {
          const others = current.filter((a) => !a.id.startsWith("table-") || ids.has(a.id));
          const fresh: Alert[] = risky
            .map((entry) => ({ id: `table-${entry.tableId}-${entry.risk}`, text: watchAlert(entry), href: "/tables", tone: "danger" as const }))
            .filter((a) => !dismissed.current.has(a.id));
          const merged = [...fresh, ...others.filter((a) => !fresh.some((f) => f.id === a.id))];
          return merged.slice(0, MAX_ALERTS);
        });
      } catch {
        // Keep the current alerts if a check fails.
      }
    }
    void check();
    const timer = window.setInterval(() => void check(), TABLE_WATCH_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
    // Re-run only when the signed-in person changes; authenticatedFetch always uses the current token.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, userId]);

  useEffect(() => {
    if (!managerAlerts) return;
    const push = (alert: Alert) => {
      if (!sounded.has(alert.id)) {
        sounded.add(alert.id);
        playAlertSound("warning");
      }
      setAlerts((current) => [alert, ...current.filter((a) => a.id !== alert.id)].slice(0, MAX_ALERTS));
    };
    const client = createRealtimeClient({
      reconnectDelay: 10000,
      onConnect: () => {
        client.subscribe("/topic/inventory.alerts", (frame) => {
          try {
            const payload = JSON.parse(frame.body) as LowStockPayload;
            if (payload.ingredientId) {
              push({
                id: `stock-${payload.ingredientId}`,
                text: `Low stock: ${payload.name} (${Number(payload.stockQuantity ?? 0)} ${payload.unit ?? ""} left)`,
                href: "/inventory",
                tone: Number(payload.stockQuantity ?? 0) <= 0 ? "danger" : "warning",
              });
            } else if (payload.menuItemId) {
              const back = payload.event === "ITEM_BACK";
              push({
                id: `item-${payload.menuItemId}`,
                text: back ? `${payload.name} is back on the menu (restocked)` : `${payload.name} is off the menu: out of an ingredient`,
                href: back ? "/menu" : "/inventory",
                tone: back ? "warning" : "danger",
              });
            }
          } catch {
            // Ignore malformed alerts.
          }
        });
        client.subscribe("/topic/feedback.alerts", (frame) => {
          try {
            const payload = JSON.parse(frame.body) as LowRatingPayload;
            push({
              id: `feedback-${payload.id}`,
              text: `${payload.rating}★ rating from ${payload.customerName || "a guest"}${payload.comment ? `: “${payload.comment.slice(0, 80)}”` : ""}`,
              href: `/orders/${payload.orderId}`,
              tone: "danger",
            });
          } catch {
            // Ignore malformed alerts.
          }
        });
      },
    }, getToken);
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [managerAlerts, getToken]);

  function dismiss(id: string) {
    dismissed.current.add(id);
    setAlerts((c) => c.filter((a) => a.id !== id));
  }

  if (!enabled || alerts.length === 0) return null;

  return (
    <div className="fixed bottom-20 right-3 z-[var(--z-toast)] grid w-[min(360px,calc(100vw-24px))] gap-2 lg:bottom-4" role="status" aria-live="polite">
      {alerts.map((alert) => (
        <div key={alert.id} className={`alert ${alert.tone === "danger" ? "alert-danger" : "alert-warning"} flex items-start justify-between gap-2 shadow-lg`}>
          <Link href={alert.href} className="text-sm underline-offset-2 hover:underline" onClick={() => dismiss(alert.id)}>
            {alert.text}
          </Link>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            aria-label="Dismiss alert"
            onClick={() => dismiss(alert.id)}
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}
