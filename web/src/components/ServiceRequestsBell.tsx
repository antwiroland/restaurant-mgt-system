"use client";

import { Client } from "@stomp/stompjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { useStaffSession } from "@/components/SessionProvider";
import { getServiceRequests, updateServiceRequest, type ServiceRequestRecord } from "@/lib/apiClient";

const LABEL: Record<ServiceRequestRecord["type"], string> = {
  CALL_WAITER: "Wants a waiter",
  BILL: "Asked for the bill",
  OTHER: "Needs help",
};

function minutesAgo(iso: string): string {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  return minutes < 1 ? "just now" : `${minutes} min ago`;
}

/** Guest "call a waiter" / "bring the bill" requests from table QR pages, live over STOMP. */
export function ServiceRequestsBell({ dark = false }: { dark?: boolean }) {
  const { session, authenticatedFetch } = useStaffSession();
  const [requests, setRequests] = useState<ServiceRequestRecord[]>([]);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    try {
      setRequests(await authenticatedFetch((s) => getServiceRequests(s)));
    } catch {
      /* bell stays quiet if the endpoint is unavailable */
    }
  }, [authenticatedFetch]);

  useEffect(() => {
    if (!session) return;
    const first = window.setTimeout(() => void load(), 0);
    const poll = window.setInterval(() => void load(), 60000);
    const client = new Client({
      brokerURL: process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws",
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe("/topic/service-requests", () => void load());
      },
    });
    client.activate();
    return () => {
      window.clearTimeout(first);
      window.clearInterval(poll);
      void client.deactivate();
    };
  }, [load, session]);

  useEffect(() => {
    if (!open) return;
    const onDown = (event: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  async function act(id: string, status: "ACKNOWLEDGED" | "RESOLVED") {
    setBusy(id);
    try {
      await authenticatedFetch((s) => updateServiceRequest(s, id, status));
      await load();
    } finally {
      setBusy(null);
    }
  }

  const openCount = requests.filter((r) => r.status === "OPEN").length;

  return (
    <div className="relative" ref={panelRef}>
      <button
        type="button"
        className={`btn btn-icon btn-sm relative ${dark ? "btn-ghost text-night-ink hover:bg-night-card" : "btn-ghost"}`}
        aria-label={openCount > 0 ? `${openCount} table request(s) waiting` : "Table requests"}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <Icon name="bell" />
        {openCount > 0 ? (
          <span className="absolute -right-0.5 -top-0.5 grid h-5 min-w-5 place-items-center rounded-full bg-danger px-1 text-[11px] font-bold text-white">
            {openCount}
          </span>
        ) : null}
      </button>
      {open ? (
        <div className="absolute right-0 top-full z-[var(--z-drawer)] mt-2 w-[min(360px,calc(100vw-2rem))] rounded-card border border-line bg-surface p-2 text-ink shadow-lg" role="dialog" aria-label="Table requests">
          <p className="px-2 py-1.5 text-xs font-bold uppercase tracking-wide text-ink-muted">Table requests</p>
          {requests.length === 0 ? (
            <p className="px-2 py-4 text-sm text-ink-soft">No guests are waiting.</p>
          ) : (
            <ul className="grid gap-1">
              {requests.map((r) => (
                <li key={r.id} className={`rounded-lg p-2.5 ${r.status === "OPEN" ? "bg-warning-subtle" : "bg-sunken"}`}>
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold">Table {r.tableNumber} · {LABEL[r.type]}</p>
                      <p className="text-xs text-ink-soft">
                        {minutesAgo(r.createdAt)}{r.status === "ACKNOWLEDGED" ? " · on it" : ""}
                      </p>
                      {r.note ? <p className="mt-1 text-sm italic">“{r.note}”</p> : null}
                    </div>
                    <div className="flex shrink-0 gap-1">
                      {r.status === "OPEN" ? (
                        <button type="button" className="btn btn-secondary btn-sm" disabled={busy === r.id} onClick={() => void act(r.id, "ACKNOWLEDGED")}>On my way</button>
                      ) : null}
                      <button type="button" className="btn btn-ghost btn-sm" disabled={busy === r.id} onClick={() => void act(r.id, "RESOLVED")}>Done</button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}
