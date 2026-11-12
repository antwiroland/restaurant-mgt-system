"use client";

import { createRealtimeClient, useAccessTokenGetter } from "@/lib/realtime";
import { useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Spinner } from "@/components/Spinner";
import { useStaffSession } from "@/components/SessionProvider";
import { getBranches, getKdsBoard, sendOrderToPrinter, setOrderItemReady, updateOrderStatus, type BranchRecord, type KdsBoardRecord, type KdsOrderCard } from "@/lib/apiClient";

const COLUMNS: Array<keyof KdsBoardRecord["columns"]> = ["PENDING", "CONFIRMED", "PREPARING", "READY"];
const POLL_INTERVAL_MS = 15000;
const WARN_AFTER_MIN = 8;
const LATE_AFTER_MIN = 15;

const COLUMN_META: Record<keyof KdsBoardRecord["columns"], { label: string; dot: string; accent: string; empty: string }> = {
  PENDING: { label: "New", dot: "bg-gold", accent: "border-t-gold", empty: "No new tickets" },
  CONFIRMED: { label: "Accepted", dot: "bg-info-border", accent: "border-t-info-border", empty: "Nothing waiting to start" },
  PREPARING: { label: "Cooking", dot: "bg-brand", accent: "border-t-brand", empty: "Nothing on the stove" },
  READY: { label: "Ready to serve", dot: "bg-success", accent: "border-t-success", empty: "Pass is clear" },
};

const kdsBtn =
  "inline-flex h-11 items-center justify-center gap-2 rounded-[10px] border border-night-line bg-night-card px-3.5 text-sm font-semibold text-night-ink transition-colors hover:border-night-soft aria-pressed:border-brand aria-pressed:text-white";

type KdsAdvanceStatus = "CONFIRMED" | "PREPARING" | "READY" | "COMPLETED";

