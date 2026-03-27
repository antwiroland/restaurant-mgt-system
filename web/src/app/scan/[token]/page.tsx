"use client";

import { Client } from "@stomp/stompjs";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import {
  createPublicDineInOrder,
  getMenuItemModifiers,
  getPublicMenuItems,
  getPublicTableTracking,
  getTableScan,
  type MenuItemRecord,
  type MenuModifierGroupRecord,
  type PublicOrderTrackingRecord,
  type TableScanRecord,
} from "@/lib/apiClient";

type CartLine = {
  key: string;
  item: MenuItemRecord;
  quantity: number;
  unitPrice: number;
  modifierOptionIds: string[];
  modifierSummary: string[];
};

type SelectionByGroup = Record<string, string[]>;

const STATUS_BADGE: Record<PublicOrderTrackingRecord["status"], string> = {
  PENDING: "badge-pending",
  CONFIRMED: "badge-confirmed",
  PREPARING: "badge-preparing",
  READY: "badge-ready",
  COMPLETED: "badge-completed",
  CANCELLED: "badge-cancelled",
  VOIDED: "badge-voided",
};

function toCurrencyValue(value: string): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function buildInitialSelection(groups: MenuModifierGroupRecord[]): SelectionByGroup {
  const initial: SelectionByGroup = {};
  for (const group of groups) {
    if (group.required && group.options.length > 0) {
      if (group.selectionType === "SINGLE") {
        initial[group.id] = [group.options[0].id];
      } else {
        const min = Math.max(group.minSelect ?? 1, 1);
        initial[group.id] = group.options.slice(0, min).map((option) => option.id);
      }
    } else {
      initial[group.id] = [];
    }
  }
  return initial;
}

