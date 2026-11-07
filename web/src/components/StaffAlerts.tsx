"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import type { StaffRole } from "@/lib/apiClient";

type Alert = { id: string; text: string; href: string; tone: "warning" | "danger" };

type LowStockPayload = { ingredientId?: string; name?: string; unit?: string; stockQuantity?: number | string; event?: "ITEM_86" | "ITEM_BACK"; menuItemId?: string };
type LowRatingPayload = { id: string; orderId: string; rating: number; customerName?: string | null; comment?: string | null };

const MAX_ALERTS = 4;

/**
 * Live pop-up alerts for managers: ingredients running low or menu items auto-86'd, and customer ratings of
 * 2 stars or lower. Hidden for roles that cannot act on them.
 */
export function StaffAlerts({ role }: { role?: StaffRole }) {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const enabled = role === "ADMIN" || role === "MANAGER";

  useEffect(() => {
    if (!enabled) return;
    const wsUrl = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws";
    const push = (alert: Alert) => setAlerts((current) => [alert, ...current.filter((a) => a.id !== alert.id)].slice(0, MAX_ALERTS));
    const client = new Client({
      brokerURL: wsUrl,
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
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [enabled]);

  if (!enabled || alerts.length === 0) return null;

  return (
    <div className="fixed bottom-20 right-3 z-[var(--z-toast)] grid w-[min(360px,calc(100vw-24px))] gap-2 lg:bottom-4" role="status" aria-live="polite">
      {alerts.map((alert) => (
        <div key={alert.id} className={`alert ${alert.tone === "danger" ? "alert-danger" : "alert-warning"} flex items-start justify-between gap-2 shadow-lg`}>
          <Link href={alert.href} className="text-sm underline-offset-2 hover:underline" onClick={() => setAlerts((c) => c.filter((a) => a.id !== alert.id))}>
            {alert.text}
          </Link>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            aria-label="Dismiss alert"
            onClick={() => setAlerts((c) => c.filter((a) => a.id !== alert.id))}
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}
