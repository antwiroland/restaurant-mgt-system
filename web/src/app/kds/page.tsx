"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { getKdsBoard, type KdsBoardRecord, type KdsOrderCard } from "@/lib/apiClient";

const COLUMNS: Array<keyof KdsBoardRecord["columns"]> = ["CONFIRMED", "PREPARING", "READY"];

export default function KdsPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [board, setBoard] = useState<KdsBoardRecord | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(async (activeSession) => {
      const next = await getKdsBoard(activeSession);
      setBoard(next);
    }).catch((err) => setError(err instanceof Error ? err.message : "Could not load KDS board"));
  }, [authenticatedFetch, session]);

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
        {error ? <p className="mt-4 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        <div className="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-3">
          {COLUMNS.map((column) => (
            <section key={column} className="rounded-xl border border-[#d6e4ce] bg-white p-3">
              <h2 className="font-semibold">{column}</h2>
              <div className="mt-3 space-y-3">
                {(board?.columns?.[column] ?? []).map((card) => (
                  <OrderCard key={card.orderId} card={card} />
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

function OrderCard({ card }: { card: KdsOrderCard }) {
  return (
    <article className="rounded-lg border border-[#d6e4ce] bg-[#fbfff7] p-3">
      <p className="text-sm font-semibold">#{card.orderId.slice(0, 8).toUpperCase()} · Table {card.tableNumber}</p>
      {card.branchName ? <p className="text-xs text-[#35523d]">{card.branchName}</p> : null}
      {card.notes ? <p className="mt-1 text-xs text-[#35523d]">{card.notes}</p> : null}
      <ul className="mt-2 space-y-1 text-sm">
        {card.items.map((item, index) => (
          <li key={`${item.name}-${index}`}>
            {item.quantity}x {item.name}
            {item.modifiers.length > 0 ? ` (${item.modifiers.join(", ")})` : ""}
          </li>
        ))}
      </ul>
    </article>
  );
}