export default function GuestScanMenuPage() {
  const params = useParams<{ token: string }>();
  const [token, setToken] = useState("");
  const [table, setTable] = useState<TableScanRecord | null>(null);
  const [menu, setMenu] = useState<MenuItemRecord[]>([]);
  const [activeCategory, setActiveCategory] = useState("ALL");
  const [modifiersByItemId, setModifiersByItemId] = useState<Record<string, MenuModifierGroupRecord[]>>({});
  const [selectionByItemId, setSelectionByItemId] = useState<Record<string, SelectionByGroup>>({});
  const [cart, setCart] = useState<CartLine[]>([]);
  const [trackedOrders, setTrackedOrders] = useState<PublicOrderTrackingRecord[]>([]);
  const [customerName, setCustomerName] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [realtimeConnected, setRealtimeConnected] = useState(false);
  const checkoutRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!params?.token) {
      setError("Invalid table QR link");
      return;
    }
    setToken(params.token);
  }, [params]);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    Promise.all([getTableScan(token), getPublicMenuItems()])
      .then(async ([tableResponse, menuResponse]) => {
        const availableMenu = menuResponse.filter((item) => item.available);
        setTable(tableResponse);
        setMenu(availableMenu);

        const modifierEntries = await Promise.all(
          availableMenu.map(async (item) => {
            const groups = await getMenuItemModifiers(item.id);
            return [item.id, groups] as const;
          }),
        );

        const modifierMap = Object.fromEntries(modifierEntries);
        setModifiersByItemId(modifierMap);
        setSelectionByItemId((previous) => {
          const next: Record<string, SelectionByGroup> = { ...previous };
          for (const [itemId, groups] of modifierEntries) {
            if (!next[itemId]) {
              next[itemId] = buildInitialSelection(groups);
            }
          }
          return next;
        });
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load table menu"))
      .finally(() => setLoading(false));
  }, [token]);

  useEffect(() => {
    if (!token) return;
    getPublicTableTracking(token)
      .then(setTrackedOrders)
      .catch(() => {});
  }, [token]);

  useEffect(() => {
    if (!token) return;
    const wsUrl = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws";
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      onConnect: () => {
        setRealtimeConnected(true);
        client.subscribe(`/topic/public.tables.${token}.orders`, () => {
          getPublicTableTracking(token).then(setTrackedOrders).catch(() => {});
        });
      },
      onWebSocketClose: () => setRealtimeConnected(false),
      onStompError: () => setRealtimeConnected(false),
    });
    client.activate();
    return () => {
      setRealtimeConnected(false);
      client.deactivate();
    };
  }, [token]);

  const categories = useMemo(() => {
    const names = Array.from(new Set(menu.map((item) => item.categoryName))).sort((a, b) => a.localeCompare(b));
    return ["ALL", ...names];
  }, [menu]);

  useEffect(() => {
    if (!categories.includes(activeCategory)) {
      setActiveCategory(categories[0] ?? "ALL");
    }
  }, [categories, activeCategory]);

  const filteredMenu = useMemo(() => {
    if (activeCategory === "ALL") return menu;
    return menu.filter((item) => item.categoryName === activeCategory);
  }, [menu, activeCategory]);

  const total = useMemo(
    () => cart.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0),
    [cart],
  );

  const cartItemsCount = useMemo(
    () => cart.reduce((sum, line) => sum + line.quantity, 0),
    [cart],
  );

  function setSingleSelection(itemId: string, groupId: string, optionId: string) {
    setSelectionByItemId((previous) => ({
      ...previous,
      [itemId]: {
        ...(previous[itemId] ?? {}),
        [groupId]: optionId ? [optionId] : [],
      },
    }));
  }

  function toggleMultiSelection(itemId: string, group: MenuModifierGroupRecord, optionId: string) {
    setSelectionByItemId((previous) => {
      const selected = previous[itemId]?.[group.id] ?? [];
      const exists = selected.includes(optionId);
      const nextSelected = exists ? selected.filter((id) => id !== optionId) : [...selected, optionId];
      if (!exists && group.maxSelect != null && nextSelected.length > group.maxSelect) {
        setError(`Only ${group.maxSelect} option(s) allowed for ${group.name}.`);
        return previous;
      }

      return {
        ...previous,
        [itemId]: {
          ...(previous[itemId] ?? {}),
          [group.id]: nextSelected,
        },
      };
    });
  }

  function resolveSelection(item: MenuItemRecord): {
    key: string;
    modifierOptionIds: string[];
    modifierSummary: string[];
    unitPrice: number;
  } {
    const groups = modifiersByItemId[item.id] ?? [];
    const selectedByGroup = selectionByItemId[item.id] ?? {};

    const optionIds: string[] = [];
    const summary: string[] = [];
    let modifierDelta = 0;

    for (const group of groups) {
      const selectedIds = selectedByGroup[group.id] ?? [];
      const min = group.minSelect ?? (group.required ? 1 : 0);
      const max = group.maxSelect;

      if (group.required && selectedIds.length === 0) {
        throw new Error(`Please choose ${group.name}.`);
      }
      if (selectedIds.length < min) {
        throw new Error(`Choose at least ${min} option(s) for ${group.name}.`);
      }
      if (max != null && selectedIds.length > max) {
        throw new Error(`Choose at most ${max} option(s) for ${group.name}.`);
      }
      if (group.selectionType === "SINGLE" && selectedIds.length > 1) {
        throw new Error(`Only one option is allowed for ${group.name}.`);
      }

      for (const optionId of selectedIds) {
        const option = group.options.find((candidate) => candidate.id === optionId);
        if (!option) continue;
        optionIds.push(option.id);
        summary.push(`${group.name}: ${option.name}`);
        modifierDelta += toCurrencyValue(option.priceDelta);
      }
    }

    const sortedOptionIds = [...optionIds].sort();
    const key = `${item.id}:${sortedOptionIds.join(",") || "base"}`;

    return {
      key,
      modifierOptionIds: sortedOptionIds,
      modifierSummary: summary,
      unitPrice: toCurrencyValue(item.price) + modifierDelta,
    };
  }

  function addConfiguredItem(item: MenuItemRecord) {
    try {
      setError("");
      const resolved = resolveSelection(item);
      setCart((previous) => {
        const existing = previous.find((line) => line.key === resolved.key);
        if (existing) {
          return previous.map((line) => (line.key === resolved.key ? { ...line, quantity: line.quantity + 1 } : line));
        }
        return [...previous, {
          key: resolved.key,
          item,
          quantity: 1,
          unitPrice: resolved.unitPrice,
          modifierOptionIds: resolved.modifierOptionIds,
          modifierSummary: resolved.modifierSummary,
        }];
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Modifier selection is invalid");
    }
  }

  function updateQuantity(lineKey: string, delta: number) {
    setCart((previous) => {
      const existing = previous.find((line) => line.key === lineKey);
      if (!existing) return previous;
      const nextQty = existing.quantity + delta;
      if (nextQty <= 0) return previous.filter((line) => line.key !== lineKey);
      return previous.map((line) => (line.key === lineKey ? { ...line, quantity: nextQty } : line));
    });
  }

  async function placeOrder() {
    if (cart.length === 0 || !token) return;
    setPlacing(true);
    setError("");
    try {
      const mergedNotes = [
        notes.trim(),
        customerName.trim() ? `Guest: ${customerName.trim()}` : "",
        customerPhone.trim() ? `Phone: ${customerPhone.trim()}` : "",
      ].filter(Boolean).join(" | ");

      const order = await createPublicDineInOrder({
        tableToken: token,
        notes: mergedNotes || undefined,
        items: cart.map((line) => ({
          menuItemId: line.item.id,
          quantity: line.quantity,
          modifierOptionIds: line.modifierOptionIds.length > 0 ? line.modifierOptionIds : undefined,
        })),
      });
      setCart([]);
      setNotes("");
      setMessage(`Order ${order.id.slice(0, 8).toUpperCase()} sent for table ${order.tableNumber}.`);
      getPublicTableTracking(token).then(setTrackedOrders).catch(() => {});
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not place order");
    } finally {
      setPlacing(false);
    }
  }

  return (
    <main className="shell-sm max-w-5xl pb-28 sm:pb-24">
      <section className="panel">
        <p className="kicker">Table {table?.tableNumber ?? "-"}</p>
        <h1 className="mt-1 text-2xl font-semibold text-ink">Menu</h1>
        <p className="mt-1 text-sm text-ink-soft">Order directly from your phone. No app required.</p>
        <p className={`mt-1 text-xs ${realtimeConnected ? "text-success-on" : "text-warning-on"}`}>
          {realtimeConnected ? "Live order tracking connected" : "Reconnecting live tracking"}
        </p>

        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}

        {loading ? (
          <div className="mt-5 grid gap-3">
            <Skeleton className="h-10 w-full" />
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-32 w-full" />
            ))}
          </div>
        ) : (
          <>
            <div className="sticky top-0 z-[var(--z-sticky)] -mx-4 mt-4 border-y border-line bg-surface px-4 py-2">
              <div className="flex gap-2 overflow-x-auto">
                {categories.map((category) => (
                  <button
                    key={category}
                    type="button"
                    className={`btn btn-sm ${activeCategory === category ? "btn-primary" : "btn-secondary"}`}
                    onClick={() => setActiveCategory(category)}
                  >
                    {category}
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-4 grid gap-3">
              {filteredMenu.map((item) => {
                const groups = modifiersByItemId[item.id] ?? [];
                return (
                  <article key={item.id} className="card grid gap-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <p className="text-base font-semibold text-ink">{item.name}</p>
                        <p className="text-sm text-ink-soft">{item.categoryName}</p>
                      </div>
                      <span className="badge badge-info">GHS {item.price}</span>
                    </div>

                    {item.description ? <p className="text-sm text-ink-soft">{item.description}</p> : null}

                    {groups.map((group) => {
                      const selectedByGroup = selectionByItemId[item.id] ?? {};
                      return (
                        <div key={group.id} className="rounded-lg border border-line p-2">
                          <div className="flex items-center justify-between gap-2">
                            <p className="text-xs font-semibold uppercase tracking-wide text-ink-soft">{group.name}</p>
                            <p className="text-xs text-ink-soft">
                              {group.selectionType === "SINGLE" ? "Pick one" : `Pick ${group.minSelect ?? 0}-${group.maxSelect ?? group.options.length}`}
                            </p>
                          </div>

                          {group.selectionType === "SINGLE" ? (
                            <select
                              className="select mt-2"
                              value={(selectedByGroup[group.id] ?? [""])[0] ?? ""}
                              onChange={(event) => setSingleSelection(item.id, group.id, event.target.value)}
                            >
                              {!group.required ? <option value="">None</option> : null}
                              {group.options.map((option) => (
                                <option key={option.id} value={option.id}>
                                  {option.name} ({toCurrencyValue(option.priceDelta) >= 0 ? "+" : ""}GHS {option.priceDelta})
                                </option>
                              ))}
                            </select>
                          ) : (
                            <div className="mt-2 grid grid-cols-2 gap-2">
                              {group.options.map((option) => {
                                const selected = (selectedByGroup[group.id] ?? []).includes(option.id);
                                return (
                                  <button
                                    key={option.id}
                                    type="button"
                                    className={`rounded-lg border p-2 text-left text-xs ${selected ? "border-line-focus bg-brand-subtle" : "border-line"}`}
                                    onClick={() => toggleMultiSelection(item.id, group, option.id)}
                                  >
                                    <span className="block font-semibold">{option.name}</span>
                                    <span className="block text-ink-soft">
                                      {toCurrencyValue(option.priceDelta) >= 0 ? "+" : ""}GHS {option.priceDelta}
                                    </span>
                                  </button>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      );
                    })}

                    <button type="button" className="btn btn-primary btn-md" onClick={() => addConfiguredItem(item)}>
                      Add to Cart
                    </button>
                  </article>
                );
              })}
            </div>

            <section ref={checkoutRef} className="mt-5 rounded-xl border border-line bg-surface p-4">
              <h2 className="text-lg font-semibold">Checkout</h2>
              <div className="mt-3 grid gap-2">
                {cart.length === 0 ? (
                  <p className="text-sm text-ink-soft">Your cart is empty.</p>
                ) : (
                  cart.map((line) => (
                    <div key={line.key} className="rounded-lg border border-line p-2 text-sm">
                      <div className="flex items-center justify-between gap-2">
                        <span>{line.item.name} × {line.quantity}</span>
                        <span>GHS {(line.unitPrice * line.quantity).toFixed(2)}</span>
                      </div>
                      {line.modifierSummary.length > 0 ? <p className="mt-1 text-xs text-ink-soft">{line.modifierSummary.join(", ")}</p> : null}
                      <div className="mt-2 flex gap-2">
                        <button className="btn btn-secondary btn-sm" onClick={() => updateQuantity(line.key, -1)}>-</button>
                        <button className="btn btn-secondary btn-sm" onClick={() => updateQuantity(line.key, 1)}>+</button>
                      </div>
                    </div>
                  ))
                )}
              </div>

              <div className="mt-3 grid gap-2">
                <input className="input" placeholder="Your name (optional)" value={customerName} onChange={(e) => setCustomerName(e.target.value)} />
                <input className="input" placeholder="Phone (optional)" value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} />
                <textarea className="textarea" placeholder="Kitchen note (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} />
              </div>

              <h3 className="mt-4 text-sm font-semibold text-ink">Live Order Status</h3>
              <div className="mt-2 grid gap-2">
                {trackedOrders.slice().reverse().slice(0, 4).map((order) => (
                  <article key={order.orderId} className="rounded-lg border border-line p-2 text-xs">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">#{order.orderId.slice(0, 8).toUpperCase()}</span>
                      <span className={`badge ${STATUS_BADGE[order.status]}`}>{order.status}</span>
                    </div>
                    <p className="mt-1 text-ink-soft">Updated {new Date(order.updatedAt).toLocaleTimeString()}</p>
                  </article>
                ))}
                {trackedOrders.length === 0 ? <p className="text-sm text-ink-soft">No orders yet.</p> : null}
              </div>

              <Link className="btn btn-secondary btn-sm mt-3" href={`/bill/${token}`}>View Running Bill</Link>
            </section>
          </>
        )}
      </section>

      <div className="fixed inset-x-0 bottom-0 z-[var(--z-sticky)] border-t border-line bg-surface/95 px-3 py-3 backdrop-blur sm:px-4">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3">
          <button className="btn btn-secondary btn-sm" onClick={() => checkoutRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })}>
            Cart ({cartItemsCount}) · GHS {total.toFixed(2)}
          </button>
          <button type="button" disabled={placing || cart.length === 0} onClick={() => void placeOrder()} className="btn btn-primary btn-md disabled:opacity-60">
            {placing ? <Spinner className="text-current" /> : null}
            Place Order
          </button>
        </div>
      </div>
    </main>
  );
}
