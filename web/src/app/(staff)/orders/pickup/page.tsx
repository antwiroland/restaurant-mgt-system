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
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Pickup Lookup</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link></section></main>;

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Pickup</p>
        <h1 className="text-2xl font-semibold">Pickup Code Lookup</h1>
        <form className="mt-4 flex gap-2" onSubmit={(event) => void onLookup(event)}>
          <input className="input" value={code} onChange={(e) => setCode(e.target.value)} placeholder="PK000001" />
          <button className="btn btn-primary">Search</button>
        </form>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {order ? (
          <div className="mt-4 rounded-xl border border-line p-4">
            <p className="font-semibold">Order #{order.id.slice(0, 8)}</p>
            <p className="text-sm text-ink-soft">Status: {order.status}</p>
            <p className="text-sm text-ink-soft">Total: GHS {order.total}</p>
            <Link className="mt-3 inline-flex btn btn-secondary" href={`/orders/${order.id}`}>Open Order</Link>
          </div>
        ) : null}
      </section>
    </main>
  );
}