export default function KdsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const getToken = useAccessTokenGetter();
  const [board, setBoard] = useState<KdsBoardRecord | null>(null);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState<string>("");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);
  const [realtimeConnected, setRealtimeConnected] = useState(false);
  const [compactMode, setCompactMode] = useState(false);
  const [tick, setTick] = useState(0);

  // Re-render every 30s so elapsed-time badges stay accurate between polls.
  useEffect(() => {
    const timer = window.setInterval(() => setTick((value) => value + 1), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  function toggleFullscreen() {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.documentElement.requestFullscreen?.().catch(() => {});
  }

  async function loadBoard() {
    if (!session) return;
    await authenticatedFetch(async (activeSession) => {
      const next = await getKdsBoard(activeSession, branchId || undefined);
      setBoard(next);
      setLastUpdated(new Date());
    });
  }

  async function loadBranches() {
    if (!session) return;
    await authenticatedFetch(async (activeSession) => {
      const next = await getBranches(activeSession);
      setBranches(next);
    });
  }

  useEffect(() => {
    if (!session) return;
    loadBranches().catch((err) => setError(err instanceof Error ? err.message : "Could not load branches"));
  }, [authenticatedFetch, session]);

  useEffect(() => {
    if (!session) return;
    loadBoard().catch((err) => setError(err instanceof Error ? err.message : "Could not load KDS board"));

    const timer = window.setInterval(() => {
      loadBoard().catch(() => {
        // Keep the current board if a poll fails.
      });
    }, POLL_INTERVAL_MS);

    return () => window.clearInterval(timer);
  }, [authenticatedFetch, session, branchId]);

  useEffect(() => {
    if (!session) return;
    const client = createRealtimeClient({
      onConnect: () => {
        setRealtimeConnected(true);
        client.subscribe("/topic/orders.new", () => {
          loadBoard().catch(() => {});
        });
        client.subscribe("/topic/orders.status", () => {
          loadBoard().catch(() => {});
        });
      },
      onWebSocketClose: () => {
        setRealtimeConnected(false);
      },
      onStompError: () => {
        setRealtimeConnected(false);
      },
    }, getToken);
    client.activate();

    return () => {
      setRealtimeConnected(false);
      client.deactivate();
    };
  }, [session, authenticatedFetch, branchId, getToken]);

  async function toggleItem(orderId: string, itemId: string, ready: boolean) {
    setBusyOrderId(orderId);
    setError("");
    try {
      await authenticatedFetch((activeSession) => setOrderItemReady(activeSession, orderId, itemId, ready));
      await loadBoard();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update item");
    } finally {
      setBusyOrderId(null);
    }
  }

  async function printTicket(orderId: string) {
    setError("");
    try {
      await authenticatedFetch((activeSession) => sendOrderToPrinter(activeSession, orderId, "kitchen"));
    } catch {
      // No network kitchen printer (or unreachable): fall back to the browser print layout.
      window.open(`/print/kitchen/${orderId}`, "_blank", "noopener");
    }
  }

  async function moveOrder(orderId: string, status: KdsAdvanceStatus) {
    setBusyOrderId(orderId);
    setError("");
    try {
      await authenticatedFetch(async (activeSession) => {
        await updateOrderStatus(activeSession, orderId, status);
      });
      await loadBoard();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update order status");
    } finally {
      setBusyOrderId(null);
    }
  }

  if (loading || !session) {
    // AppShell renders the loading / sign-in states; keep a quiet placeholder.
    return (
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, col) => (
          <div key={col} className="grid content-start gap-3 rounded-2xl bg-night-raised p-3">
            <div className="h-8 w-32 rounded-lg bg-night-card" />
            <div className="h-40 rounded-xl bg-night-card" />
            <div className="h-40 rounded-xl bg-night-card" />
          </div>
        ))}
      </div>
    );
  }

  const totals = COLUMNS.reduce((sum, column) => sum + (board?.columns?.[column]?.length ?? 0), 0);
  const lateCount = COLUMNS.slice(0, 3).reduce(
    (sum, column) => sum + (board?.columns?.[column] ?? []).filter((card) => minutesSince(card.createdAt) > LATE_AFTER_MIN).length,
    0,
  );

  return (
    <main className="grid gap-4 text-night-ink" data-tick={tick}>
      <section className="flex flex-col gap-3 rounded-2xl border border-night-line bg-night-raised p-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap items-center gap-3 px-1">
          <span className="font-display text-xl font-extrabold tracking-tight">Kitchen</span>
          <span className="rounded-lg bg-night-card px-3 py-1.5 text-sm font-semibold text-night-soft">
            <span className="tabular text-night-ink">{totals}</span> open tickets
          </span>
          {lateCount > 0 ? (
            <span className="flex items-center gap-1.5 rounded-lg bg-danger px-3 py-1.5 text-sm font-bold text-white" role="status">
              <Icon name="alert" size={16} /> {lateCount} running late
            </span>
          ) : null}
          <span className="flex items-center gap-2 text-sm font-semibold text-night-ink" role="status">
            <span className={`h-2.5 w-2.5 rounded-full ${realtimeConnected ? "bg-success" : "animate-pulse bg-warning"}`} aria-hidden="true" />
            {realtimeConnected ? "Live" : "Reconnecting…"}
            <span className="font-normal text-night-soft">{lastUpdated ? `· ${lastUpdated.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}` : ""}</span>
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <label className="relative min-w-[220px] flex-1 lg:flex-none">
            <span className="sr-only">Search tickets</span>
            <Icon name="search" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-night-soft" />
            <input
              className="h-11 w-full rounded-[10px] border border-night-line bg-night-card pl-10 pr-3 text-[15px] text-night-ink placeholder:text-night-soft focus:border-brand focus:outline-none"
              placeholder="Table, order or dish"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
          {branches.length > 1 ? (
            <select
              value={branchId}
              onChange={(event) => setBranchId(event.target.value)}
              className="h-11 rounded-[10px] border border-night-line bg-night-card px-3 text-[15px] text-night-ink focus:border-brand focus:outline-none"
              aria-label="Branch"
            >
              <option value="">All branches</option>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>{branch.name}</option>
              ))}
            </select>
          ) : null}
          <button type="button" className={kdsBtn} aria-pressed={compactMode} onClick={() => setCompactMode((value) => !value)}>
            {compactMode ? "Detailed" : "Compact"}
          </button>
          <button
            type="button"
            className={kdsBtn}
            onClick={() => loadBoard().catch((err) => setError(err instanceof Error ? err.message : "Could not refresh KDS board"))}
            aria-label="Refresh tickets"
          >
            <Icon name="refresh" size={18} />
          </button>
          <button type="button" className={kdsBtn} onClick={toggleFullscreen} aria-label="Toggle full screen">
            <Icon name="eye" size={18} /> <span className="hidden sm:inline">Full screen</span>
          </button>
        </div>
      </section>

      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}

      <div className="grid auto-cols-[minmax(300px,1fr)] grid-flow-col gap-4 overflow-x-auto pb-2 xl:grid-flow-row xl:grid-cols-4">
        {COLUMNS.map((column) => {
          const meta = COLUMN_META[column];
          const cards = (board?.columns?.[column] ?? [])
            .filter((card) => matchesCardQuery(card, query))
            .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
          return (
            <section
              key={column}
              aria-labelledby={`kds-col-${column}`}
              aria-live="polite"
              className="flex min-h-[60dvh] flex-col rounded-2xl border border-night-line bg-night-raised"
            >
              <header className="flex items-center justify-between gap-2 border-b border-night-line px-4 py-3">
                <h2 id={`kds-col-${column}`} className="flex items-center gap-2.5 font-display text-[15px] font-extrabold uppercase tracking-[0.12em] text-night-ink">
                  <span className={`h-3 w-3 rounded-full ${meta.dot}`} aria-hidden="true" />
                  {meta.label}
                </h2>
                <span className="grid h-8 min-w-8 place-items-center rounded-lg bg-night-card px-2 font-display text-base font-bold tabular">{cards.length}</span>
              </header>
              <div className="grid content-start gap-3 p-3">
                {cards.map((card) => (
                  <OrderCard
                    key={card.orderId}
                    card={card}
                    busy={busyOrderId === card.orderId}
                    compact={compactMode}
                    onMove={moveOrder}
                    onToggleItem={toggleItem}
                    onPrint={printTicket}
                  />
                ))}
                {cards.length === 0 ? (
                  <p className="grid place-items-center rounded-xl border border-dashed border-night-line px-4 py-10 text-center text-sm text-night-soft">
                    {query ? "No matching tickets" : meta.empty}
                  </p>
                ) : null}
              </div>
            </section>
          );
        })}
      </div>
      <p className="px-1 text-xs text-night-soft">Tip: swipe a ticket to the right to move it to the next stage. Tickets turn red after {LATE_AFTER_MIN} minutes.</p>
    </main>
  );
}

