"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  addGroupItems,
  createGroupSession,
  finalizeGroupSession,
  getGroupSession,
  getMenuItems,
  getTables,
  joinGroupSession,
  type GroupViewRecord,
  type MenuItemRecord,
  type TableRecord,
} from "@/lib/apiClient";

export default function GroupOrderingPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [sessionCode, setSessionCode] = useState("");
  const [participantId, setParticipantId] = useState("");
  const [groupView, setGroupView] = useState<GroupViewRecord | null>(null);

  const [createName, setCreateName] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [joinName, setJoinName] = useState("");

  const [itemMenuId, setItemMenuId] = useState("");
  const [itemQty, setItemQty] = useState("1");
  const [itemNotes, setItemNotes] = useState("");

  const [finalizeType, setFinalizeType] = useState<"DINE_IN" | "PICKUP" | "DELIVERY">("DINE_IN");
  const [finalizeTableId, setFinalizeTableId] = useState("");
  const [finalizeAddress, setFinalizeAddress] = useState("");
  const [finalizeNotes, setFinalizeNotes] = useState("");

  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(async (activeSession) => {
      const [items, tableList] = await Promise.all([getMenuItems(activeSession), getTables(activeSession)]);
      setMenuItems(items.filter((item) => item.available));
      setTables(tableList);
      if (items.length > 0) setItemMenuId((current) => current || items[0].id);
      if (tableList.length > 0) setFinalizeTableId((current) => current || tableList[0].id);
    }).catch((err) => setError(err instanceof Error ? err.message : "Could not load group ordering data"));
  }, [session, authenticatedFetch]);

  const selectedItem = useMemo(() => menuItems.find((item) => item.id === itemMenuId), [menuItems, itemMenuId]);

  async function onCreateSession(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    try {
      setError("");
      const created = await authenticatedFetch((activeSession) => createGroupSession(activeSession, { displayName: createName || undefined }));
      setSessionCode(created.sessionCode);
      setJoinCode(created.sessionCode);
      setMessage(`Session created: ${created.sessionCode}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create group session");
    }
  }

  async function onJoinSession(event: FormEvent) {
    event.preventDefault();
    if (!session || !joinCode.trim()) return;
    try {
      setError("");
      const joined = await authenticatedFetch((activeSession) => joinGroupSession(activeSession, joinCode.trim(), { displayName: joinName || undefined }));
      setSessionCode(joinCode.trim());
      setParticipantId(joined.participantId);
      setMessage(`Joined ${joinCode.trim()}`);
      const view = await authenticatedFetch((activeSession) => getGroupSession(activeSession, joinCode.trim()));
      setGroupView(view);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not join group session");
    }
  }

  async function refreshView() {
    if (!session || !sessionCode) return;
    try {
      const view = await authenticatedFetch((activeSession) => getGroupSession(activeSession, sessionCode));
      setGroupView(view);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load group session");
    }
  }

  async function onAddItem(event: FormEvent) {
    event.preventDefault();
    if (!session || !sessionCode || !participantId || !itemMenuId) return;
    try {
      setError("");
      const updated = await authenticatedFetch((activeSession) =>
        addGroupItems(activeSession, sessionCode, {
          participantId,
          items: [
            {
              menuItemId: itemMenuId,
              quantity: Number.parseInt(itemQty || "1", 10),
              notes: itemNotes || undefined,
            },
          ],
        }),
      );
      setGroupView(updated);
      setItemNotes("");
      setMessage("Item added to group cart");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not add item");
    }
  }

  async function onFinalize(event: FormEvent) {
    event.preventDefault();
    if (!session || !sessionCode) return;
    try {
      setError("");
      const order = await authenticatedFetch((activeSession) =>
        finalizeGroupSession(activeSession, sessionCode, {
          type: finalizeType,
          tableId: finalizeType === "DINE_IN" ? finalizeTableId || undefined : undefined,
          deliveryAddress: finalizeType === "DELIVERY" ? finalizeAddress || undefined : undefined,
          notes: finalizeNotes || undefined,
        }),
      );
      setMessage(`Group finalized as order ${order.id.slice(0, 8)}`);
      await refreshView();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not finalize group");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading group ordering...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Group Ordering</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Group Ordering</p>
        <h1 className="text-2xl font-semibold">Session Management</h1>
        <p className="mt-2 text-sm text-ink-soft">Create or join a session, add participant items, and finalize to one order.</p>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Create Session</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onCreateSession(event)}>
            <input className="input" placeholder="Host display name" value={createName} onChange={(e) => setCreateName(e.target.value)} />
            <button className="btn btn-primary">Create</button>
          </form>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Join Session</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onJoinSession(event)}>
            <input className="input" placeholder="Session code" value={joinCode} onChange={(e) => setJoinCode(e.target.value.toUpperCase())} required />
            <input className="input" placeholder="Participant display name" value={joinName} onChange={(e) => setJoinName(e.target.value)} />
            <button className="btn btn-primary">Join</button>
          </form>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Add Participant Item</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onAddItem(event)}>
            <input className="input" placeholder="Active session code" value={sessionCode} onChange={(e) => setSessionCode(e.target.value.toUpperCase())} required />
            <input className="input" placeholder="Participant ID" value={participantId} onChange={(e) => setParticipantId(e.target.value)} required />
            <select className="input" value={itemMenuId} onChange={(e) => setItemMenuId(e.target.value)}>
              {menuItems.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
            <input className="input" type="number" min={1} placeholder="Quantity" value={itemQty} onChange={(e) => setItemQty(e.target.value)} required />
            <input className="input" placeholder="Notes" value={itemNotes} onChange={(e) => setItemNotes(e.target.value)} />
            <button className="btn btn-secondary">Add Item</button>
          </form>
          {selectedItem ? <p className="mt-2 text-xs text-ink-soft">Selected: {selectedItem.name} ({selectedItem.price})</p> : null}
          <button className="mt-3 btn btn-secondary btn-sm" onClick={() => void refreshView()}>Refresh Session</button>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Finalize Session</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onFinalize(event)}>
            <select className="input" value={finalizeType} onChange={(e) => setFinalizeType(e.target.value as "DINE_IN" | "PICKUP" | "DELIVERY")}>
              <option value="DINE_IN">DINE_IN</option>
              <option value="PICKUP">PICKUP</option>
              <option value="DELIVERY">DELIVERY</option>
            </select>
            {finalizeType === "DINE_IN" ? (
              <select className="input" value={finalizeTableId} onChange={(e) => setFinalizeTableId(e.target.value)}>
                {tables.map((table) => <option key={table.id} value={table.id}>Table {table.number}</option>)}
              </select>
            ) : null}
            {finalizeType === "DELIVERY" ? (
              <input className="input" placeholder="Delivery address" value={finalizeAddress} onChange={(e) => setFinalizeAddress(e.target.value)} required />
            ) : null}
            <input className="input" placeholder="Finalize notes" value={finalizeNotes} onChange={(e) => setFinalizeNotes(e.target.value)} />
            <button className="btn btn-primary">Finalize Group</button>
          </form>
        </article>
      </section>

      <section className="panel">
        <h2 className="text-lg font-semibold">Session View</h2>
        {!groupView ? (
          <p className="mt-3 text-sm text-ink-soft">No session loaded.</p>
        ) : (
          <div className="mt-3 grid gap-3">
            <p className="text-sm">Code: <span className="font-mono">{groupView.sessionCode}</span> · Status: {groupView.status} · Total: GHS {groupView.groupTotal}</p>
            {groupView.participants.map((participant) => (
              <article key={participant.participantId} className="input">
                <p className="font-semibold">{participant.displayName} · Subtotal GHS {participant.subtotal}</p>
                <ul className="mt-2 grid gap-1 text-sm">
                  {participant.items.map((item) => (
                    <li key={item.itemId}>{item.name} x {item.quantity} ({item.price}) {item.notes ? `- ${item.notes}` : ""}</li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}


