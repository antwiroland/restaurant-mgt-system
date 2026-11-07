"use client";

import { Client } from "@stomp/stompjs";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { GuestOrderActions } from "@/components/GuestOrderActions";
import { Skeleton } from "@/components/Skeleton";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { BrandMark } from "@/components/NavSidebar";
import { FoodImage } from "@/components/site/FoodImage";
import { brand, formatMoney } from "@/lib/brand";
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
  linkTableSession,
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
  const [liveTick, setLiveTick] = useState(0);
  const checkoutRef = useRef<HTMLDivElement | null>(null);
  const [sheetItemId, setSheetItemId] = useState<string | null>(null);
  const [cartOpen, setCartOpen] = useState(false);
  const [justAdded, setJustAdded] = useState(0);

  useEffect(() => {
    if (!params?.token) {
      setError("Invalid table QR link");
      return;
    }
    setToken(params.token);
  }, [params]);

  useEffect(() => {
    if (!token) return;
    // Register this browser as an ordering session at the table (best effort).
    let sessionId = window.localStorage.getItem("rm-table-session-id");
    if (!sessionId) {
      sessionId = window.crypto?.randomUUID?.() ?? `web-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      window.localStorage.setItem("rm-table-session-id", sessionId);
    }
    void linkTableSession(token, sessionId).catch(() => undefined);
  }, [token]);

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
          setLiveTick((tick) => tick + 1);
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

  function addConfiguredItem(item: MenuItemRecord): boolean {
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
      setJustAdded((value) => value + 1);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Modifier selection is invalid");
      return false;
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
      setMessage(`Order #${order.id.slice(0, 8).toUpperCase()} is on its way to the kitchen.`);
      setCartOpen(false);
      window.scrollTo({ top: 0, behavior: "smooth" });
      getPublicTableTracking(token).then(setTrackedOrders).catch(() => {});
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not place order");
    } finally {
      setPlacing(false);
    }
  }

  const STEPS: Array<{ label: string; statuses: PublicOrderTrackingRecord["status"][] }> = [
    { label: "Received", statuses: ["PENDING"] },
    { label: "Accepted", statuses: ["CONFIRMED"] },
    { label: "Cooking", statuses: ["PREPARING"] },
    { label: "Ready", statuses: ["READY", "OUT_FOR_DELIVERY"] },
    { label: "Served", statuses: ["COMPLETED", "DELIVERED"] },
  ];
  const stepIndex = (status: PublicOrderTrackingRecord["status"]) => STEPS.findIndex((step) => step.statuses.includes(status));
  const sheetItem = sheetItemId ? menu.find((item) => item.id === sheetItemId) ?? null : null;
  const qtyByItem = cart.reduce<Record<string, number>>((acc, line) => ({ ...acc, [line.item.id]: (acc[line.item.id] ?? 0) + line.quantity }), {});
  const recentOrders = trackedOrders.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 4);

  function quickAdd(item: MenuItemRecord) {
    if ((modifiersByItemId[item.id] ?? []).length > 0) {
      setError("");
      setSheetItemId(item.id);
      return;
    }
    addConfiguredItem(item);
  }

  return (
    <main className="min-h-dvh bg-ivory pb-32">
      {/* Header */}
      <header className="bg-espresso text-espresso-ink">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-4 pb-6 pt-5">
          <div className="flex items-center gap-3">
            <BrandMark size={40} />
            <div>
              <p className="font-serif text-lg font-semibold leading-tight">{brand.name}</p>
              <p className="text-xs text-espresso-soft">Order from your table</p>
            </div>
          </div>
          <div className="rounded-xl bg-espresso-raised px-3.5 py-2 text-center">
            <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-espresso-soft">Table</p>
            <p className="font-display text-2xl font-extrabold leading-none text-gold">{table?.tableNumber ?? "–"}</p>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-3xl px-4">
        {/* Status messages + live orders */}
        <div className="-mt-3 grid gap-3">
          {error ? (
            <p className="alert alert-danger shadow-sm" role="alert"><Icon name="alert" size={18} className="mt-0.5 shrink-0" />{error}</p>
          ) : null}
          {message ? (
            <p className="alert alert-success shadow-sm" role="status"><Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />{message}</p>
          ) : null}

          {recentOrders.length > 0 ? (
            <section className="rounded-2xl border border-line bg-surface p-4 shadow-sm" aria-labelledby="your-orders">
              <div className="flex items-center justify-between gap-3">
                <h2 id="your-orders" className="font-display font-bold">Your table&apos;s orders</h2>
                <span className="flex items-center gap-1.5 text-xs font-semibold text-ink-soft">
                  <span className={`h-2 w-2 rounded-full ${realtimeConnected ? "bg-success" : "animate-pulse bg-warning"}`} aria-hidden="true" />
                  {realtimeConnected ? "Live" : "Reconnecting"}
                </span>
              </div>
              <ul className="mt-3 grid gap-4">
                {recentOrders.map((order) => {
                  const current = stepIndex(order.status);
                  const cancelled = order.status === "CANCELLED" || order.status === "VOIDED";
                  return (
                    <li key={order.orderId}>
                      <div className="flex items-center justify-between text-sm">
                        <span className="font-mono font-semibold">#{order.orderId.slice(0, 8).toUpperCase()}</span>
                        <span className="text-xs text-ink-muted">
                          {cancelled ? "Cancelled" : STEPS[current]?.label ?? order.status} · {new Date(order.updatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                        </span>
                      </div>
                      {cancelled ? (
                        <p className="mt-1 text-xs text-danger-on">{order.cancelReason ?? "This order was cancelled. Please ask our team."}</p>
                      ) : (
                        <ol className="mt-2 grid grid-cols-5 gap-1" aria-label={`Order progress: ${STEPS[current]?.label ?? order.status}`}>
                          {STEPS.map((step, index) => (
                            <li key={step.label} className="grid gap-1">
                              <span className={`h-1.5 rounded-full ${index <= current ? "bg-brand" : "bg-muted"} ${index === current ? "animate-pulse" : ""}`} />
                              <span className={`text-[10px] font-semibold ${index <= current ? "text-ink" : "text-ink-muted"}`}>{step.label}</span>
                            </li>
                          ))}
                        </ol>
                      )}
                      <GuestOrderActions tableToken={token} order={order} refreshKey={liveTick} />
                    </li>
                  );
                })}
              </ul>
              <Link className="btn btn-secondary btn-sm mt-4 w-full" href={`/bill/${token}`}>
                <Icon name="receipt" size={16} /> View running bill
              </Link>
            </section>
          ) : null}
        </div>

        {loading ? (
          <div className="mt-6 grid gap-3">
            <Skeleton className="h-10 w-full" />
            {Array.from({ length: 6 }).map((_, index) => <Skeleton key={index} className="h-28 w-full" />)}
          </div>
        ) : (
          <>
            {/* Category bar */}
            <div className="sticky top-0 z-[var(--z-sticky)] -mx-4 mt-5 border-b border-line bg-ivory/95 px-4 py-3 backdrop-blur">
              <div className="flex gap-2 overflow-x-auto [scrollbar-width:none]" role="group" aria-label="Categories">
                {categories.map((category) => (
                  <button key={category} type="button" className="chip shrink-0" aria-pressed={activeCategory === category} onClick={() => setActiveCategory(category)}>
                    {category === "ALL" ? "Everything" : category}
                  </button>
                ))}
              </div>
            </div>

            {filteredMenu.length === 0 ? (
              <p className="mt-10 text-center text-ink-soft">Nothing available in this section right now.</p>
            ) : (
              <ul className="mt-4 grid gap-3">
                {filteredMenu.map((item) => {
                  const hasOptions = (modifiersByItemId[item.id] ?? []).length > 0;
                  const qty = qtyByItem[item.id] ?? 0;
                  return (
                    <li key={item.id}>
                      <article className={`flex gap-3 rounded-2xl border bg-surface p-3 transition-colors ${qty > 0 ? "border-brand-soft" : "border-line"}`}>
                        <button type="button" className="shrink-0" onClick={() => setSheetItemId(item.id)} aria-label={`View ${item.name}`}>
                          <FoodImage src={item.imageUrl} alt={item.name} ratio="aspect-square" className="w-24 rounded-xl" />
                        </button>
                        <div className="flex min-w-0 flex-1 flex-col">
                          <button type="button" className="text-left" onClick={() => setSheetItemId(item.id)}>
                            <h3 className="font-display text-[15px] font-bold leading-snug text-ink">{item.name}</h3>
                            {item.description ? <p className="mt-0.5 line-clamp-2 text-[13px] leading-snug text-ink-soft">{item.description}</p> : null}
                          </button>
                          <div className="mt-auto flex items-center justify-between gap-2 pt-2">
                            <span className="money text-[15px] text-ink">{formatMoney(item.price)}</span>
                            <button
                              type="button"
                              onClick={() => quickAdd(item)}
                              className={`flex h-10 min-w-10 items-center justify-center gap-1.5 rounded-full px-3 text-sm font-bold transition-colors ${qty > 0 ? "bg-brand text-white" : "border border-line-strong bg-surface text-ink hover:border-brand hover:text-brand"}`}
                              aria-label={`Add ${item.name}${hasOptions ? " (choose options)" : ""}`}
                            >
                              {qty > 0 ? <span key={qty} className="animate-pop tabular">{qty}</span> : null}
                              <Icon name="plus" size={18} strokeWidth={2.25} />
                              {hasOptions && qty === 0 ? <span className="text-xs font-semibold">Options</span> : null}
                            </button>
                          </div>
                        </div>
                      </article>
                    </li>
                  );
                })}
              </ul>
            )}

            {trackedOrders.length === 0 ? (
              <p className="mt-8 text-center text-xs text-ink-muted">
                Orders go straight to our kitchen. Pay with our team or from your running bill.
              </p>
            ) : null}
          </>
        )}
      </div>

      {/* Floating cart bar */}
      {cartItemsCount > 0 ? (
        <div className="fixed inset-x-0 bottom-0 z-[var(--z-sticky)] px-3 pb-[max(env(safe-area-inset-bottom),12px)] pt-3">
          <button
            type="button"
            onClick={() => setCartOpen(true)}
            className="mx-auto flex h-16 w-full max-w-3xl items-center justify-between gap-3 rounded-2xl bg-espresso px-5 text-espresso-ink shadow-xl transition-transform active:scale-[0.99]"
          >
            <span className="flex items-center gap-3">
              <span key={justAdded} className="grid h-9 min-w-9 animate-pop place-items-center rounded-full bg-brand px-2 font-display font-extrabold text-white">{cartItemsCount}</span>
              <span className="font-semibold">View order</span>
            </span>
            <span className="money text-lg">{formatMoney(total)}</span>
          </button>
        </div>
      ) : null}

      {/* Dish sheet */}
      {sheetItem ? (
        <Modal open title={sheetItem.name} onClose={() => setSheetItemId(null)}
          footer={
            <button
              type="button"
              className="btn btn-primary btn-lg btn-block"
              onClick={() => { if (addConfiguredItem(sheetItem)) setSheetItemId(null); }}
            >
              Add to order · {(() => { try { return formatMoney(resolveSelection(sheetItem).unitPrice); } catch { return formatMoney(sheetItem.price); } })()}
            </button>
          }
        >
          <div className="grid gap-4">
            <FoodImage src={sheetItem.imageUrl} alt={sheetItem.name} ratio="aspect-[16/10]" className="-mx-5 -mt-2 sm:mx-0 sm:rounded-xl" />
            {sheetItem.description ? <p className="text-ink-soft">{sheetItem.description}</p> : null}
            <p className="money text-xl text-brand">{formatMoney(sheetItem.price)}</p>
            {(modifiersByItemId[sheetItem.id] ?? []).map((group) => {
              const selected = selectionByItemId[sheetItem.id]?.[group.id] ?? [];
              return (
                <fieldset key={group.id} className="grid gap-2">
                  <legend className="mb-1 flex w-full items-center justify-between">
                    <span className="font-display font-bold">{group.name}</span>
                    <span className={`badge ${group.required ? "badge-warning" : "badge-neutral"}`}>
                      {group.required ? "Required" : "Optional"}{group.selectionType === "MULTIPLE" && group.maxSelect ? ` · up to ${group.maxSelect}` : ""}
                    </span>
                  </legend>
                  {group.options.map((option) => {
                    const on = selected.includes(option.id);
                    return (
                      <button
                        key={option.id}
                        type="button"
                        role={group.selectionType === "SINGLE" ? "radio" : "checkbox"}
                        aria-checked={on}
                        onClick={() => {
                          if (group.selectionType === "SINGLE") setSingleSelection(sheetItem.id, group.id, !group.required && on ? "" : option.id);
                          else toggleMultiSelection(sheetItem.id, group, option.id);
                        }}
                        className={`flex min-h-[52px] items-center justify-between gap-3 rounded-xl border px-4 text-left transition-colors ${on ? "border-brand bg-brand-subtle" : "border-line hover:border-line-strong"}`}
                      >
                        <span className="flex items-center gap-3 font-semibold text-ink">
                          <span className={`grid h-5 w-5 place-items-center border ${group.selectionType === "SINGLE" ? "rounded-full" : "rounded-md"} ${on ? "border-brand bg-brand text-white" : "border-line-strong bg-surface"}`}>
                            {on ? <Icon name="check" size={13} strokeWidth={3} /> : null}
                          </span>
                          {option.name}
                        </span>
                        <span className="money text-sm text-ink-soft">{toCurrencyValue(option.priceDelta) > 0 ? `+${formatMoney(option.priceDelta)}` : ""}</span>
                      </button>
                    );
                  })}
                </fieldset>
              );
            })}
            {error ? <p className="field-error" role="alert">{error}</p> : null}
          </div>
        </Modal>
      ) : null}

      {/* Cart / checkout sheet */}
      <Modal open={cartOpen} title={`Your order · Table ${table?.tableNumber ?? ""}`} onClose={() => setCartOpen(false)}
        footer={
          <button type="button" disabled={placing || cart.length === 0} onClick={() => void placeOrder()} className="btn btn-primary btn-lg btn-block justify-between">
            <span className="flex items-center gap-2">{placing ? <Spinner className="text-current" /> : <Icon name="chef" size={20} />}{placing ? "Sending…" : "Send to kitchen"}</span>
            <span className="money">{formatMoney(total)}</span>
          </button>
        }
      >
        <div ref={checkoutRef} className="grid gap-4">
          {cart.length === 0 ? (
            <p className="text-ink-soft">Your order is empty.</p>
          ) : (
            <ul className="divide-y divide-line-subtle">
              {cart.map((line) => (
                <li key={line.key} className="flex items-center gap-3 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-ink">{line.item.name}</p>
                    {line.modifierSummary.length > 0 ? <p className="text-xs text-ink-soft">{line.modifierSummary.map((m) => m.split(": ")[1] ?? m).join(" · ")}</p> : null}
                    <p className="money mt-0.5 text-sm text-ink-soft">{formatMoney(line.unitPrice * line.quantity)}</p>
                  </div>
                  <div className="flex items-center rounded-full border border-line">
                    <button className="grid h-10 w-10 place-items-center" onClick={() => updateQuantity(line.key, -1)} aria-label={`Remove one ${line.item.name}`}>
                      <Icon name={line.quantity === 1 ? "trash" : "minus"} size={16} />
                    </button>
                    <span className="w-6 text-center font-bold tabular">{line.quantity}</span>
                    <button className="grid h-10 w-10 place-items-center" onClick={() => updateQuantity(line.key, 1)} aria-label={`Add one ${line.item.name}`}>
                      <Icon name="plus" size={16} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <div className="grid gap-3 rounded-xl bg-ivory p-3">
            <label className="field">
              <span className="field-label">Note for the kitchen <span className="font-normal text-ink-muted">(optional)</span></span>
              <textarea className="textarea min-h-[72px]" placeholder="Allergies, no onions, extra spicy…" value={notes} onChange={(e) => setNotes(e.target.value)} />
            </label>
            <div className="grid grid-cols-2 gap-2">
              <label className="field">
                <span className="field-label">Name <span className="font-normal text-ink-muted">(optional)</span></span>
                <input className="input" autoComplete="given-name" value={customerName} onChange={(e) => setCustomerName(e.target.value)} />
              </label>
              <label className="field">
                <span className="field-label">Phone <span className="font-normal text-ink-muted">(optional)</span></span>
                <input className="input" inputMode="tel" autoComplete="tel" value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} />
              </label>
            </div>
          </div>
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          <p className="text-xs text-ink-muted">Prices include any options you chose. Your server will confirm the final bill.</p>
        </div>
      </Modal>
    </main>
  );
}