function minutesSince(isoTime: string): number {
  const created = new Date(isoTime).getTime();
  if (Number.isNaN(created)) return 0;
  return Math.max(0, Math.floor((Date.now() - created) / 60000));
}

function ageMeta(isoTime: string): { label: string; tone: "ok" | "warn" | "late" } {
  const mins = minutesSince(isoTime);
  const label = mins >= 60 ? `${Math.floor(mins / 60)}h ${mins % 60}m` : `${mins}m`;
  if (mins > LATE_AFTER_MIN) return { label, tone: "late" };
  if (mins > WARN_AFTER_MIN) return { label, tone: "warn" };
  return { label, tone: "ok" };
}

function matchesCardQuery(card: KdsOrderCard, query: string): boolean {
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) return true;
  return card.orderId.toLowerCase().includes(normalizedQuery)
    || card.tableNumber.toLowerCase().includes(normalizedQuery)
    || (card.branchName?.toLowerCase().includes(normalizedQuery) ?? false)
    || card.items.some((item) => item.name.toLowerCase().includes(normalizedQuery));
}

function nextAction(status: KdsOrderCard["status"]): { label: string; status: KdsAdvanceStatus } | null {
  if (status === "PENDING") return { label: "Accept", status: "CONFIRMED" };
  if (status === "CONFIRMED") return { label: "Start cooking", status: "PREPARING" };
  if (status === "PREPARING") return { label: "Mark ready", status: "READY" };
  if (status === "READY") return { label: "Served", status: "COMPLETED" };
  return null;
}

