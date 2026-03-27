"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { closeTableSession, getTableBill, reverseTableSession, type TableBillRecord } from "@/lib/apiClient";

export default function TableBillPage() {
  const params = useParams<{ id: string }>();
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [bill, setBill] = useState<TableBillRecord | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function loadBill() {
    if (!session || !params?.id) return;
    const next = await authenticatedFetch((activeSession) => getTableBill(activeSession, params.id));
    setBill(next);
  }

  useEffect(() => {
    loadBill().catch((err) => setError(err instanceof Error ? err.message : "Could not load table bill"));
  }, [session, params?.id]);

  async function closeTable() {
    if (!session || !params?.id) return;
    setError("");
    try {
      await authenticatedFetch((activeSession) => closeTableSession(activeSession, params.id));
      setMessage("Table closed.");
      await loadBill();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not close table");
    }
  }

  async function reverseTable() {
    if (!session || !params?.id) return;
    setError("");
    try {
      await authenticatedFetch((activeSession) => reverseTableSession(activeSession, params.id));
      setMessage("Table reversed by manager.");
      await loadBill();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not reverse table");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading table bill...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Table Bill</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Table Bill</p>
        <h1 className="text-2xl font-semibold">{bill ? `Table ${bill.tableNumber}` : "Table"}</h1>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}

        {bill ? (
          <div className="mt-4 rounded-xl border border-line p-4 text-sm text-ink-soft">
            <p>Status: {bill.tableStatus}</p>
            <p>Active orders: {bill.activeOrderCount}</p>
            <p>Total ordered: GHS {bill.totalOrdered}</p>
            <p>Total paid: GHS {bill.totalPaid}</p>
            <p className="font-semibold">Outstanding: GHS {bill.outstandingTotal}</p>
          </div>
        ) : null}

        <div className="mt-4 flex gap-2">
          <button className="btn btn-secondary" onClick={() => void closeTable()}>Close Table</button>
          <button className="btn btn-danger" onClick={() => void reverseTable()}>Reverse Table</button>
          <Link className="btn btn-secondary" href="/tables">Back</Link>
        </div>
      </section>
    </main>
  );
}


