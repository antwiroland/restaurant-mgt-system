"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { getOrderByPickupCode, type OrderRecord } from "@/lib/apiClient";

export default function PickupLookupPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [code, setCode] = useState("");
  const [order, setOrder] = useState<OrderRecord | null>(null);
  const [error, setError] = useState("");

  async function onLookup(event: FormEvent) {
    event.preventDefault();
    if (!session || !code.trim()) return;
    setError("");
    try {
      const next = await authenticatedFetch((activeSession) => getOrderByPickupCode(activeSession, code.trim()));
      setOrder(next);
    } catch (err) {
      setOrder(null);
      setError(err instanceof Error ? err.message : "Could not find order");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Pickup Lookup</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link></section></main>;

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Pickup</p>
        <h1 className="text-2xl font-semibold">Pickup Code Lookup</h1>
        <form className="mt-4 flex gap-2" onSubmit={(event) => void onLookup(event)}>
          <input className="rounded-xl border border-[#cfe0c8] p-3" value={code} onChange={(e) => setCode(e.target.value)} placeholder="PK000001" />
          <button className="rounded-full bg-[#132018] px-4 py-2 text-white">Search</button>
        </form>
        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        {order ? (
          <div className="mt-4 rounded-xl border border-[#cfe0c8] p-4">
            <p className="font-semibold">Order #{order.id.slice(0, 8)}</p>
            <p className="text-sm text-[#35523d]">Status: {order.status}</p>
            <p className="text-sm text-[#35523d]">Total: GHS {order.total}</p>
            <Link className="mt-3 inline-flex rounded-full border border-[#132018] px-4 py-2 text-sm text-[#132018]" href={`/orders/${order.id}`}>Open Order</Link>
          </div>
        ) : null}
      </section>
    </main>
  );
}