function OrderCard({
  card,
  busy,
  compact,
  onMove,
  onToggleItem,
  onPrint,
}: {
  card: KdsOrderCard;
  busy: boolean;
  compact: boolean;
  onMove: (orderId: string, status: KdsAdvanceStatus) => void;
  onToggleItem: (orderId: string, itemId: string, ready: boolean) => void;
  onPrint: (orderId: string) => void;
}) {
  const action = nextAction(card.status);
  const age = ageMeta(card.createdAt);
  const [touchStartX, setTouchStartX] = useState<number | null>(null);
  const [touchStartY, setTouchStartY] = useState<number | null>(null);

  function handleSwipeEnd(endX: number, endY: number) {
    if (!action || touchStartX === null || touchStartY === null || busy) return;
    const deltaX = endX - touchStartX;
    const deltaY = Math.abs(endY - touchStartY);
    if (deltaX > 70 && deltaY < 50) {
      onMove(card.orderId, action.status);
    }
  }

  const late = age.tone === "late" && card.status !== "READY";
  const meta = COLUMN_META[card.status];

  return (
    <article
      className={[
        "rounded-xl border border-t-4 bg-night-card p-4 transition-colors",
        late ? "border-danger border-t-danger animate-[pulseRing_2s_ease-out_infinite]" : `border-night-line ${meta.accent}`,
      ].join(" ")}
      onTouchStart={(event) => {
        setTouchStartX(event.changedTouches[0]?.clientX ?? null);
        setTouchStartY(event.changedTouches[0]?.clientY ?? null);
      }}
      onTouchEnd={(event) => {
        handleSwipeEnd(event.changedTouches[0]?.clientX ?? 0, event.changedTouches[0]?.clientY ?? 0);
        setTouchStartX(null);
        setTouchStartY(null);
      }}
    >
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-display text-2xl font-extrabold leading-none tracking-tight text-night-ink">
            {card.tableNumber ? `Table ${card.tableNumber}` : "Counter"}
          </p>
          <p className="mt-1.5 font-mono text-xs text-night-soft">
            #{card.orderId.slice(0, 8).toUpperCase()} · {new Date(card.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            {card.branchName && !compact ? ` · ${card.branchName}` : ""}
          </p>
        </div>
        <span
          className={[
            "flex shrink-0 items-center gap-1 rounded-lg px-2.5 py-1.5 font-display text-base font-bold tabular",
            age.tone === "late" ? "bg-danger text-white" : age.tone === "warn" ? "bg-warning text-white" : "bg-night-raised text-night-ink",
          ].join(" ")}
          aria-label={`Waiting ${age.label}${late ? ", running late" : ""}`}
        >
          <Icon name="timer" size={16} /> {age.label}
        </span>
      </header>
      <div className="mt-2 flex justify-end">
        <button type="button" onClick={() => onPrint(card.orderId)} className="flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-semibold text-night-soft hover:bg-night-raised hover:text-night-ink">
          <Icon name="print" size={14} /> Print ticket
        </button>
      </div>

      {card.notes && !compact ? (
        <p className="mt-3 flex gap-2 rounded-lg border border-warning/40 bg-warning/15 px-3 py-2 text-sm font-semibold text-warning-border">
          <Icon name="note" size={16} className="mt-0.5 shrink-0" /> {card.notes}
        </p>
      ) : null}

      <ul className={`mt-3 divide-y divide-night-line ${compact ? "text-base" : "text-lg"}`}>
        {card.items.map((item, index) => {
          const canTick = !!item.id && card.status !== "PENDING";
          return (
            <li key={item.id ?? `${item.name}-${index}`}>
              <button
                type="button"
                disabled={!canTick || busy}
                onClick={() => item.id && onToggleItem(card.orderId, item.id, !item.ready)}
                aria-pressed={!!item.ready}
                title={canTick ? (item.ready ? "Tap to mark not ready" : "Tap when this item is ready") : "Accept the order first"}
                className={`flex w-full gap-3 rounded-lg py-2 text-left transition-colors ${canTick ? "hover:bg-night-raised" : "cursor-default"} ${item.ready ? "opacity-55" : ""}`}
              >
                <span className={`grid h-8 min-w-8 shrink-0 place-items-center rounded-md px-1.5 font-display font-extrabold tabular ${item.ready ? "bg-success text-white" : "bg-night-raised text-gold"}`}>
                  {item.ready ? <Icon name="check" size={16} strokeWidth={3} /> : item.quantity}
                </span>
                <span className="min-w-0 pt-0.5">
                  <span className={`block font-semibold leading-snug text-night-ink ${item.ready ? "line-through decoration-2" : ""}`}>
                    {item.ready ? `${item.quantity} × ` : ""}{item.name}
                  </span>
                  {!compact && item.modifiers.length > 0 ? (
                    <span className="mt-0.5 block text-sm leading-snug text-night-soft">{item.modifiers.join(" · ")}</span>
                  ) : null}
                  {item.notes ? <span className="mt-0.5 block text-sm font-semibold italic text-warning-border">“{item.notes}”</span> : null}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {card.items.length > 1 && card.status !== "PENDING" ? (
        <p className="mt-1 text-xs text-night-soft">
          {card.items.filter((i) => i.ready).length}/{card.items.length} items ready · tap an item when it&apos;s done
        </p>
      ) : null}

      {action ? (
        <button
          type="button"
          className={`mt-4 btn btn-lg btn-block text-[17px] ${card.status === "READY" ? "btn-success" : "btn-primary"}`}
          disabled={busy}
          onClick={() => onMove(card.orderId, action.status)}
        >
          {busy ? <Spinner className="text-current" /> : <Icon name={card.status === "READY" ? "checkCircle" : "arrowRight"} size={20} />}
          {action.label}
        </button>
      ) : null}
    </article>
  );
}
