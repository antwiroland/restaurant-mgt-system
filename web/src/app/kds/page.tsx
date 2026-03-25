"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
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
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

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
    return <main className="shell"><section className="panel">Loading kitchen board...</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">KDS</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Kitchen Display</p>
        <h1 className="text-2xl font-semibold">Order Flow Board</h1>
        <p className="mt-2 text-[#35523d]">Live queue replacing verbal/printer-only handoff.</p>
        <div className="mt-3 flex items-center gap-3 text-xs text-[#35523d]">
          <select
            value={branchId}
            onChange={(event) => setBranchId(event.target.value)}
            className="rounded-full border border-[#132018] bg-white px-3 py-1 text-xs font-semibold text-[#132018]"
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
            className="rounded-full border border-[#132018] px-3 py-1 font-semibold text-[#132018]"
          >
            Refresh
          </button>
          <span>{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString()}` : "Not refreshed yet"}</span>
        </div>
        {error ? <p className="mt-4 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          {COLUMNS.map((column) => (
            <section key={column} className="rounded-xl border border-[#d6e4ce] bg-white p-3">
              <h2 className="font-semibold">{column}</h2>
              <div className="mt-3 space-y-3">
                {(board?.columns?.[column] ?? []).map((card) => (
                  <OrderCard key={card.orderId} card={card} busy={busyOrderId === card.orderId} onMove={moveOrder} />
                ))}
                {(board?.columns?.[column] ?? []).length === 0 ? (
                  <p className="text-sm text-[#35523d]">No orders</p>
                ) : null}
              </div>
            </section>
          ))}
        </div>
      </section>
    </main>
  );
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
  onMove,
}: {
  card: KdsOrderCard;
  busy: boolean;
  onMove: (orderId: string, status: KdsAdvanceStatus) => void;
}) {
  const action = nextAction(card.status);

  return (
    <article className="rounded-lg border border-[#d6e4ce] bg-[#fbfff7] p-3">
      <p className="text-sm font-semibold">#{card.orderId.slice(0, 8).toUpperCase()} - Table {card.tableNumber}</p>
      {card.branchName ? <p className="text-xs text-[#35523d]">{card.branchName}</p> : null}
      {card.notes ? <p className="mt-1 text-xs text-[#35523d]">{card.notes}</p> : null}
      <p className="mt-1 text-xs text-[#35523d]">Created {new Date(card.createdAt).toLocaleTimeString()}</p>
      <ul className="mt-2 space-y-1 text-sm">
        {card.items.map((item, index) => (
          <li key={`${item.name}-${index}`}>
            {item.quantity}x {item.name}
            {item.modifiers.length > 0 ? ` (${item.modifiers.join(", ")})` : ""}
          </li>
        ))}
      </ul>
      {action ? (
        <button
          type="button"
          className="mt-3 rounded-full border border-[#132018] px-3 py-1 text-xs font-semibold text-[#132018] disabled:opacity-50"
          disabled={busy}
          onClick={() => onMove(card.orderId, action.status)}
        >
          {busy ? "Updating..." : action.label}
        </button>
      ) : null}
    </article>
  );
}
