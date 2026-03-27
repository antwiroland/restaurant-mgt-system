"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { useStaffSession } from "@/components/SessionProvider";
import { getBranches, getKdsBoard, updateOrderStatus, type BranchRecord, type KdsBoardRecord, type KdsOrderCard } from "@/lib/apiClient";

const COLUMNS: Array<keyof KdsBoardRecord["columns"]> = ["PENDING", "CONFIRMED", "PREPARING", "READY"];
const POLL_INTERVAL_MS = 15000;

type KdsAdvanceStatus = "CONFIRMED" | "PREPARING" | "READY" | "COMPLETED";

export default function KdsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [board, setBoard] = useState<KdsBoardRecord | null>(null);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState<string>("");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);
  const [realtimeConnected, setRealtimeConnected] = useState(false);
  const [compactMode, setCompactMode] = useState(false);

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
    const wsUrl = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws";
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
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
    });
    client.activate();

    return () => {
      setRealtimeConnected(false);
      client.deactivate();
    };
  }, [session, authenticatedFetch, branchId]);

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

  if (loading) {
    return (
      <main className="shell">
        <section className="panel grid gap-4">
          <Skeleton className="h-6 w-44" />
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, col) => (
              <div key={col} className="grid gap-3">
                <Skeleton className="h-8 w-24" />
                <Skeleton className="h-36 w-full" />
                <Skeleton className="h-36 w-full" />
                <Skeleton className="h-36 w-full" />
              </div>
            ))}
          </div>
        </section>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">KDS</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Kitchen Display</p>
        <h1 className="text-2xl font-semibold">Order Flow Board</h1>
        <p className="mt-2 text-ink-soft">Live queue replacing verbal/printer-only handoff.</p>
        <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-ink-soft">
          <select
            value={branchId}
            onChange={(event) => setBranchId(event.target.value)}
            className="select max-w-[220px]"
          >
            <option value="">All branches</option>
            {branches.map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => loadBoard().catch((err) => setError(err instanceof Error ? err.message : "Could not refresh KDS board"))}
            className="btn btn-secondary btn-md font-semibold text-ink"
          >
            Refresh
          </button>
          <span>{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString()}` : "Not refreshed yet"}</span>
          <span className={realtimeConnected ? "text-success-on" : "text-warning-on"}>
            {realtimeConnected ? "Realtime: connected" : "Realtime: reconnecting"}
          </span>
          <button
            type="button"
            className={`btn btn-sm ${compactMode ? "btn-primary" : "btn-secondary"}`}
            onClick={() => setCompactMode((value) => !value)}
          >
            {compactMode ? "Expanded" : "Compact"} Mode
          </button>
        </div>
        <input
          className="mt-3 w-full input text-sm"
          placeholder="Search order, table, branch, or item"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {error ? <p className="mt-4 alert alert-danger">{error}</p> : null}
        <p className="mt-3 text-xs text-ink-soft">Tip: swipe right on a card to advance status.</p>
        <div className="mt-5 grid grid-flow-col auto-cols-[minmax(280px,1fr)] gap-4 overflow-x-auto xl:grid-flow-row xl:grid-cols-4">
          {COLUMNS.map((column) => {
            const cards = (board?.columns?.[column] ?? []).filter((card) => matchesCardQuery(card, query));
            return (
              <section key={column} aria-live="polite" className="rounded-xl border border-line bg-surface p-3">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-base font-semibold">{column}</h2>
                  <span className="badge badge-info">{cards.length}</span>
                </div>
                <div className="mt-3 space-y-3">
                  {cards.map((card) => (
                    <OrderCard
                      key={card.orderId}
                      card={card}
                      busy={busyOrderId === card.orderId}
                      compact={compactMode}
                      onMove={moveOrder}
                    />
                  ))}
                  {cards.length === 0 ? (
                    <p className="text-sm text-ink-soft">No orders</p>
                  ) : null}
                </div>
              </section>
            );
          })}
        </div>
      </section>
    </main>
  );
}

function minutesSince(isoTime: string): number {
  const created = new Date(isoTime).getTime();
  if (Number.isNaN(created)) return 0;
  return Math.max(0, Math.floor((Date.now() - created) / 60000));
}

function ageMeta(isoTime: string): { label: string; badgeClass: string } {
  const mins = minutesSince(isoTime);
  if (mins <= 5) return { label: `${mins}m`, badgeClass: "badge-success" };
  if (mins <= 12) return { label: `${mins}m`, badgeClass: "badge-warning" };
  return { label: `${mins}m`, badgeClass: "badge-danger" };
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
  if (status === "CONFIRMED") return { label: "Start Prep", status: "PREPARING" };
  if (status === "PREPARING") return { label: "Mark Ready", status: "READY" };
  if (status === "READY") return { label: "Complete", status: "COMPLETED" };
  return null;
}

function OrderCard({
  card,
  busy,
  compact,
  onMove,
}: {
  card: KdsOrderCard;
  busy: boolean;
  compact: boolean;
  onMove: (orderId: string, status: KdsAdvanceStatus) => void;
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

  return (
    <article
      className="rounded-lg border border-line bg-surface p-3"
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
      <div className="flex items-start justify-between gap-2">
        <p className="text-base font-semibold">#{card.orderId.slice(0, 8).toUpperCase()} - Table {card.tableNumber}</p>
        <span className={`badge ${age.badgeClass}`}>{age.label}</span>
      </div>
      {!compact ? (
        <>
          {card.branchName ? <p className="text-xs text-ink-soft">{card.branchName}</p> : null}
          {card.notes ? <p className="mt-1 text-xs text-ink-soft">{card.notes}</p> : null}
          <p className="mt-1 text-xs text-ink-soft">Created {new Date(card.createdAt).toLocaleTimeString()}</p>
        </>
      ) : null}
      <ul className={`mt-2 space-y-1 ${compact ? "text-base" : "text-lg"}`}>
        {card.items.map((item, index) => (
          <li key={`${item.name}-${index}`}>
            {item.quantity}x {item.name}
            {!compact && item.modifiers.length > 0 ? ` (${item.modifiers.join(", ")})` : ""}
          </li>
        ))}
      </ul>
      {action ? (
        <button
          type="button"
          className="mt-3 btn btn-primary btn-lg btn-block font-semibold disabled:opacity-50"
          disabled={busy}
          onClick={() => onMove(card.orderId, action.status)}
        >
          {busy ? <Spinner className="text-current" /> : null}
          {action.label}
        </button>
      ) : null}
    </article>
  );
}


