"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  addGroupItems,
  cancelGroupSession,
  createGroupSession,
  finalizeGroupSession,
  getGroupSession,
  getMenuItemModifiers,
  getMenuItems,
  getTables,
  joinGroupSession,
  leaveGroupSession,
  removeGroupItem,
  updateGroupItem,
  type GroupViewRecord,
  type MenuItemRecord,
  type MenuModifierGroupRecord,
  type TableRecord,
} from "@/lib/apiClient";

const POLL_MS = 5000;

export default function GroupOrderingPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [sessionCode, setSessionCode] = useState("");
  const [groupView, setGroupView] = useState<GroupViewRecord | null>(null);

  const [createName, setCreateName] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [joinName, setJoinName] = useState("");

  const [itemMenuId, setItemMenuId] = useState("");
  const [itemQty, setItemQty] = useState("1");
  const [itemNotes, setItemNotes] = useState("");
  const [modifierGroups, setModifierGroups] = useState<MenuModifierGroupRecord[]>([]);
  const [selectedOptions, setSelectedOptions] = useState<Record<string, string[]>>({});

  const [finalizeType, setFinalizeType] = useState<"DINE_IN" | "PICKUP" | "DELIVERY">("DINE_IN");
  const [finalizeTableId, setFinalizeTableId] = useState("");
  const [finalizeAddress, setFinalizeAddress] = useState("");
  const [finalizeNotes, setFinalizeNotes] = useState("");

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const userId = session?.user.id;
  const isHost = !!groupView && !!userId && groupView.hostUserId === userId;
  const isManager = session?.user.role === "ADMIN" || session?.user.role === "MANAGER";
  const isOpen = groupView?.status === "OPEN";
  const me = useMemo(
    () => groupView?.participants.find((participant) => participant.userId === userId),
    [groupView, userId],
  );

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(async (activeSession) => {
      const [items, tableList] = await Promise.all([getMenuItems(activeSession), getTables(activeSession)]);
      const available = items.filter((item) => item.available);
      setMenuItems(available);
      setTables(tableList);
      if (available.length > 0) setItemMenuId((current) => current || available[0].id);
      if (tableList.length > 0) setFinalizeTableId((current) => current || tableList[0].id);
    }).catch((err) => setError(err instanceof Error ? err.message : "Could not load group ordering data"));
  }, [session, authenticatedFetch]);

  // Modifier groups for the dish being added (size, spice, add-ons).
  useEffect(() => {
    if (!itemMenuId) return;
    let cancelled = false;
    setSelectedOptions({});
    getMenuItemModifiers(itemMenuId)
      .then((groups) => {
        if (!cancelled) setModifierGroups(groups);
      })
      .catch(() => {
        if (!cancelled) setModifierGroups([]);
      });
    return () => {
      cancelled = true;
    };
  }, [itemMenuId]);

  const loadView = useCallback(
    async (code: string) => {
      const view = await authenticatedFetch((activeSession) => getGroupSession(activeSession, code));
      setGroupView(view);
      return view;
    },
    [authenticatedFetch],
  );

  // Keep the cart current while other people edit it.
  useEffect(() => {
    if (!sessionCode || !isOpen) return;
    const timer = setInterval(() => {
      loadView(sessionCode).catch(() => undefined);
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [sessionCode, isOpen, loadView]);

  async function run(action: () => Promise<void>, fallback: string) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await action();
    } catch (err) {
      setError(err instanceof Error ? err.message : fallback);
    } finally {
      setBusy(false);
    }
  }

  function onCreateSession(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      const created = await authenticatedFetch((activeSession) => createGroupSession(activeSession, { displayName: createName || undefined }));
      setSessionCode(created.sessionCode);
      setJoinCode(created.sessionCode);
      await loadView(created.sessionCode);
      setMessage(`Session ${created.sessionCode} created. Share the code so others can join.`);
    }, "Could not create group session");
  }

  function onJoinSession(event: FormEvent) {
    event.preventDefault();
    const code = joinCode.trim();
    if (!code) return;
    void run(async () => {
      await authenticatedFetch((activeSession) => joinGroupSession(activeSession, code, { displayName: joinName || undefined }));
      setSessionCode(code);
      await loadView(code);
      setMessage(`Joined ${code}`);
    }, "Could not join group session");
  }

  function toggleOption(group: MenuModifierGroupRecord, optionId: string) {
    setSelectedOptions((current) => {
      const chosen = current[group.id] ?? [];
      if (group.selectionType === "SINGLE") {
        return { ...current, [group.id]: chosen.includes(optionId) && !group.required ? [] : [optionId] };
      }
      return {
        ...current,
        [group.id]: chosen.includes(optionId) ? chosen.filter((id) => id !== optionId) : [...chosen, optionId],
      };
    });
  }

  const missingRequired = modifierGroups.filter((group) => group.required && (selectedOptions[group.id] ?? []).length === 0);

  function onAddItem(event: FormEvent) {
    event.preventDefault();
    if (!sessionCode || !me || !itemMenuId) return;
    void run(async () => {
      const updated = await authenticatedFetch((activeSession) =>
        addGroupItems(activeSession, sessionCode, {
          participantId: me.participantId,
          items: [
            {
              menuItemId: itemMenuId,
              quantity: Math.max(1, Number.parseInt(itemQty || "1", 10)),
              notes: itemNotes || undefined,
              modifierOptionIds: Object.values(selectedOptions).flat(),
            },
          ],
        }),
      );
      setGroupView(updated);
      setItemNotes("");
      setItemQty("1");
      setSelectedOptions({});
      setMessage("Item added to the group cart");
    }, "Could not add item");
  }

  function onChangeQuantity(itemId: string, quantity: number, notes?: string) {
    if (quantity < 1) {
      onRemoveItem(itemId);
      return;
    }
    void run(async () => {
      setGroupView(await authenticatedFetch((activeSession) => updateGroupItem(activeSession, sessionCode, itemId, { quantity, notes })));
    }, "Could not change the item");
  }

  function onRemoveItem(itemId: string) {
    void run(async () => {
      setGroupView(await authenticatedFetch((activeSession) => removeGroupItem(activeSession, sessionCode, itemId)));
    }, "Could not remove the item");
  }

  function onLeave() {
    if (!window.confirm("Leave this group? Your items will be removed from the cart.")) return;
    void run(async () => {
      await authenticatedFetch((activeSession) => leaveGroupSession(activeSession, sessionCode));
      setGroupView(null);
      setSessionCode("");
      setMessage("You left the group");
    }, "Could not leave the group");
  }

  function onCancelSession() {
    if (!window.confirm("Cancel this group order for everyone? Nothing will be ordered.")) return;
    void run(async () => {
      setGroupView(await authenticatedFetch((activeSession) => cancelGroupSession(activeSession, sessionCode)));
      setMessage("Group session cancelled");
    }, "Could not cancel the group");
  }

  function onFinalize(event: FormEvent) {
    event.preventDefault();
    if (!sessionCode) return;
    void run(async () => {
      const order = await authenticatedFetch((activeSession) =>
        finalizeGroupSession(activeSession, sessionCode, {
          type: finalizeType,
          tableId: finalizeType === "DINE_IN" ? finalizeTableId || undefined : undefined,
          deliveryAddress: finalizeType === "DELIVERY" ? finalizeAddress || undefined : undefined,
          notes: finalizeNotes || undefined,
        }),
      );
      setMessage(`Group placed as order ${order.id.slice(0, 8)}`);
      await loadView(sessionCode);
    }, "Could not place the group order");
  }

  if (loading) return <main className="shell"><section className="panel">Loading group ordering...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Group Ordering</p><h1 className="heading-lg mt-1">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Group Ordering</p>
        <h1 className="heading-lg mt-1">Shared cart</h1>
        <p className="mt-2 text-sm text-ink-soft">
          Start or join a session with its code. Everyone adds and edits their own items; the host places one order.
          Sessions with no activity for a day are cancelled automatically.
        </p>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>

      {!groupView ? (
        <section className="grid gap-4 md:grid-cols-2">
          <article className="panel">
            <h2 className="font-display text-lg font-bold text-ink">Start a session</h2>
            <form className="mt-3 grid gap-2" onSubmit={onCreateSession}>
              <input className="input" placeholder="Your display name (optional)" value={createName} onChange={(e) => setCreateName(e.target.value)} />
              <button className="btn btn-primary" disabled={busy}>Start</button>
            </form>
          </article>

          <article className="panel">
            <h2 className="font-display text-lg font-bold text-ink">Join a session</h2>
            <form className="mt-3 grid gap-2" onSubmit={onJoinSession}>
              <input className="input" placeholder="Session code, e.g. GRP-1234" value={joinCode} onChange={(e) => setJoinCode(e.target.value.toUpperCase())} required />
              <input className="input" placeholder="Your display name (optional)" value={joinName} onChange={(e) => setJoinName(e.target.value)} />
              <button className="btn btn-primary" disabled={busy}>Join</button>
            </form>
          </article>
        </section>
      ) : null}

      {groupView ? (
        <section className="panel">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="font-display text-lg font-bold text-ink">
                <span className="font-mono">{groupView.sessionCode}</span>
              </h2>
              <p className="text-sm text-ink-soft">
                {groupView.status} · {groupView.participants.length} {groupView.participants.length === 1 ? "person" : "people"} · Total GHS {groupView.groupTotal}
                {isHost ? " · You are the host" : ""}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button className="btn btn-secondary btn-sm" disabled={busy} onClick={() => void run(async () => void (await loadView(sessionCode)), "Could not refresh")}>Refresh</button>
              {isOpen && me && !isHost ? <button className="btn btn-secondary btn-sm" disabled={busy} onClick={onLeave}>Leave group</button> : null}
              {isOpen && (isHost || isManager) ? <button className="btn btn-danger btn-sm" disabled={busy} onClick={onCancelSession}>Cancel session</button> : null}
              {!isOpen ? (
                <button className="btn btn-secondary btn-sm" onClick={() => { setGroupView(null); setSessionCode(""); setMessage(""); }}>Start another</button>
              ) : null}
            </div>
          </div>

          <div className="mt-4 grid gap-3">
            {groupView.participants.map((participant) => {
              const mine = participant.userId === userId;
              return (
                <article key={participant.participantId} className="rounded-xl border border-line bg-surface p-3">
                  <p className="font-semibold">
                    {participant.displayName}
                    {participant.userId === groupView.hostUserId ? " (host)" : ""}
                    {mine ? " · you" : ""} · GHS {participant.subtotal}
                  </p>
                  {participant.items.length === 0 ? (
                    <p className="mt-1 text-sm text-ink-soft">Nothing added yet.</p>
                  ) : (
                    <ul className="mt-2 grid gap-2 text-sm">
                      {participant.items.map((item) => (
                        <li key={item.itemId} className="flex flex-wrap items-center justify-between gap-2">
                          <span>
                            {item.name} × {item.quantity} · GHS {item.price} each
                            {item.modifiers && item.modifiers.length > 0 ? (
                              <span className="block text-xs text-ink-soft">
                                {item.modifiers.map((m) => `${m.groupName}: ${m.optionName}`).join(", ")}
                              </span>
                            ) : null}
                            {item.notes ? <span className="block text-xs text-ink-soft">Note: {item.notes}</span> : null}
                          </span>
                          {isOpen && (mine || isHost) ? (
                            <span className="flex items-center gap-1">
                              {mine ? (
                                <>
                                  <button className="btn btn-secondary btn-sm" aria-label={`One less ${item.name}`} disabled={busy} onClick={() => onChangeQuantity(item.itemId, item.quantity - 1, item.notes)}>−</button>
                                  <button className="btn btn-secondary btn-sm" aria-label={`One more ${item.name}`} disabled={busy} onClick={() => onChangeQuantity(item.itemId, item.quantity + 1, item.notes)}>+</button>
                                </>
                              ) : null}
                              <button className="btn btn-secondary btn-sm" disabled={busy} onClick={() => onRemoveItem(item.itemId)}>Remove</button>
                            </span>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  )}
                </article>
              );
            })}
          </div>
        </section>
      ) : null}

      {groupView && isOpen ? (
        <section className="grid gap-4 md:grid-cols-2">
          {me ? (
            <article className="panel">
              <h2 className="font-display text-lg font-bold text-ink">Add an item</h2>
              <form className="mt-3 grid gap-2" onSubmit={onAddItem}>
                <select className="select" value={itemMenuId} onChange={(e) => setItemMenuId(e.target.value)}>
                  {menuItems.map((item) => <option key={item.id} value={item.id}>{item.name} · GHS {item.price}</option>)}
                </select>
                {modifierGroups.map((group) => (
                  <fieldset key={group.id} className="rounded-xl border border-line p-2">
                    <legend className="px-1 text-xs font-semibold text-ink">
                      {group.name} {group.required ? "(required)" : "(optional)"}
                      {group.selectionType === "MULTIPLE" ? " · choose any" : " · choose one"}
                    </legend>
                    <div className="flex flex-wrap gap-3 text-sm">
                      {group.options.map((option) => (
                        <label key={option.id} className="inline-flex items-center gap-1">
                          <input
                            type={group.selectionType === "SINGLE" ? "radio" : "checkbox"}
                            name={`modifier-${group.id}`}
                            checked={(selectedOptions[group.id] ?? []).includes(option.id)}
                            onChange={() => toggleOption(group, option.id)}
                          />
                          {option.name}
                          {Number(option.priceDelta) !== 0 ? ` (+${option.priceDelta})` : ""}
                        </label>
                      ))}
                    </div>
                  </fieldset>
                ))}
                <input className="input" type="number" min={1} placeholder="Quantity" value={itemQty} onChange={(e) => setItemQty(e.target.value)} required />
                <input className="input" placeholder="Notes (optional)" value={itemNotes} onChange={(e) => setItemNotes(e.target.value)} />
                {missingRequired.length > 0 ? (
                  <p className="text-xs text-ink-soft">Choose: {missingRequired.map((group) => group.name).join(", ")}</p>
                ) : null}
                <button className="btn btn-secondary" disabled={busy || missingRequired.length > 0}>Add to cart</button>
              </form>
            </article>
          ) : (
            <article className="panel">
              <p className="text-sm text-ink-soft">You are viewing this session as staff. Join it with the code to add items.</p>
            </article>
          )}

          {isHost ? (
            <article className="panel">
              <h2 className="font-display text-lg font-bold text-ink">Place the group order</h2>
              <form className="mt-3 grid gap-2" onSubmit={onFinalize}>
                <select className="select" value={finalizeType} onChange={(e) => setFinalizeType(e.target.value as "DINE_IN" | "PICKUP" | "DELIVERY")}>
                  <option value="DINE_IN">Dine in</option>
                  <option value="PICKUP">Pickup</option>
                  <option value="DELIVERY">Delivery</option>
                </select>
                {finalizeType === "DINE_IN" ? (
                  <select className="select" value={finalizeTableId} onChange={(e) => setFinalizeTableId(e.target.value)}>
                    {tables.map((table) => <option key={table.id} value={table.id}>Table {table.number}</option>)}
                  </select>
                ) : null}
                {finalizeType === "DELIVERY" ? (
                  <input className="input" placeholder="Delivery address" value={finalizeAddress} onChange={(e) => setFinalizeAddress(e.target.value)} required />
                ) : null}
                <input className="input" placeholder="Order notes (optional)" value={finalizeNotes} onChange={(e) => setFinalizeNotes(e.target.value)} />
                <button className="btn btn-primary" disabled={busy || Number(groupView.groupTotal) <= 0}>Place order</button>
              </form>
            </article>
          ) : null}
        </section>
      ) : null}

      {groupView?.orderId ? (
        <section className="panel">
          <p className="text-sm">
            This group was placed as an order.{" "}
            <Link className="underline" href={`/orders/${groupView.orderId}`}>Open the order</Link> to split the bill or take payment.
          </p>
        </section>
      ) : null}
    </main>
  );
}
